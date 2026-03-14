package com.digitalisyours.infrastructure.ia;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IAQuizService {
    @Value("${groq.api.key:}")
    private String groqApiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    private final CoursContentExtractor contentExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // ── Cache des thèmes déjà couverts par cours/formation ───
    // Clé : "cours-{id}" ou "formation-{id}"
    // Valeur : liste des débuts de questions (premiers 60 chars) des 2 derniers quiz
    private final Map<String, List<String>> themesDejaCouverts = new ConcurrentHashMap<>();

    // ══════════════════════════════════════════════════════════
    // POINT D'ENTRÉE PRINCIPAL
    // ══════════════════════════════════════════════════════════

    public List<QuizQuestionIA> genererQuiz(
            CoursInfoIA coursInfo,
            Long coursId,
            Long formationId,
            String videoUrl,
            String videoType,
            int nbQuestions,
            String difficulte,
            boolean inclureDefinitions,
            boolean inclureCasPratiques) {

        // Clé unique pour identifier le contexte (cours ou formation)
        String cacheKey = formationId != null ? "formation-" + formationId : "cours-" + coursId;

        String contenuPdf = "";
        String transcriptionVideo = "";

        if (formationId != null) {
            contenuPdf = contentExtractor.extraireTousLesCoursDeLaFormation(formationId);
            if (contenuPdf.length() > 6000) {
                contenuPdf = contenuPdf.substring(0, 6000) + "\n[...contenu résumé...]";
            }
            if (!contenuPdf.isBlank()) {
                log.info("Contenu PDF formation extrait : {} caractères pour la formation {}", contenuPdf.length(), formationId);
            }
        } else if (coursId != null) {
            contenuPdf = contentExtractor.extrairePdfs(coursId);
            if (!contenuPdf.isBlank()) {
                log.info("Contenu PDF extrait : {} caractères pour le cours {}", contenuPdf.length(), coursId);
            }
        }

        if ("YOUTUBE".equals(videoType) && videoUrl != null && !videoUrl.isBlank()) {
            transcriptionVideo = contentExtractor.extraireTranscriptionYoutube(videoUrl);
            if (!transcriptionVideo.isBlank()) {
                log.info("Transcription YouTube extraite : {} caractères", transcriptionVideo.length());
            }
        }

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("Clé Groq manquante — mode simulation activé");
            return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques, cacheKey);
        }

        try {
            int seed = new java.util.Random().nextInt(100000);

            // Groq tronque au-delà de ~20 questions — limiter pour éviter JSON cassé
            int nbQEffectif = Math.min(nbQuestions, 20);
            if (nbQEffectif < nbQuestions) {
                log.info("nbQuestions limité à {} (demandé: {}) pour éviter troncature Groq", nbQEffectif, nbQuestions);
            }

            // Récupérer les thèmes déjà couverts pour ce contexte
            List<String> themesExistants = themesDejaCouverts.getOrDefault(cacheKey, new ArrayList<>());

            String prompt = buildPrompt(coursInfo, contenuPdf, transcriptionVideo,
                    nbQEffectif, difficulte, inclureDefinitions, inclureCasPratiques,
                    seed, themesExistants);

            // maxTokens : ~280 tokens par question, minimum 3000
            int maxTokens = Math.max(3000, nbQEffectif * 280);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", GROQ_MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "Tu es un expert pédagogique. Réponds UNIQUEMENT avec un tableau JSON valide, "
                                            + "sans markdown, sans texte avant ou après, sans balises ```json."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 1.0,
                    "max_tokens", maxTokens
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(90))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                log.warn("Quota Groq dépassé (429) — mode simulation activé");
                return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques, cacheKey);
            }

            if (response.statusCode() != 200) {
                log.error("Erreur API Groq: {} — {}", response.statusCode(), response.body());
                return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques, cacheKey);
            }

            List<QuizQuestionIA> questions = parseGroqResponse(response.body());

            // Si JSON trop corrompu → fallback simulation
            if (questions.isEmpty()) {
                log.warn("Réponse Groq vide après parsing — mode simulation activé");
                return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques, cacheKey);
            }

            // ── Mémoriser les thèmes de CE quiz pour le prochain ──
            List<String> nouveauxThemes = questions.stream()
                    .map(q -> q.getTexte() != null
                            ? q.getTexte().substring(0, Math.min(70, q.getTexte().length()))
                            : "")
                    .filter(t -> !t.isBlank())
                    .collect(Collectors.toList());

            // Garder uniquement les 40 derniers thèmes (2 quiz max)
            List<String> tousThemes = new ArrayList<>(themesExistants);
            tousThemes.addAll(nouveauxThemes);
            if (tousThemes.size() > 40) {
                tousThemes = tousThemes.subList(tousThemes.size() - 40, tousThemes.size());
            }
            themesDejaCouverts.put(cacheKey, tousThemes);

            // ── Mélanger questions ET options ──
            Collections.shuffle(questions);
            String[] lettres = {"A", "B", "C", "D"};
            for (QuizQuestionIA q : questions) {
                String bonneReponseTexte = q.getOptions().stream()
                        .filter(OptionIA::isEstCorrecte)
                        .map(OptionIA::getTexte)
                        .findFirst().orElse("");
                Collections.shuffle(q.getOptions());
                for (int i = 0; i < q.getOptions().size(); i++) {
                    OptionIA opt = q.getOptions().get(i);
                    opt.setOrdre(lettres[i]);
                    opt.setEstCorrecte(opt.getTexte().equals(bonneReponseTexte));
                }
            }

            log.info("Génération IA Groq réussie : {} questions (thèmes exclus : {})",
                    questions.size(), themesExistants.size());
            return questions;

        } catch (Exception e) {
            log.error("Erreur lors de la génération IA Groq: {}", e.getMessage());
            return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques, cacheKey);
        }
    }

    // ══════════════════════════════════════════════════════════
    // CONSTRUCTION DU PROMPT — avec anti-reformulation
    // ══════════════════════════════════════════════════════════

    private String buildPrompt(CoursInfoIA info, String contenuPdf, String transcriptionVideo,
                               int nbQ, String diff, boolean def, boolean prat,
                               int seed, List<String> themesDejaTraites) {

        String diffNorm = (diff != null) ? diff.toUpperCase() : "MOYEN";
        boolean hasRealContent = !contenuPdf.isBlank() || !transcriptionVideo.isBlank();

        StringBuilder sb = new StringBuilder();

        sb.append("Tu es un expert pédagogique qui crée des QCM de niveau universitaire.\n");
        sb.append("Tu dois générer exactement ").append(nbQ).append(" questions QCM.\n\n");

        sb.append("COURS / FORMATION : ").append(info.getTitre()).append("\n");
        if (info.getDescription() != null && !info.getDescription().isBlank()) {
            sb.append("DESCRIPTION : ").append(info.getDescription()).append("\n");
        }
        if (info.getObjectifs() != null && !info.getObjectifs().isBlank()) {
            sb.append("OBJECTIFS : ").append(info.getObjectifs()).append("\n");
        }

        if (!contenuPdf.isBlank()) {
            sb.append("\n========== CONTENU DES DOCUMENTS ==========\n");
            sb.append(contenuPdf);
            sb.append("\n==============================================\n");
        }
        if (!transcriptionVideo.isBlank()) {
            sb.append("\n========== TRANSCRIPTION DE LA VIDÉO =========\n");
            sb.append(transcriptionVideo);
            sb.append("\n==============================================\n");
        }

        if (hasRealContent) {
            sb.append("\nREGLE ABSOLUE : chaque question doit être basée UNIQUEMENT sur le contenu fourni ci-dessus.\n");
            sb.append("Chaque question doit tester un concept DIFFERENT. Aucune question générique.\n");
        }

        // ── ANTI-REFORMULATION : thèmes déjà couverts ─────────
        if (!themesDejaTraites.isEmpty()) {
            sb.append("\n╔══════════════════════════════════════════════╗\n");
            sb.append("║  QUESTIONS DÉJÀ POSÉES — À NE PAS RÉPÉTER  ║\n");
            sb.append("╚══════════════════════════════════════════════╝\n");
            sb.append("Les questions suivantes ont déjà été posées dans les quiz précédents.\n");
            sb.append("Tu NE DOIS PAS poser des questions similaires, reformulées ou portant sur le même concept :\n\n");
            for (int i = 0; i < themesDejaTraites.size(); i++) {
                sb.append("  ✗ ").append(i + 1).append(". ").append(themesDejaTraites.get(i)).append("...\n");
            }
            sb.append("\nPour chaque nouvelle question, tu DOIS explorer un ANGLE DIFFÉRENT :\n");
            sb.append("- Si 'crawl' a déjà été couvert → parle de Googlebot, de budget de crawl, de crawl delay\n");
            sb.append("- Si 'indexation' a été couverte → parle de duplicate content, de canonical, de noindex\n");
            sb.append("- Si 'mots-clés' a été couvert → parle de clustering, de cannibalization, de mapping\n");
            sb.append("- Si 'backlinks' a été couvert → parle de anchor text, de toxic links, de disavow\n");
            sb.append("- En général : approfondis les sous-thèmes, cas limites, chiffres précis, erreurs courantes\n\n");
        }

        // ── Types de questions ────────────────────────────────
        if (def && prat) {
            sb.append("TYPES : mélange de questions sur les définitions/concepts ET sur l'application pratique.\n");
        } else if (def) {
            sb.append("TYPES : questions sur les définitions et la compréhension des concepts.\n");
        } else if (prat) {
            sb.append("TYPES : questions sur l'application pratique et les cas concrets.\n");
        }

        // ── Niveau de difficulté ──────────────────────────────
        switch (diffNorm) {
            case "FACILE" -> {
                sb.append("\n==== NIVEAU : FACILE ====\n");
                sb.append("- Questions directes sur les définitions de base\n");
                sb.append("- La bonne réponse est évidente pour quelqu'un qui a lu le contenu\n");
                sb.append("- Les 3 mauvaises réponses sont clairement fausses\n");
                sb.append("- INTERDIT de reformuler une question déjà posée — même en changeant juste les mots\n");
                sb.append("- Exemples d'angles NOUVEAUX : définitions secondaires, exemples concrets, utilisation basique\n");
            }
            case "MOYEN" -> {
                sb.append("\n==== NIVEAU : MOYEN ====\n");
                sb.append("- Questions de compréhension nécessitant d'avoir bien étudié\n");
                sb.append("- Les 3 mauvaises réponses sont plausibles\n");
                sb.append("- INTERDIT de reformuler une question déjà posée\n");
                sb.append("- Angles NOUVEAUX obligatoires : comparaisons, 'pourquoi', 'dans quel cas', différences entre concepts\n");
                sb.append("- Exemples : 'Quelle est la différence entre X et Y ?', 'Dans quel cas utilise-t-on X ?'\n");
            }
            case "DIFFICILE" -> {
                sb.append("\n==== NIVEAU : DIFFICILE ====\n");
                sb.append("- Questions sur des NUANCES PRÉCISES, CHIFFRES EXACTS, EXCEPTIONS, CAS LIMITES\n");
                sb.append("- Les 3 mauvaises réponses sont très proches de la bonne\n");
                sb.append("- OBLIGATOIRE : au moins 2 questions 'Laquelle de ces affirmations est FAUSSE ?'\n");
                sb.append("- INTERDIT de reformuler une question déjà posée\n");
                sb.append("- Angles NOUVEAUX : combinaisons de concepts, ordres de priorité, valeurs numériques précises,\n");
                sb.append("  scénarios d'erreur, interactions entre fonctionnalités, edge cases\n");
                sb.append("- Exemples : 'Lequel de ces outils NE permet PAS de...', 'Dans quel ordre précis...', \n");
                sb.append("  'Que se passe-t-il si simultanément X et Y ?'\n");
            }
            default -> sb.append("Niveau de difficulté standard.\n");
        }

        // ── Diversité des formats de questions ────────────────
        sb.append("\n== DIVERSITÉ DES FORMATS OBLIGATOIRE ==\n");
        sb.append("Parmi les ").append(nbQ).append(" questions, tu DOIS utiliser ces différents formats :\n");
        sb.append("- Questions 'Qu'est-ce que X ?' (définitions)\n");
        sb.append("- Questions 'Laquelle est FAUSSE ?' (contre-exemples)\n");
        sb.append("- Questions 'Quel est le rôle de X ?' (fonctions)\n");
        sb.append("- Questions 'Dans quel cas X est-il utilisé ?' (application)\n");
        sb.append("- Questions 'Quelle est la différence entre X et Y ?' (comparaison)\n");
        sb.append("- Questions avec des chiffres précis si le contenu en contient\n");
        sb.append("INTERDIT d'utiliser le même format plus de ").append(Math.max(2, nbQ / 4)).append(" fois.\n\n");

        // ── Seed pour la variété ──────────────────────────────
        sb.append("SEED #").append(seed).append(" — Explore un angle INÉDIT du contenu. ");
        sb.append("Les questions doivent être SUBSTANTIELLEMENT différentes de tout quiz précédent.\n\n");

        // ── Format de réponse ─────────────────────────────────
        sb.append("FORMAT DE RÉPONSE : tableau JSON pur, sans markdown, sans texte avant/après.\n");
        sb.append("[{\"texte\":\"Question ?\",\"explication\":\"Explication\",\"options\":[\n");
        sb.append("{\"ordre\":\"A\",\"texte\":\"Option A\",\"estCorrecte\":false},\n");
        sb.append("{\"ordre\":\"B\",\"texte\":\"Option B\",\"estCorrecte\":true},\n");
        sb.append("{\"ordre\":\"C\",\"texte\":\"Option C\",\"estCorrecte\":false},\n");
        sb.append("{\"ordre\":\"D\",\"texte\":\"Option D\",\"estCorrecte\":false}]}]\n");
        sb.append("RÈGLES STRICTES : exactement 4 options, exactement 1 estCorrecte:true, pas de markdown.");

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    // PARSING GROQ
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> parseGroqResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").get(0).path("message").path("content").asText().trim();

        if (content.contains("```json")) content = content.substring(content.indexOf("```json") + 7);
        if (content.contains("```")) content = content.substring(0, content.lastIndexOf("```"));
        content = content.trim();

        int jsonStart = content.indexOf('[');
        if (jsonStart > 0) content = content.substring(jsonStart);

        // ── Réparer JSON tronqué (quota token Groq dépassé en milieu de réponse) ──
        content = reparerJsonTronque(content);

        JsonNode questionsNode = objectMapper.readTree(content);
        List<QuizQuestionIA> questions = new ArrayList<>();

        // Clés de déduplication : premiers 50 chars du texte normalisé
        Set<String> textesDeja = new HashSet<>();

        for (JsonNode qNode : questionsNode) {
            QuizQuestionIA q = new QuizQuestionIA();
            q.setTexte(qNode.path("texte").asText());
            q.setExplication(qNode.path("explication").asText());
            List<OptionIA> options = new ArrayList<>();
            for (JsonNode oNode : qNode.path("options")) {
                OptionIA opt = new OptionIA();
                opt.setOrdre(oNode.path("ordre").asText());
                opt.setTexte(oNode.path("texte").asText());
                opt.setEstCorrecte(oNode.path("estCorrecte").asBoolean());
                options.add(opt);
            }
            q.setOptions(options);

            // ── Déduplication interne au quiz ──────────────────
            // Normaliser : minuscules + supprimer ponctuation + garder 50 chars
            String texteNorm = q.getTexte() == null ? "" :
                    q.getTexte().toLowerCase()
                            .replaceAll("[^a-zàâéèêëîïôùûüç0-9 ]", "")
                            .trim();
            String cle = texteNorm.substring(0, Math.min(50, texteNorm.length()));

            // Vérifier aussi la similarité par mots-clés communs (>60% de mots en commun)
            boolean estDoublon = textesDeja.contains(cle) || estSimilaire(cle, textesDeja);

            if (!estDoublon) {
                textesDeja.add(cle);
                questions.add(q);
            } else {
                log.warn("Question dupliquée ignorée : '{}'", q.getTexte());
            }
        }
        return questions;
    }

    /**
     * Répare un JSON tronqué en milieu de génération (quota token Groq).
     * Stratégie : trouver la dernière question COMPLÈTE et fermer le tableau proprement.
     */
    private String reparerJsonTronque(String json) {
        if (json == null || json.isBlank()) return "[]";

        // Supprimer les caractères de contrôle illégaux dans les strings JSON
        json = json.replaceAll("[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F]", " ");
        // Remplacer les retours à la ligne dans les strings par espace
        json = json.replaceAll("(?<=[:\"\\[,])\\s*\\n\\s*(?=[^\"\\[{])", " ");

        // Tenter de parser directement
        try {
            objectMapper.readTree(json);
            return json; // JSON valide, rien à faire
        } catch (Exception ignored) {}

        // JSON invalide → trouver la dernière question complète
        // Une question complète se termine par "}]}" (fin options + fin question)
        int derniereFin = json.lastIndexOf("}]}");
        if (derniereFin != -1) {
            String repare = json.substring(0, derniereFin + 3) + "]";
            try {
                objectMapper.readTree(repare);
                log.warn("JSON Groq tronqué réparé — conservé jusqu'à l'index {}", derniereFin);
                return repare;
            } catch (Exception ignored) {}
        }

        // Fallback : retourner tableau vide pour déclencher le mode simulation
        log.warn("JSON Groq trop corrompu pour être réparé — fallback simulation");
        return "[]";
    }

    /**
     * Detecte si une question est trop similaire a une deja dans le quiz.
     * Logique : si 65% ou plus des mots significatifs (>3 chars) sont communs = doublon.
     */
    private boolean estSimilaire(String texteNorm, Set<String> textesDeja) {
        if (textesDeja.isEmpty() || texteNorm.isBlank()) return false;
        Set<String> motsCible = new HashSet<>(Arrays.asList(texteNorm.split(" ")));
        motsCible.removeIf(m -> m.length() <= 3); // ignorer mots courts (le, la, de, est...)
        if (motsCible.isEmpty()) return false;

        for (String existant : textesDeja) {
            Set<String> motsExistant = new HashSet<>(Arrays.asList(existant.split(" ")));
            motsExistant.removeIf(m -> m.length() <= 3);
            if (motsExistant.isEmpty()) continue;

            long communs = motsCible.stream().filter(motsExistant::contains).count();
            double ratio = (double) communs / Math.min(motsCible.size(), motsExistant.size());
            if (ratio >= 0.65) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════
    // MODE SIMULATION — avec anti-reformulation également
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> genererQuizSimule(CoursInfoIA info, int nbQ, String diff,
                                                   boolean inclDef, boolean inclPrat,
                                                   String cacheKey) {
        log.info("Simulation quiz — titre: '{}' niveau: {} nbQ: {}", info.getTitre(), diff, nbQ);
        String titre    = info.getTitre() != null ? info.getTitre() : "ce cours";
        String diffNorm = (diff != null) ? diff.toUpperCase() : "MOYEN";

        List<QuizQuestionIA> poolDef  = buildQuestionsDefinitions(titre, diffNorm);
        List<QuizQuestionIA> poolPrat = buildQuestionsPratiques(titre, diffNorm);

        // Récupérer les thèmes déjà couverts pour filtrer le pool
        List<String> dejaCouverts = themesDejaCouverts.getOrDefault(cacheKey, new ArrayList<>());

        // Filtrer les questions déjà posées (comparaison basique sur les 40 premiers chars)
        if (!dejaCouverts.isEmpty()) {
            poolDef  = filtrerQuestionsNouvelles(poolDef,  dejaCouverts);
            poolPrat = filtrerQuestionsNouvelles(poolPrat, dejaCouverts);
        }

        Collections.shuffle(poolDef);
        Collections.shuffle(poolPrat);

        List<QuizQuestionIA> pool = new ArrayList<>();
        if (inclDef && inclPrat) {
            pool.addAll(poolDef); pool.addAll(poolPrat); Collections.shuffle(pool);
        } else if (inclDef)  { pool.addAll(poolDef);  }
        else if (inclPrat)   { pool.addAll(poolPrat); }
        else { pool.addAll(poolDef); pool.addAll(poolPrat); Collections.shuffle(pool); }

        // Si plus assez de questions nouvelles, utiliser tout le pool
        if (pool.size() < nbQ) {
            List<QuizQuestionIA> poolComplet = new ArrayList<>();
            poolComplet.addAll(buildQuestionsDefinitions(titre, diffNorm));
            poolComplet.addAll(buildQuestionsPratiques(titre, diffNorm));
            Collections.shuffle(poolComplet);
            pool = poolComplet;
        }

        List<QuizQuestionIA> resultat = new ArrayList<>(pool.subList(0, Math.min(nbQ, pool.size())));

        // Mémoriser les thèmes de ce quiz simulé
        List<String> nouveauxThemes = resultat.stream()
                .map(q -> q.getTexte() != null
                        ? q.getTexte().substring(0, Math.min(70, q.getTexte().length()))
                        : "")
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());

        List<String> tousThemes = new ArrayList<>(dejaCouverts);
        tousThemes.addAll(nouveauxThemes);
        if (tousThemes.size() > 40) {
            tousThemes = tousThemes.subList(tousThemes.size() - 40, tousThemes.size());
        }
        themesDejaCouverts.put(cacheKey, tousThemes);

        return resultat;
    }

    /**
     * Filtre les questions dont le début ressemble à une question déjà posée.
     */
    private List<QuizQuestionIA> filtrerQuestionsNouvelles(List<QuizQuestionIA> pool,
                                                           List<String> dejaCouverts) {
        return pool.stream().filter(q -> {
            if (q.getTexte() == null) return true;
            String debut = q.getTexte().substring(0, Math.min(40, q.getTexte().length())).toLowerCase();
            return dejaCouverts.stream().noneMatch(deja ->
                    deja.substring(0, Math.min(40, deja.length())).toLowerCase().contains(debut)
                            || debut.contains(deja.substring(0, Math.min(30, deja.length())).toLowerCase())
            );
        }).collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════
    // POOL DÉFINITIONS
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> buildQuestionsDefinitions(String titre, String diff) {
        List<Object[]> data;
        switch (diff) {
            case "FACILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Qu'est-ce que « " + titre + " » ?",
                            new String[]{"Un domaine d'étude spécialisé", "Un logiciel", "Un langage de programmation", "Un système d'exploitation"}, "A",
                            "Ce cours porte sur un domaine d'étude spécialisé."},
                    new Object[]{"Quelle est la définition principale de « " + titre + " » ?",
                            new String[]{"Un ensemble de méthodes et connaissances", "Un outil de gestion", "Un protocole réseau", "Un algorithme"}, "A",
                            "Un ensemble structuré de méthodes et connaissances est la définition fondamentale."},
                    new Object[]{"Quel terme décrit le mieux le concept central de « " + titre + " » ?",
                            new String[]{"Apprentissage simple", "Compétence structurée", "Interface utilisateur", "Base de données"}, "B",
                            "Une compétence structurée est au cœur de ce domaine."},
                    new Object[]{"Parmi ces définitions, laquelle correspond à « " + titre + " » ?",
                            new String[]{"Une technologie obsolète", "Une discipline avec ses propres concepts", "Un format de fichier", "Un serveur web"}, "B",
                            "C'est une discipline avec ses propres concepts et méthodes."},
                    new Object[]{"Comment qualifier « " + titre + " » dans un contexte professionnel ?",
                            new String[]{"Une compétence optionnelle", "Un savoir-faire valorisé", "Un outil remplacé", "Une certification obsolète"}, "B",
                            "C'est un savoir-faire reconnu et valorisé professionnellement."},
                    new Object[]{"Quel vocabulaire est fondamental pour comprendre « " + titre + " » ?",
                            new String[]{"Les termes propres à ce domaine", "Le jargon marketing", "Les termes juridiques", "Le vocabulaire médical"}, "A",
                            "La maîtrise du vocabulaire spécifique est essentielle."},
                    new Object[]{"Quelle est la première chose à apprendre dans « " + titre + " » ?",
                            new String[]{"Les outils avancés", "Les concepts de base", "Les cas complexes", "Les certifications"}, "B",
                            "Les concepts de base sont toujours le point de départ."},
                    new Object[]{"À quoi sert principalement « " + titre + " » ?",
                            new String[]{"À automatiser des tâches", "À structurer et développer des compétences", "À remplacer des experts", "À générer des revenus"}, "B",
                            "L'objectif principal est de structurer et développer des compétences."},
                    new Object[]{"Quelle compétence de base est nécessaire pour aborder « " + titre + " » ?",
                            new String[]{"Une expérience avancée", "Une curiosité intellectuelle et une base théorique", "Des certifications", "Des outils spécialisés"}, "B",
                            "La curiosité et une base théorique suffisent pour débuter."},
                    new Object[]{"Quel objectif principal vise « " + titre + " » ?",
                            new String[]{"Mémoriser des formules", "Développer une compréhension globale du domaine", "Maîtriser un seul outil", "Obtenir une certification"}, "B",
                            "Développer une compréhension globale est l'objectif central."},
                    new Object[]{"Qui peut bénéficier de « " + titre + " » ?",
                            new String[]{"Uniquement les experts", "Toute personne souhaitant progresser dans ce domaine", "Uniquement les développeurs", "Uniquement les managers"}, "B",
                            "Ce domaine est accessible à toute personne souhaitant progresser."},
                    new Object[]{"Quelle est la nature fondamentale de « " + titre + " » ?",
                            new String[]{"Un outil technique unique", "Une discipline combinant théorie et pratique", "Un simple concept marketing", "Un protocole informatique"}, "B",
                            "Ce domaine combine nécessairement théorie et pratique."}
            ));
            case "DIFFICILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Quelle distinction conceptuelle est fondamentale dans « " + titre + " » ?",
                            new String[]{"Entre théorie et pratique", "Entre concept et outil", "Entre méthode et résultat", "Toutes ces distinctions"}, "D",
                            "Dans un domaine avancé, toutes ces distinctions sont importantes."},
                    new Object[]{"Quelle nuance différencie les experts des débutants dans « " + titre + " » ?",
                            new String[]{"La précision du vocabulaire", "La vitesse d'exécution", "Le nombre d'outils maîtrisés", "L'ancienneté"}, "A",
                            "La précision du vocabulaire technique distingue les experts des débutants."},
                    new Object[]{"Quel concept de « " + titre + " » est souvent mal compris par les praticiens intermédiaires ?",
                            new String[]{"La relation entre cause et effet", "L'interdépendance des variables contextuelles", "La hiérarchie des priorités", "L'optimisation des ressources"}, "B",
                            "L'interdépendance des variables contextuelles est souvent sous-estimée."},
                    new Object[]{"Parmi ces assertions sur « " + titre + " », laquelle est la plus exacte ?",
                            new String[]{"Les définitions sont universelles", "Les définitions évoluent avec le contexte", "Un seul modèle théorique suffit", "La pratique prime toujours"}, "B",
                            "Les définitions évoluent avec le contexte et les avancées du domaine."},
                    new Object[]{"Quelle épistémologie sous-tend « " + titre + " » ?",
                            new String[]{"Un raisonnement purement inductif", "Un va-et-vient entre théorie et empirisme", "Une approche exclusivement déductive", "Un modèle algorithmique fixe"}, "B",
                            "Le va-et-vient entre théorie et empirisme caractérise les domaines matures."},
                    new Object[]{"Quel paradigme définit le mieux l'évolution de « " + titre + " » ?",
                            new String[]{"Stabilité des fondements", "Ruptures et continuités progressives", "Révolution technologique constante", "Standardisation universelle"}, "B",
                            "Les domaines évoluent par ruptures et continuités progressives."},
                    new Object[]{"Quelle relation unit les concepts fondateurs de « " + titre + " » ?",
                            new String[]{"Ils sont indépendants", "Ils forment un système cohérent et interdépendant", "Ils se contredisent souvent", "Ils évoluent séparément"}, "B",
                            "Les concepts fondateurs forment un système où chacun influence les autres."},
                    new Object[]{"Quelle limite théorique doit-on connaître avant d'approfondir « " + titre + " » ?",
                            new String[]{"Il n'y a aucune limite", "Tout modèle théorique a un domaine de validité", "La théorie est toujours exacte", "Les limites sont uniquement pratiques"}, "B",
                            "Tout modèle théorique possède un domaine de validité précis."},
                    new Object[]{"Comment évolue la définition d'un concept clé dans « " + titre + " » ?",
                            new String[]{"Elle reste figée", "Elle s'enrichit par la recherche et la pratique", "Elle se simplifie toujours", "Elle devient obsolète rapidement"}, "B",
                            "Les définitions s'enrichissent grâce aux avancées de la recherche."},
                    new Object[]{"Quelle approche critique est nécessaire pour maîtriser « " + titre + " » à un niveau expert ?",
                            new String[]{"Accepter tous les modèles sans questionnement", "Questionner les présupposés et tester les limites", "Mémoriser les définitions officielles", "Suivre uniquement les standards établis"}, "B",
                            "L'expert questionne les présupposés et teste les limites des modèles."},
                    new Object[]{"Laquelle de ces affirmations sur « " + titre + " » est FAUSSE ?",
                            new String[]{"Les concepts évoluent avec le contexte", "Un seul modèle suffit pour couvrir tous les cas", "La pratique enrichit la théorie", "Les experts remettent en question les présupposés"}, "B",
                            "Aucun modèle unique ne peut couvrir tous les cas dans un domaine mature."},
                    new Object[]{"Quel facteur est le plus souvent sous-estimé dans la maîtrise de « " + titre + " » ?",
                            new String[]{"Le temps d'apprentissage", "L'interaction entre les sous-domaines", "Le nombre d'outils disponibles", "La quantité de ressources en ligne"}, "B",
                            "L'interaction entre les sous-domaines est systématiquement sous-estimée par les apprenants."}
            ));
            default -> data = new ArrayList<>(List.of(
                    new Object[]{"Quelle définition correspond le mieux à « " + titre + " » ?",
                            new String[]{"Une technique obsolète", "Un ensemble structuré de méthodes et connaissances", "Un outil logiciel", "Une certification"}, "B",
                            "Ce cours repose sur un ensemble structuré de méthodes et connaissances."},
                    new Object[]{"Quel est l'objet principal d'étude dans « " + titre + " » ?",
                            new String[]{"Les outils technologiques", "Les concepts et leur application", "Les certifications disponibles", "Les logiciels utilisés"}, "B",
                            "L'objet principal est la compréhension des concepts et leur application."},
                    new Object[]{"Comment définir la portée de « " + titre + " » ?",
                            new String[]{"Limitée à un seul outil", "Large, couvrant méthodes et contextes variés", "Réservée aux experts", "Spécifique à une industrie"}, "B",
                            "Sa portée est large, couvrant méthodes et contextes variés."},
                    new Object[]{"Quel fondement théorique distingue « " + titre + " » d'autres disciplines ?",
                            new String[]{"Ses outils propriétaires", "Ses concepts et cadres analytiques spécifiques", "Sa popularité", "Son ancienneté"}, "B",
                            "Les concepts et cadres analytiques spécifiques fondent l'identité d'une discipline."},
                    new Object[]{"Quelle compréhension est nécessaire avant d'avancer dans « " + titre + " » ?",
                            new String[]{"Connaître tous les outils", "Maîtriser les concepts fondamentaux", "Obtenir une certification", "Avoir de l'expérience"}, "B",
                            "La maîtrise des concepts fondamentaux est le préalable indispensable."},
                    new Object[]{"En quoi « " + titre + " » se distingue-t-il d'une simple compétence technique ?",
                            new String[]{"Il est plus difficile à apprendre", "Il intègre théorie, méthode et sens critique", "Il nécessite plus d'outils", "Il est réservé aux universitaires"}, "B",
                            "L'intégration de la théorie, de la méthode et du sens critique le distingue."},
                    new Object[]{"Quel lien existe entre les concepts de « " + titre + " » ?",
                            new String[]{"Ils sont totalement indépendants", "Ils forment un tout cohérent", "Seul un concept est important", "Ils s'opposent souvent"}, "B",
                            "Les concepts d'un domaine forment un ensemble cohérent et interdépendant."},
                    new Object[]{"Pourquoi est-il important de bien définir les termes dans « " + titre + " » ?",
                            new String[]{"Pour impressionner les collègues", "Pour éviter les malentendus et progresser", "Pour passer les examens", "Pour utiliser les bons outils"}, "B",
                            "Une terminologie précise évite les malentendus et facilite la progression."},
                    new Object[]{"Quelle est la meilleure façon d'approfondir « " + titre + " » ?",
                            new String[]{"Mémoriser les définitions", "Relier les concepts entre eux et les contextualiser", "Multiplier les outils", "Lire uniquement des résumés"}, "B",
                            "Relier les concepts et les contextualiser est la voie vers une compréhension profonde."},
                    new Object[]{"Comment identifier un concept clé dans « " + titre + " » ?",
                            new String[]{"Par sa longueur dans le texte", "Par sa récurrence et son rôle structurant", "Par sa difficulté", "Par sa position dans le cours"}, "B",
                            "Un concept clé se reconnaît à sa récurrence et à son rôle structurant."},
                    new Object[]{"Laquelle de ces affirmations sur « " + titre + " » est FAUSSE ?",
                            new String[]{"Les concepts sont liés entre eux", "Un seul concept suffit pour tout comprendre", "La pratique enrichit la compréhension", "Les définitions évoluent avec le contexte"}, "B",
                            "Aucun concept unique ne suffit — ils sont interdépendants."},
                    new Object[]{"Quelle est la différence entre comprendre et mémoriser dans « " + titre + " » ?",
                            new String[]{"Il n'y a pas de différence", "Comprendre permet d'adapter, mémoriser permet de réciter", "Mémoriser est plus utile", "Comprendre est réservé aux experts"}, "B",
                            "Comprendre permet d'adapter ses connaissances à de nouveaux contextes, contrairement à la simple mémorisation."}
            ));
        }
        return buildFromData(data);
    }

    // ══════════════════════════════════════════════════════════
    // POOL PRATIQUES
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> buildQuestionsPratiques(String titre, String diff) {
        List<Object[]> data;
        switch (diff) {
            case "FACILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Comment appliquer « " + titre + " » dans un projet simple ?",
                            new String[]{"En ignorant les étapes", "En suivant une méthode structurée", "En improvisant", "En copiant sans adapter"}, "B",
                            "Suivre une méthode structurée est la base de toute application réussie."},
                    new Object[]{"Quelle est la première étape pratique de « " + titre + " » ?",
                            new String[]{"Passer à l'action immédiatement", "Analyser le contexte et les besoins", "Consulter un expert", "Acheter un outil"}, "B",
                            "Analyser le contexte et les besoins est toujours la première étape."},
                    new Object[]{"Dans quel cas « " + titre + " » est-il le plus utile ?",
                            new String[]{"Pour des tâches sans objectif", "Pour résoudre des problèmes concrets", "Pour remplacer un expert", "Pour automatiser sans réfléchir"}, "B",
                            "La résolution de problèmes concrets est l'utilité principale."},
                    new Object[]{"Quelle méthode facilite la mise en pratique de « " + titre + " » ?",
                            new String[]{"Mémoriser sans pratiquer", "Appliquer sur des cas réels", "Lire uniquement la théorie", "Observer sans participer"}, "B",
                            "Appliquer sur des cas réels est la méthode la plus efficace."},
                    new Object[]{"Comment mesurer l'application correcte de « " + titre + " » ?",
                            new String[]{"Par le nombre d'heures passées", "Par les résultats obtenus", "Par le nombre d'outils utilisés", "Par les certifications obtenues"}, "B",
                            "Les résultats obtenus sont le meilleur indicateur."},
                    new Object[]{"Quelle erreur éviter avec « " + titre + " » ?",
                            new String[]{"Prendre trop de temps à analyser", "Appliquer sans adapter au contexte", "Demander de l'aide", "Tester ses hypothèses"}, "B",
                            "Appliquer sans adapter au contexte est l'erreur la plus fréquente."},
                    new Object[]{"Quel bénéfice apporte « " + titre + " » au quotidien ?",
                            new String[]{"Aucun bénéfice immédiat", "Une meilleure efficacité", "Un gain de temps uniquement", "Une économie d'argent uniquement"}, "B",
                            "Une meilleure efficacité dans les tâches liées est le bénéfice immédiat."},
                    new Object[]{"Comment commencer à pratiquer « " + titre + " » efficacement ?",
                            new String[]{"Commencer par les cas les plus complexes", "Commencer simple et progresser", "Attendre d'avoir tout lu", "Imiter sans comprendre"}, "B",
                            "Commencer simple et progresser est la méthode pédagogique la plus efficace."},
                    new Object[]{"Quel comportement favorise la maîtrise de « " + titre + " » ?",
                            new String[]{"Éviter les erreurs à tout prix", "Pratiquer régulièrement et apprendre de ses erreurs", "Ne pratiquer que quand nécessaire", "Toujours demander à un expert"}, "B",
                            "La pratique régulière et l'apprentissage par les erreurs favorisent la maîtrise."},
                    new Object[]{"Quelle ressource aide le plus à progresser dans « " + titre + " » ?",
                            new String[]{"Les définitions seules", "Les exercices pratiques sur des cas réels", "Les résumés théoriques", "Les vidéos sans exercice"}, "B",
                            "Les exercices sur des cas réels ancrent l'apprentissage durablement."},
                    new Object[]{"Quel signe montre qu'on progresse dans « " + titre + " » ?",
                            new String[]{"On peut réciter les définitions", "On peut résoudre des problèmes nouveaux sans aide", "On a terminé tous les modules", "On a obtenu une certification"}, "B",
                            "La capacité à résoudre des problèmes nouveaux est le vrai signe de progression."},
                    new Object[]{"Pourquoi pratiquer « " + titre + " » sur des exemples variés ?",
                            new String[]{"Pour aller plus vite", "Pour développer une adaptabilité face à différentes situations", "Pour mémoriser plus facilement", "Pour impressionner son entourage"}, "B",
                            "La variété des exemples développe l'adaptabilité face à des situations inédites."}
            ));
            case "DIFFICILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Face à un problème complexe avec « " + titre + " », quelle démarche choisir ?",
                            new String[]{"Appliquer la première solution connue", "Décomposer, analyser le contexte, puis agir", "Chercher un exemple similaire", "Déléguer à un expert"}, "B",
                            "La décomposition et l'analyse contextuelle caractérisent l'expert."},
                    new Object[]{"Quelle est la principale limite de l'approche théorique dans « " + titre + " » ?",
                            new String[]{"Elle est trop complexe", "Elle néglige les contraintes réelles du terrain", "Elle est réservée aux chercheurs", "Elle donne de mauvais résultats"}, "B",
                            "La théorie fournit un cadre mais doit être adaptée aux contraintes réelles."},
                    new Object[]{"Des résultats inattendus après application de « " + titre + " » — quelle réaction ?",
                            new String[]{"Abandonner la méthode", "Continuer sans questionnement", "Analyser l'écart attendu/obtenu pour ajuster", "Changer immédiatement d'approche"}, "C",
                            "L'analyse des écarts est fondamentale pour progresser."},
                    new Object[]{"Comment optimiser l'impact de « " + titre + " » dans un contexte contraint ?",
                            new String[]{"Réduire les ambitions", "Prioriser les actions à fort impact contextuel", "Augmenter les ressources", "Simplifier sans analyser"}, "B",
                            "Prioriser les actions à fort impact est la clé de l'optimisation."},
                    new Object[]{"Quel facteur influence le plus la qualité de l'application de « " + titre + " » ?",
                            new String[]{"Les outils disponibles uniquement", "Le temps alloué uniquement", "La synergie compétences + méthode + contexte", "L'expérience seule"}, "C",
                            "La synergie entre compétences, méthode et contexte est déterminante."},
                    new Object[]{"Quand « " + titre + " » devient-il contre-productif ?",
                            new String[]{"Quand on manque de temps", "Quand le contexte ne correspond pas aux prérequis", "En travail d'équipe", "Avec beaucoup de ressources"}, "B",
                            "Ignorer les prérequis contextuels peut rendre une méthode contre-productive."},
                    new Object[]{"Comment gérer un conflit de méthodes dans « " + titre + " » ?",
                            new String[]{"Choisir arbitrairement", "Analyser les forces/faiblesses selon le contexte", "Ignorer le conflit", "Appliquer les deux simultanément"}, "B",
                            "L'analyse comparative selon le contexte permet un choix éclairé."},
                    new Object[]{"Quelle compétence transversale amplifie l'impact de « " + titre + " » ?",
                            new String[]{"La mémorisation", "L'analyse critique et la pensée systémique", "La rapidité d'exécution", "La maîtrise d'un seul outil"}, "B",
                            "La pensée systémique et l'analyse critique permettent d'exploiter pleinement le domaine."},
                    new Object[]{"Comment transférer les acquis de « " + titre + " » à un nouveau contexte ?",
                            new String[]{"En appliquant exactement les mêmes étapes", "En identifiant les invariants et en adaptant le reste", "En recommençant de zéro", "En cherchant un nouveau cours"}, "B",
                            "Identifier les invariants transférables et adapter le reste est la clé du transfert."},
                    new Object[]{"Quel indicateur prouve une maîtrise experte de « " + titre + " » ?",
                            new String[]{"Connaître toutes les définitions", "Pouvoir enseigner et adapter dans des situations inédites", "Avoir suivi tous les cours", "Maîtriser tous les outils"}, "B",
                            "La capacité à enseigner et à s'adapter à des situations inédites est la marque de l'expertise."},
                    new Object[]{"Laquelle de ces pratiques avancées est INCORRECTE pour « " + titre + " » ?",
                            new String[]{"Tester ses hypothèses dans des contextes variés", "Appliquer mécaniquement les mêmes étapes à chaque situation", "Remettre en question ses propres méthodes", "Analyser les échecs autant que les succès"}, "B",
                            "Appliquer mécaniquement sans adaptation est l'approche contraire de l'expertise."},
                    new Object[]{"Quel est le risque principal d'une maîtrise partielle de « " + titre + " » ?",
                            new String[]{"Apprendre trop vite", "Surestimer sa capacité à gérer des cas complexes", "Négliger les outils de base", "Se spécialiser trop tôt"}, "B",
                            "La maîtrise partielle génère une sur-confiance dangereuse face aux cas complexes."}
            ));
            default -> data = new ArrayList<>(List.of(
                    new Object[]{"Comment appliquer efficacement « " + titre + " » en situation réelle ?",
                            new String[]{"En suivant les règles sans adaptation", "En ignorant le contexte", "En adaptant la méthode au contexte", "En improvisant"}, "C",
                            "L'adaptation au contexte spécifique est la clé d'une application efficace."},
                    new Object[]{"Quel défi rencontrez-vous lors de la mise en pratique de « " + titre + " » ?",
                            new String[]{"Le manque de ressources", "Relier théorie et pratique dans un contexte réel", "La barrière de la langue", "Le coût des formations"}, "B",
                            "Relier théorie et pratique dans un contexte réel est le défi central."},
                    new Object[]{"Quelle approche donne les meilleurs résultats avec « " + titre + " » ?",
                            new String[]{"Apprendre par cœur les procédures", "Expérimenter sur des cas variés", "Utiliser toujours le même outil", "Suivre un seul modèle"}, "B",
                            "Expérimenter sur des cas variés développe une vraie maîtrise pratique."},
                    new Object[]{"Comment évaluer sa maîtrise pratique de « " + titre + " » ?",
                            new String[]{"Par le nombre de cours suivis", "Par la capacité à l'appliquer dans des situations variées", "Par les diplômes obtenus", "Par l'ancienneté"}, "B",
                            "La maîtrise se mesure à la capacité d'application dans des situations variées."},
                    new Object[]{"Quel retour d'expérience est le plus formateur dans « " + titre + " » ?",
                            new String[]{"Les succès uniquement", "Les erreurs analysées et corrigées", "Les exemples théoriques", "Les avis des experts"}, "B",
                            "L'analyse et la correction des erreurs est la source d'apprentissage la plus profonde."},
                    new Object[]{"Dans quelle situation « " + titre + " » apporte-t-il le plus de valeur ?",
                            new String[]{"Pour des tâches routinières simples", "Pour résoudre des problèmes complexes nécessitant méthode", "Pour remplacer un spécialiste", "Pour automatiser sans réfléchir"}, "B",
                            "La résolution de problèmes complexes nécessitant une méthode est la plus haute valeur ajoutée."},
                    new Object[]{"Quelle étape est souvent négligée dans la pratique de « " + titre + " » ?",
                            new String[]{"L'exécution", "L'évaluation des résultats obtenus", "La planification", "La documentation"}, "B",
                            "L'évaluation des résultats est souvent négligée alors qu'elle est essentielle."},
                    new Object[]{"Comment améliorer ses résultats pratiques dans « " + titre + " » ?",
                            new String[]{"En répétant toujours la même méthode", "En analysant ses erreurs et en ajustant l'approche", "En cherchant plus d'outils", "En suivant plus de cours théoriques"}, "B",
                            "Analyser ses erreurs et ajuster l'approche est le moteur de l'amélioration continue."},
                    new Object[]{"Quel est l'avantage de pratiquer « " + titre + " » sur des projets réels ?",
                            new String[]{"Cela remplace la théorie", "Cela ancre les apprentissages et révèle les lacunes", "Cela garantit le succès", "Cela évite les erreurs"}, "B",
                            "La pratique sur projets réels ancre les apprentissages et révèle les lacunes."},
                    new Object[]{"Comment planifier une application efficace de « " + titre + " » ?",
                            new String[]{"Sans planification préalable", "En définissant objectifs, étapes et indicateurs de succès", "En copiant un plan existant", "En agissant par intuition"}, "B",
                            "Définir objectifs, étapes et indicateurs de succès est la base d'une bonne planification."},
                    new Object[]{"Laquelle de ces pratiques est CONTRE-PRODUCTIVE dans « " + titre + " » ?",
                            new String[]{"Expérimenter sur des cas variés", "Reproduire mécaniquement les mêmes étapes sans réfléchir", "Analyser ses erreurs", "Adapter la méthode au contexte"}, "B",
                            "Reproduire mécaniquement sans réfléchir empêche tout progrès réel."},
                    new Object[]{"Quelle est la différence entre pratiquer et maîtriser dans « " + titre + " » ?",
                            new String[]{"Il n'y a pas de différence", "Pratiquer c'est appliquer, maîtriser c'est adapter et innover", "Maîtriser c'est pratiquer plus longtemps", "Pratiquer est réservé aux débutants"}, "B",
                            "La maîtrise va au-delà de la simple pratique : elle implique l'adaptation et l'innovation."}
            ));
        }
        return buildFromData(data);
    }

    // ══════════════════════════════════════════════════════════
    // UTILITAIRE
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> buildFromData(List<Object[]> data) {
        String[] lettres = {"A", "B", "C", "D"};
        List<QuizQuestionIA> questions = new ArrayList<>();
        for (Object[] d : data) {
            QuizQuestionIA q = new QuizQuestionIA();
            q.setTexte((String) d[0]);
            q.setExplication((String) d[3]);
            String[] optionsTexte = (String[]) d[1];
            String   bonneReponse = (String)   d[2];
            List<OptionIA> opts = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                OptionIA opt = new OptionIA();
                opt.setOrdre(lettres[j]);
                opt.setTexte(optionsTexte[j]);
                opt.setEstCorrecte(lettres[j].equals(bonneReponse));
                opts.add(opt);
            }
            q.setOptions(opts);
            questions.add(q);
        }
        return questions;
    }
}
