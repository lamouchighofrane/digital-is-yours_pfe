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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    // ══════════════════════════════════════════════════════════
    // POINT D'ENTRÉE PRINCIPAL
    // ══════════════════════════════════════════════════════════

    /**
     * Génère un quiz IA basé sur le contenu réel du cours (PDF + vidéo YouTube).
     * En cas d'échec de l'API Groq, bascule sur le mode simulation.
     *
     * @param coursInfo          Titre, description, objectifs du cours
     * @param coursId            ID du cours pour récupérer les documents PDF
     * @param videoUrl           URL de la vidéo YouTube (peut être null)
     * @param videoType          "YOUTUBE", "LOCAL" ou null
     * @param nbQuestions        Nombre de questions demandées (3 à 10)
     * @param difficulte         "FACILE", "MOYEN" ou "DIFFICILE"
     * @param inclureDefinitions Inclure des questions sur les définitions
     * @param inclureCasPratiques Inclure des questions sur les cas pratiques
     */
    public List<QuizQuestionIA> genererQuiz(
            CoursInfoIA coursInfo,
            Long coursId,
            String videoUrl,
            String videoType,
            int nbQuestions,
            String difficulte,
            boolean inclureDefinitions,
            boolean inclureCasPratiques) {

        // ── Extraction du contenu réel du cours ──────────────
        String contenuPdf = "";
        String transcriptionVideo = "";

        if (coursId != null) {
            contenuPdf = contentExtractor.extrairePdfs(coursId);
            if (!contenuPdf.isBlank()) {
                log.info("Contenu PDF extrait : {} caractères pour le cours {}", contenuPdf.length(), coursId);
            } else {
                log.info("Aucun PDF disponible pour le cours {}", coursId);
            }
        }

        if ("YOUTUBE".equals(videoType) && videoUrl != null && !videoUrl.isBlank()) {
            transcriptionVideo = contentExtractor.extraireTranscriptionYoutube(videoUrl);
            if (!transcriptionVideo.isBlank()) {
                log.info("Transcription YouTube extraite : {} caractères", transcriptionVideo.length());
            } else {
                log.info("Pas de transcription YouTube disponible pour : {}", videoUrl);
            }
        }

        // ── Appel IA Groq ─────────────────────────────────────
        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("Clé Groq manquante — mode simulation activé");
            return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques);
        }

        try {
            // Seed aléatoire unique pour forcer Groq à varier les questions
            int seed = new java.util.Random().nextInt(100000);
            String prompt = buildPrompt(coursInfo, contenuPdf, transcriptionVideo,
                    nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques, seed);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", GROQ_MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "Tu es un expert pédagogique. Réponds UNIQUEMENT avec un tableau JSON valide, "
                                            + "sans markdown, sans texte avant ou après, sans balises ```json."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 1.0,
                    "max_tokens", 4000
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                log.warn("Quota Groq dépassé (429) — mode simulation activé");
                return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques);
            }

            if (response.statusCode() != 200) {
                log.error("Erreur API Groq: {} — {}", response.statusCode(), response.body());
                return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques);
            }

            List<QuizQuestionIA> questions = parseGroqResponse(response.body());

            // ── Aléatoire : mélanger questions ET options ──────
            Collections.shuffle(questions);
            String[] lettres = {"A", "B", "C", "D"};
            for (QuizQuestionIA q : questions) {
                // Mémoriser le texte de la bonne réponse avant shuffle
                String bonneReponseTexte = q.getOptions().stream()
                        .filter(OptionIA::isEstCorrecte)
                        .map(OptionIA::getTexte)
                        .findFirst().orElse("");
                // Mélanger les options
                Collections.shuffle(q.getOptions());
                // Réassigner A/B/C/D et recalibrer estCorrecte
                for (int i = 0; i < q.getOptions().size(); i++) {
                    OptionIA opt = q.getOptions().get(i);
                    opt.setOrdre(lettres[i]);
                    opt.setEstCorrecte(opt.getTexte().equals(bonneReponseTexte));
                }
            }

            log.info("Génération IA Groq réussie : {} questions générées (ordre aléatoire)", questions.size());
            return questions;

        } catch (Exception e) {
            log.error("Erreur lors de la génération IA Groq: {}", e.getMessage());
            return genererQuizSimule(coursInfo, nbQuestions, difficulte, inclureDefinitions, inclureCasPratiques);
        }
    }

    // ══════════════════════════════════════════════════════════
    // CONSTRUCTION DU PROMPT — contenu PDF + YouTube intégré
    // ══════════════════════════════════════════════════════════

    private String buildPrompt(CoursInfoIA info, String contenuPdf, String transcriptionVideo,
                               int nbQ, String diff, boolean def, boolean prat, int seed) {

        String diffNorm = (diff != null) ? diff.toUpperCase() : "MOYEN";
        boolean hasRealContent = !contenuPdf.isBlank() || !transcriptionVideo.isBlank();

        StringBuilder sb = new StringBuilder();

        // ── Rôle et mission ────────────────────────────────────
        sb.append("Tu es un expert pédagogique qui crée des QCM de niveau universitaire.\n");
        sb.append("Tu dois générer exactement ").append(nbQ).append(" questions QCM.\n\n");

        // ── Contenu source ────────────────────────────────────
        sb.append("COURS : ").append(info.getTitre()).append("\n");
        if (info.getDescription() != null && !info.getDescription().isBlank()) {
            sb.append("DESCRIPTION : ").append(info.getDescription()).append("\n");
        }
        if (info.getObjectifs() != null && !info.getObjectifs().isBlank()) {
            sb.append("OBJECTIFS : ").append(info.getObjectifs()).append("\n");
        }

        if (!contenuPdf.isBlank()) {
            sb.append("\n========== CONTENU DU DOCUMENT PDF ==========\n");
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

        // ── Types de questions ────────────────────────────────
        if (def && prat) {
            sb.append("\nTYPES : mélange de questions sur les définitions/concepts ET sur l'application pratique.\n");
        } else if (def) {
            sb.append("\nTYPES : questions sur les définitions et la compréhension des concepts.\n");
        } else if (prat) {
            sb.append("\nTYPES : questions sur l'application pratique et les cas concrets.\n");
        }

        // ── Instructions STRICTES par niveau ─────────────────
        switch (diffNorm) {
            case "FACILE" -> {
                sb.append("\n==== NIVEAU : FACILE (débutant) ====\n");
                sb.append("Le niveau FACILE signifie :\n");
                sb.append("- Questions simples et directes sur les définitions de base\n");
                sb.append("- La bonne réponse est ÉVIDENTE pour quelqu'un qui a juste lu le contenu une fois\n");
                sb.append("- Les 3 mauvaises réponses sont clairement fausses ou hors-sujet\n");
                sb.append("- Formulations autorisées : 'Qu'est-ce que X ?', 'Quel est le rôle de X ?', 'Que signifie X ?'\n");
                sb.append("\nEXEMPLES DE BONNES QUESTIONS FACILES :\n");
                sb.append("✓ 'Que signifie l'acronyme PBN ?' → réponse directe dans le texte\n");
                sb.append("✓ 'Qu'est-ce qu'un backlink ?' → définition basique\n");
                sb.append("✓ 'Quel type de lien transmet du jus SEO ?' → réponse explicite dans le document\n");
                sb.append("\nEXEMPLES DE MAUVAISES QUESTIONS FACILES (trop difficiles pour ce niveau) :\n");
                sb.append("✗ 'Pourquoi un lien DA 70 vaut-il 100 liens faibles ?' → trop analytique\n");
                sb.append("✗ 'Dans quel cas un lien nofollow peut-il quand même avoir de la valeur ?' → trop nuancé\n");
            }
            case "MOYEN" -> {
                sb.append("\n==== NIVEAU : MOYEN (intermédiaire) ====\n");
                sb.append("Le niveau MOYEN signifie :\n");
                sb.append("- Questions de compréhension : il faut avoir bien lu et compris le contenu\n");
                sb.append("- La bonne réponse n'est pas immédiatement évidente sans avoir étudié\n");
                sb.append("- Les 3 mauvaises réponses sont PLAUSIBLES (elles semblent presque correctes)\n");
                sb.append("- Formulations : 'Pourquoi X est-il important ?', 'Quelle est la différence entre X et Y ?', 'Lequel de ces éléments...'\n");
                sb.append("\nEXEMPLES DE BONNES QUESTIONS MOYENNES :\n");
                sb.append("✓ 'Pourquoi la position d'un lien dans la page influence-t-elle sa valeur ?' → nécessite compréhension\n");
                sb.append("✓ 'Quelle stratégie est la PLUS recommandée pour obtenir des backlinks durables ?' → analyse requise\n");
                sb.append("✓ 'Laquelle de ces pratiques est contraire aux règles de Google ?' → discernement nécessaire\n");
                sb.append("\nEXEMPLES DE MAUVAISES QUESTIONS MOYENNES :\n");
                sb.append("✗ 'Qu'est-ce qu'un backlink ?' → trop facile, définition basique\n");
                sb.append("✗ 'Combien de liens par mois est-il recommandé ?' → chiffre exact, niveau difficile\n");
            }
            case "DIFFICILE" -> {
                sb.append("\n==== NIVEAU : DIFFICILE (expert) ====\n");
                sb.append("Le niveau DIFFICILE signifie :\n");
                sb.append("- Questions sur des NUANCES PRÉCISES, CHIFFRES EXACTS, EXCEPTIONS et CAS LIMITES\n");
                sb.append("- Même quelqu'un qui a bien lu le contenu peut se tromper\n");
                sb.append("- Les 3 mauvaises réponses sont TRÈS proches de la bonne réponse (mêmes chiffres voisins, nuances subtiles)\n");
                sb.append("- INTERDIT : 'Qu'est-ce que X ?', 'Quel est le rôle de X ?' → trop basiques\n");
                sb.append("- OBLIGATOIRE : au moins 3 questions avec des CHIFFRES PRÉCIS du contenu\n");
                sb.append("- OBLIGATOIRE : au moins 2 questions 'Laquelle de ces affirmations est FAUSSE ?'\n");
                sb.append("- OBLIGATOIRE : des distracteurs très plausibles (ex: si réponse=100, proposer 50, 150, 200)\n");
                sb.append("\nEXEMPLES DE BONNES QUESTIONS DIFFICILES :\n");
                sb.append("✓ 'Un lien DA 70+ vaut autant que combien de liens faibles ?' → A:50 B:100✓ C:150 D:200 (chiffres proches)\n");
                sb.append("✓ 'La régularité prime : X bons liens/mois valent mieux que Y mauvais en une fois. Quelles sont les valeurs X et Y ?' → A:1 et 20 B:2 et 50✓ C:3 et 30 D:5 et 100\n");
                sb.append("✓ 'Laquelle est FAUSSE : les échanges de liens A) sont contre Google B) transmettent du jus C) créent un profil naturel D) sont faciles à détecter'\n");
                sb.append("✓ 'Dans quel CAS PRÉCIS un lien nofollow peut-il quand même générer de la valeur indirecte ?'\n");
                sb.append("✓ 'Parmi ces 4 critères de valeur d'un backlink, lequel N'EST PAS mentionné dans le document ?' → teste si l'apprenant invente des critères\n");
                sb.append("\nEXEMPLES DE MAUVAISES QUESTIONS DIFFICILES (trop simples) :\n");
                sb.append("✗ 'Qu'est-ce qu'un PBN ?' → définition simple = niveau FACILE\n");
                sb.append("✗ 'Quelle est la durée du cours ?' → trivial = à bannir\n");
                sb.append("✗ 'Quel outil analyser les backlinks ?' → réponse directe dans le texte = niveau FACILE\n");
            }
            default -> sb.append("\nNiveau de difficulté standard.\n");
        }

        // ── Instruction de variété (seed aléatoire) ──────────
        sb.append("\n\nIMPORTANT VARIATION #").append(seed).append(" : ");
        sb.append("Tu DOIS générer des questions DIFFÉRENTES de tes générations précédentes. ");
        sb.append("Explore des angles différents du contenu. Seed=").append(seed).append("\n");

        // ── Format de réponse obligatoire ─────────────────────
        sb.append("\n\nFORMAT DE RÉPONSE : tableau JSON pur uniquement, sans markdown, sans texte avant ou après.\n");
        sb.append("[");
        sb.append("{\"texte\":\"Texte de la question ?\",");
        sb.append("\"explication\":\"Explication de la bonne réponse\",");
        sb.append("\"options\":[");
        sb.append("{\"ordre\":\"A\",\"texte\":\"Option A\",\"estCorrecte\":false},");
        sb.append("{\"ordre\":\"B\",\"texte\":\"Option B\",\"estCorrecte\":true},");
        sb.append("{\"ordre\":\"C\",\"texte\":\"Option C\",\"estCorrecte\":false},");
        sb.append("{\"ordre\":\"D\",\"texte\":\"Option D\",\"estCorrecte\":false}");
        sb.append("]}");
        sb.append("]\n");
        sb.append("RÈGLES STRICTES : exactement 4 options par question, ");
        sb.append("exactement 1 seule option avec estCorrecte:true par question, ");
        sb.append("pas de markdown, pas de texte avant ou après le JSON.");

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    // PARSING DE LA RÉPONSE GROQ (format OpenAI-compatible)
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> parseGroqResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root
                .path("choices").get(0)
                .path("message")
                .path("content").asText().trim();

        // Nettoyage des éventuels artefacts markdown
        if (content.contains("```json")) {
            content = content.substring(content.indexOf("```json") + 7);
        }
        if (content.contains("```")) {
            content = content.substring(0, content.lastIndexOf("```"));
        }
        content = content.trim();

        // S'assurer que le contenu commence par [
        int jsonStart = content.indexOf('[');
        if (jsonStart > 0) content = content.substring(jsonStart);

        JsonNode questionsNode = objectMapper.readTree(content);
        List<QuizQuestionIA> questions = new ArrayList<>();

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
            questions.add(q);
        }

        return questions;
    }

    // ══════════════════════════════════════════════════════════
    // MODE SIMULATION — fallback si clé absente ou quota dépassé
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> genererQuizSimule(CoursInfoIA info, int nbQ, String diff,
                                                   boolean inclDef, boolean inclPrat) {
        log.info("Simulation quiz — cours: '{}' niveau: {} nbQ: {} def: {} prat: {}",
                info.getTitre(), diff, nbQ, inclDef, inclPrat);

        String titre    = info.getTitre() != null ? info.getTitre() : "ce cours";
        String diffNorm = (diff != null) ? diff.toUpperCase() : "MOYEN";

        List<QuizQuestionIA> poolDef  = buildQuestionsDefinitions(titre, diffNorm);
        List<QuizQuestionIA> poolPrat = buildQuestionsPratiques(titre, diffNorm);

        Collections.shuffle(poolDef);
        Collections.shuffle(poolPrat);

        List<QuizQuestionIA> pool = new ArrayList<>();
        if (inclDef && inclPrat) {
            pool.addAll(poolDef);
            pool.addAll(poolPrat);
            Collections.shuffle(pool);
        } else if (inclDef) {
            pool.addAll(poolDef);
        } else if (inclPrat) {
            pool.addAll(poolPrat);
        } else {
            pool.addAll(poolDef);
            pool.addAll(poolPrat);
            Collections.shuffle(pool);
        }

        int count = Math.min(nbQ, pool.size());
        return new ArrayList<>(pool.subList(0, count));
    }

    // ══════════════════════════════════════════════════════════
    // POOL DÉFINITIONS — 10 questions par niveau
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> buildQuestionsDefinitions(String titre, String diff) {
        List<Object[]> data;

        switch (diff) {
            case "FACILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Qu'est-ce que « " + titre + " » ?",
                            new String[]{"Un domaine d'étude spécialisé", "Un logiciel", "Un langage de programmation", "Un système d'exploitation"}, "A",
                            "Ce cours porte sur un domaine d'étude spécialisé."},
                    new Object[]{"Quelle est la définition principale associée à « " + titre + " » ?",
                            new String[]{"Un ensemble de méthodes et connaissances", "Un outil de gestion", "Un protocole réseau", "Un algorithme"}, "A",
                            "Un ensemble structuré de méthodes et connaissances est la définition fondamentale."},
                    new Object[]{"Quel terme décrit le mieux le concept central de « " + titre + " » ?",
                            new String[]{"Apprentissage simple", "Compétence structurée", "Interface utilisateur", "Base de données"}, "B",
                            "Une compétence structurée est au cœur de ce domaine."},
                    new Object[]{"Parmi ces définitions, laquelle correspond à « " + titre + " » ?",
                            new String[]{"Une technologie obsolète", "Une discipline avec ses propres concepts", "Un format de fichier", "Un serveur web"}, "B",
                            "C'est une discipline avec ses propres concepts et méthodes."},
                    new Object[]{"Comment peut-on qualifier « " + titre + " » dans un contexte professionnel ?",
                            new String[]{"Une compétence optionnelle", "Un savoir-faire valorisé", "Un outil remplacé", "Une certification obsolète"}, "B",
                            "C'est un savoir-faire reconnu et valorisé professionnellement."},
                    new Object[]{"Quel vocabulaire est fondamental pour comprendre « " + titre + " » ?",
                            new String[]{"Les termes propres à ce domaine", "Le jargon marketing", "Les termes juridiques", "Le vocabulaire médical"}, "A",
                            "La maîtrise du vocabulaire spécifique est essentielle dans tout domaine."},
                    new Object[]{"Quelle est la première chose à apprendre dans « " + titre + " » ?",
                            new String[]{"Les outils avancés", "Les concepts de base", "Les cas complexes", "Les certifications"}, "B",
                            "Les concepts de base sont toujours le point de départ de tout apprentissage."},
                    new Object[]{"À quoi sert principalement « " + titre + " » ?",
                            new String[]{"À automatiser des tâches", "À structurer et développer des compétences", "À remplacer des experts", "À générer des revenus"}, "B",
                            "L'objectif principal est de structurer et développer des compétences."},
                    new Object[]{"Quelle compétence de base est nécessaire pour aborder « " + titre + " » ?",
                            new String[]{"Une expérience avancée", "Une curiosité intellectuelle et une base théorique", "Des certifications", "Des outils spécialisés"}, "B",
                            "La curiosité et une base théorique suffisent pour débuter."},
                    new Object[]{"Quel objectif principal vise « " + titre + " » ?",
                            new String[]{"Mémoriser des formules", "Développer une compréhension globale du domaine", "Maîtriser un seul outil", "Obtenir une certification"}, "B",
                            "Développer une compréhension globale est l'objectif central de tout cours."}
            ));
            case "DIFFICILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Quelle distinction conceptuelle est fondamentale dans « " + titre + " » ?",
                            new String[]{"Entre théorie et pratique", "Entre concept et outil", "Entre méthode et résultat", "Toutes ces distinctions"}, "D",
                            "Dans un domaine avancé, toutes ces distinctions conceptuelles sont importantes."},
                    new Object[]{"Quelle nuance terminologique différencie les experts des débutants dans « " + titre + " » ?",
                            new String[]{"La précision du vocabulaire", "La vitesse d'exécution", "Le nombre d'outils maîtrisés", "L'ancienneté"}, "A",
                            "La précision du vocabulaire technique distingue les experts des débutants."},
                    new Object[]{"Quel concept de « " + titre + " » est souvent mal compris par les praticiens intermédiaires ?",
                            new String[]{"La relation entre cause et effet", "L'interdépendance des variables contextuelles", "La hiérarchie des priorités", "L'optimisation des ressources"}, "B",
                            "L'interdépendance des variables contextuelles est un concept avancé souvent sous-estimé."},
                    new Object[]{"Parmi ces assertions sur « " + titre + " », laquelle est la plus exacte ?",
                            new String[]{"Les définitions sont universelles", "Les définitions évoluent avec le contexte", "Un seul modèle théorique suffit", "La pratique prime toujours sur la théorie"}, "B",
                            "Les définitions et concepts évoluent avec le contexte et les avancées du domaine."},
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
                    new Object[]{"Comment évolue la définition d'un concept clé dans « " + titre + " » au fil du temps ?",
                            new String[]{"Elle reste figée", "Elle s'enrichit par la recherche et la pratique", "Elle se simplifie toujours", "Elle devient obsolète rapidement"}, "B",
                            "Les définitions s'enrichissent grâce aux avancées de la recherche et de la pratique."},
                    new Object[]{"Quelle approche critique est nécessaire pour maîtriser « " + titre + " » à un niveau expert ?",
                            new String[]{"Accepter tous les modèles sans questionnement", "Questionner les présupposés et tester les limites", "Mémoriser les définitions officielles", "Suivre uniquement les standards établis"}, "B",
                            "L'expert questionne les présupposés et teste les limites des modèles pour les dépasser."}
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
                            "L'intégration de la théorie, de la méthode et du sens critique le distingue d'une simple technique."},
                    new Object[]{"Quel lien existe entre les concepts de « " + titre + " » ?",
                            new String[]{"Ils sont totalement indépendants", "Ils forment un tout cohérent", "Seul un concept est important", "Ils s'opposent souvent"}, "B",
                            "Les concepts d'un domaine forment un ensemble cohérent et interdépendant."},
                    new Object[]{"Pourquoi est-il important de bien définir les termes dans « " + titre + " » ?",
                            new String[]{"Pour impressionner les collègues", "Pour éviter les malentendus et progresser efficacement", "Pour passer les examens", "Pour utiliser les bons outils"}, "B",
                            "Une terminologie précise évite les malentendus et facilite la progression."},
                    new Object[]{"Quelle est la meilleure façon d'approfondir sa compréhension de « " + titre + " » ?",
                            new String[]{"Mémoriser les définitions", "Relier les concepts entre eux et les contextualiser", "Multiplier les outils", "Lire uniquement des résumés"}, "B",
                            "Relier les concepts et les contextualiser est la voie vers une compréhension profonde."},
                    new Object[]{"Comment identifier un concept clé dans « " + titre + " » ?",
                            new String[]{"Par sa longueur dans le texte", "Par sa récurrence et son rôle structurant", "Par sa difficulté", "Par sa position dans le cours"}, "B",
                            "Un concept clé se reconnaît à sa récurrence et à son rôle structurant dans le domaine."}
            ));
        }

        return buildFromData(data);
    }

    // ══════════════════════════════════════════════════════════
    // POOL PRATIQUES — 10 questions par niveau
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> buildQuestionsPratiques(String titre, String diff) {
        List<Object[]> data;

        switch (diff) {
            case "FACILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Comment appliquer « " + titre + " » dans un projet simple ?",
                            new String[]{"En ignorant les étapes", "En suivant une méthode structurée", "En improvisant à chaque fois", "En copiant un exemple sans l'adapter"}, "B",
                            "Suivre une méthode structurée est la base de toute application réussie."},
                    new Object[]{"Quelle est la première étape pratique lors de l'utilisation de « " + titre + " » ?",
                            new String[]{"Passer à l'action immédiatement", "Analyser le contexte et les besoins", "Consulter un expert", "Acheter un outil"}, "B",
                            "Analyser le contexte et les besoins est toujours la première étape."},
                    new Object[]{"Dans quel cas pratique « " + titre + " » est-il le plus utile ?",
                            new String[]{"Pour des tâches sans objectif", "Pour résoudre des problèmes concrets", "Pour remplacer un expert", "Pour automatiser sans réfléchir"}, "B",
                            "La résolution de problèmes concrets est l'utilité principale."},
                    new Object[]{"Quelle méthode facilite la mise en pratique de « " + titre + " » ?",
                            new String[]{"Mémoriser sans pratiquer", "Appliquer sur des cas réels", "Lire uniquement la théorie", "Observer sans participer"}, "B",
                            "Appliquer sur des cas réels est la méthode la plus efficace."},
                    new Object[]{"Comment mesurer l'application correcte de « " + titre + " » ?",
                            new String[]{"Par le nombre d'heures passées", "Par les résultats obtenus", "Par le nombre d'outils utilisés", "Par les certifications obtenues"}, "B",
                            "Les résultats obtenus sont le meilleur indicateur d'une bonne application."},
                    new Object[]{"Quelle erreur pratique éviter avec « " + titre + " » ?",
                            new String[]{"Prendre trop de temps à analyser", "Appliquer sans adapter au contexte", "Demander de l'aide", "Tester ses hypothèses"}, "B",
                            "Appliquer mécaniquement sans adapter au contexte est l'erreur la plus fréquente."},
                    new Object[]{"Quel bénéfice concret apporte « " + titre + " » au quotidien ?",
                            new String[]{"Aucun bénéfice immédiat", "Une meilleure efficacité dans les tâches liées", "Un gain de temps uniquement", "Une économie d'argent uniquement"}, "B",
                            "Une meilleure efficacité dans les tâches liées est le bénéfice immédiat."},
                    new Object[]{"Comment commencer à pratiquer « " + titre + " » efficacement ?",
                            new String[]{"Commencer par les cas les plus complexes", "Commencer par des exercices simples et progresser", "Attendre d'avoir tout lu", "Imiter sans comprendre"}, "B",
                            "Commencer simple et progresser est la méthode pédagogique la plus efficace."},
                    new Object[]{"Quel comportement favorise la maîtrise de « " + titre + " » ?",
                            new String[]{"Éviter les erreurs à tout prix", "Pratiquer régulièrement et apprendre de ses erreurs", "Ne pratiquer que quand c'est nécessaire", "Toujours demander à un expert"}, "B",
                            "La pratique régulière et l'apprentissage par les erreurs favorisent la maîtrise."},
                    new Object[]{"Quelle ressource pratique aide le plus à progresser dans « " + titre + " » ?",
                            new String[]{"Les définitions seules", "Les exercices pratiques sur des cas réels", "Les résumés théoriques", "Les vidéos sans exercice"}, "B",
                            "Les exercices sur des cas réels ancrent l'apprentissage de façon durable."}
            ));
            case "DIFFICILE" -> data = new ArrayList<>(List.of(
                    new Object[]{"Face à un problème complexe avec « " + titre + " », quelle démarche choisir ?",
                            new String[]{"Appliquer la première solution connue", "Décomposer, analyser le contexte, puis agir", "Chercher un exemple similaire", "Déléguer à un expert"}, "B",
                            "La décomposition et l'analyse contextuelle avant l'action caractérisent l'expert."},
                    new Object[]{"Quelle est la principale limite de l'approche théorique dans « " + titre + " » ?",
                            new String[]{"Elle est trop complexe", "Elle néglige les contraintes réelles du terrain", "Elle est réservée aux chercheurs", "Elle donne de mauvais résultats"}, "B",
                            "La théorie fournit un cadre mais doit être adaptée aux contraintes réelles."},
                    new Object[]{"Des résultats inattendus apparaissent après application de « " + titre + " » — quelle est la meilleure réaction ?",
                            new String[]{"Abandonner la méthode", "Continuer sans questionnement", "Analyser l'écart attendu/obtenu pour ajuster", "Changer immédiatement d'approche"}, "C",
                            "L'analyse des écarts entre attendu et obtenu est fondamentale pour progresser."},
                    new Object[]{"Comment optimiser l'impact de « " + titre + " » dans un contexte contraint ?",
                            new String[]{"Réduire les ambitions", "Prioriser les actions à fort impact contextuel", "Augmenter les ressources", "Simplifier sans analyser"}, "B",
                            "Prioriser les actions à fort impact selon le contexte est la clé de l'optimisation."},
                    new Object[]{"Quel facteur influence le plus la qualité de l'application de « " + titre + " » ?",
                            new String[]{"Les outils disponibles uniquement", "Le temps alloué uniquement", "La synergie compétences + méthode + contexte", "L'expérience seule"}, "C",
                            "La synergie entre compétences, méthode et contexte est déterminante."},
                    new Object[]{"Quand « " + titre + " » devient-il contre-productif ?",
                            new String[]{"Quand on manque de temps", "Quand le contexte ne correspond pas aux prérequis", "En travail d'équipe", "Avec beaucoup de ressources"}, "B",
                            "Ignorer les prérequis contextuels peut rendre une méthode contre-productive."},
                    new Object[]{"Comment gérer un conflit de méthodes dans l'application de « " + titre + " » ?",
                            new String[]{"Choisir arbitrairement", "Analyser les forces/faiblesses de chaque méthode selon le contexte", "Ignorer le conflit", "Appliquer les deux simultanément"}, "B",
                            "L'analyse comparative des méthodes selon le contexte permet un choix éclairé."},
                    new Object[]{"Quelle compétence transversale amplifie l'impact de « " + titre + " » ?",
                            new String[]{"La mémorisation", "L'analyse critique et la pensée systémique", "La rapidité d'exécution", "La maîtrise d'un seul outil"}, "B",
                            "La pensée systémique et l'analyse critique permettent d'exploiter pleinement le domaine."},
                    new Object[]{"Comment transférer les acquis de « " + titre + " » à un nouveau contexte ?",
                            new String[]{"En appliquant exactement les mêmes étapes", "En identifiant les invariants et en adaptant le reste", "En recommençant de zéro", "En cherchant un nouveau cours"}, "B",
                            "Identifier les invariants transférables et adapter le reste est la clé du transfert."},
                    new Object[]{"Quel indicateur prouve une maîtrise experte de « " + titre + " » ?",
                            new String[]{"Connaître toutes les définitions", "Pouvoir enseigner et adapter dans des situations inédites", "Avoir suivi tous les cours disponibles", "Maîtriser tous les outils du domaine"}, "B",
                            "La capacité à enseigner et à s'adapter à des situations inédites est la marque de l'expertise."}
            ));
            default -> data = new ArrayList<>(List.of(
                    new Object[]{"Comment appliquer efficacement « " + titre + " » en situation réelle ?",
                            new String[]{"En suivant les règles sans adaptation", "En ignorant le contexte", "En adaptant la méthode au contexte spécifique", "En improvisant"}, "C",
                            "L'adaptation au contexte spécifique est la clé d'une application efficace."},
                    new Object[]{"Quel défi principal rencontrez-vous lors de la mise en pratique de « " + titre + " » ?",
                            new String[]{"Le manque de ressources", "Relier théorie et pratique dans un contexte réel", "La barrière de la langue", "Le coût des formations"}, "B",
                            "Relier théorie et pratique dans un contexte réel est le défi central."},
                    new Object[]{"Quelle approche pratique donne les meilleurs résultats avec « " + titre + " » ?",
                            new String[]{"Apprendre par cœur les procédures", "Expérimenter sur des cas variés", "Utiliser toujours le même outil", "Suivre un seul modèle"}, "B",
                            "Expérimenter sur des cas variés développe une vraie maîtrise pratique."},
                    new Object[]{"Comment évaluer sa maîtrise pratique de « " + titre + " » ?",
                            new String[]{"Par le nombre de cours suivis", "Par la capacité à l'appliquer dans des situations variées", "Par les diplômes obtenus", "Par l'ancienneté"}, "B",
                            "La maîtrise se mesure à la capacité d'application dans des situations variées."},
                    new Object[]{"Quel retour d'expérience est le plus formateur dans « " + titre + " » ?",
                            new String[]{"Les succès uniquement", "Les erreurs analysées et corrigées", "Les exemples théoriques", "Les avis des experts"}, "B",
                            "L'analyse et la correction des erreurs est la source d'apprentissage la plus profonde."},
                    new Object[]{"Dans quelle situation pratique « " + titre + " » apporte-t-il le plus de valeur ?",
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
                            "La pratique sur projets réels ancre les apprentissages et révèle les lacunes à combler."},
                    new Object[]{"Comment planifier une application efficace de « " + titre + " » ?",
                            new String[]{"Sans planification préalable", "En définissant objectifs, étapes et indicateurs de succès", "En copiant un plan existant", "En agissant par intuition"}, "B",
                            "Définir objectifs, étapes et indicateurs de succès est la base d'une bonne planification."}
            ));
        }

        return buildFromData(data);
    }

    // ══════════════════════════════════════════════════════════
    // UTILITAIRE : construction de QuizQuestionIA depuis tableau
    // ══════════════════════════════════════════════════════════

    private List<QuizQuestionIA> buildFromData(List<Object[]> data) {
        String[] lettres = {"A", "B", "C", "D"};
        List<QuizQuestionIA> questions = new ArrayList<>();

        for (Object[] d : data) {
            String   questionTexte = (String)   d[0];
            String[] optionsTexte  = (String[]) d[1];
            String   bonneReponse  = (String)   d[2];
            String   explication   = (String)   d[3];

            QuizQuestionIA q = new QuizQuestionIA();
            q.setTexte(questionTexte);
            q.setExplication(explication);

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
