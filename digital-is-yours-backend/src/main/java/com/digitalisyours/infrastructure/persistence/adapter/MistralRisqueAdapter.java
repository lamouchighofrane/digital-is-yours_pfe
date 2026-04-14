package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.AnalyseRisque;
import com.digitalisyours.domain.port.out.DeepSeekRisqueAnalysePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class MistralRisqueAdapter implements DeepSeekRisqueAnalysePort {

    @Value("${mistral.api.key:}")
    private String mistralApiKey;

    private static final String MISTRAL_URL   = "https://api.mistral.ai/v1/chat/completions";
    private static final String MISTRAL_MODEL = "mistral-small-latest";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @PostConstruct
    public void init() {
        if (mistralApiKey == null || mistralApiKey.isBlank()) {
            log.warn("═══ MistralRisqueAdapter : CLÉ ABSENTE → fallback local ═══");
        } else {
            log.info("═══ MistralRisqueAdapter ACTIF — clé: {}... ═══",
                    mistralApiKey.substring(0, Math.min(8, mistralApiKey.length())));
        }
    }

    // ═══════════════════════════════════════════════════════
    // POINT D'ENTRÉE PRINCIPAL
    // ═══════════════════════════════════════════════════════

    @Override
    public AnalyseRisque analyserRisque(AnalyseRisque ctx) {

        float  scoreLocal  = calculerScoreLocal(ctx);
        String niveauLocal = scoreLocal >= 60 ? "ELEVE"
                : scoreLocal >= 30 ? "MOYEN" : "FAIBLE";

        log.info("┌─ Analyse risque ──────────────────────────────────");
        log.info("│ Formation   : {}", ctx.getFormationTitre());
        log.info("│ Progression : {}%",
                ctx.getProgression() != null ? (int)(float)ctx.getProgression() : 0);
        log.info("│ Inactivité  : {} jours", ctx.getJoursInactivite());
        log.info("│ Quiz        : {}% ({} passés)",
                ctx.getScoreMoyenQuiz() != null
                        ? (int)(float)ctx.getScoreMoyenQuiz() : 0,
                ctx.getNbQuizPasses() != null ? ctx.getNbQuizPasses() : 0);
        log.info("│ Vidéos      : {}", ctx.getNbVideosVues());
        log.info("│ Score local : {} → {}", (int) scoreLocal, niveauLocal);
        log.info("└───────────────────────────────────────────────────");

        if (mistralApiKey == null || mistralApiKey.isBlank()) {
            log.warn("Clé Mistral absente → fallback local");
            return analyseLocaleComplete(ctx, scoreLocal, niveauLocal);
        }

        try {
            String prompt = buildPrompt(ctx, niveauLocal, scoreLocal);
            String body   = objectMapper.writeValueAsString(Map.of(
                    "model",       MISTRAL_MODEL,
                    "messages",    List.of(
                            Map.of("role", "system", "content", buildSystemPrompt()),
                            Map.of("role", "user",   "content", prompt)
                    ),
                    "temperature", 0.4,
                    "max_tokens",  300
            ));

            log.info("→ Appel Mistral API...");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MISTRAL_URL))
                    .header("Content-Type",  "application/json")
                    .header("Authorization", "Bearer " + mistralApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());

            log.info("← Mistral HTTP : {}", resp.statusCode());

            if (resp.statusCode() == 429) {
                log.warn("Quota Mistral dépassé → fallback local");
                return analyseLocaleComplete(ctx, scoreLocal, niveauLocal);
            }
            if (resp.statusCode() == 401) {
                log.error("Clé Mistral invalide (401) → fallback local");
                return analyseLocaleComplete(ctx, scoreLocal, niveauLocal);
            }
            if (resp.statusCode() != 200) {
                log.error("Mistral HTTP {} → fallback : {}",
                        resp.statusCode(), resp.body());
                return analyseLocaleComplete(ctx, scoreLocal, niveauLocal);
            }

            log.info("✓ Mistral succès");
            return parseMistralResponse(resp.body(), ctx, scoreLocal, niveauLocal);

        } catch (Exception e) {
            log.error("Erreur Mistral : {} → fallback local", e.getMessage());
            return analyseLocaleComplete(ctx, scoreLocal, niveauLocal);
        }
    }

    // ═══════════════════════════════════════════════════════
    // CALCUL DU SCORE LOCAL
    //
    // Critères (vidéos supprimé — déjà inclus dans progression) :
    //
    // INACTIVITÉ (respecte les disponibilités déclarées) :
    //   < 7 jours   →  0 pts  normal
    //   7-14 jours  → +8 pts  légère alerte
    //   15-21 jours → +20 pts préoccupant
    //   > 21 jours  → +35 pts abandon probable
    //
    // PROGRESSION :
    //   < 10%  → +25 pts
    //   10-29% → +15 pts
    //   30-59% →  +5 pts
    //   ≥ 60%  →   0 pts
    //
    // QUIZ :
    //   < 40%  → +25 pts
    //   40-59% → +12 pts
    //   ≥ 60%  →   0 pts
    //   absent →   0 pts (60% par défaut = neutre)
    //
    // TOTAL MAX = 85 pts
    // ═══════════════════════════════════════════════════════

    private float calculerScoreLocal(AnalyseRisque ctx) {
        int   jours = ctx.getJoursInactivite() != null ? ctx.getJoursInactivite() : 0;
        float prog  = ctx.getProgression()     != null ? ctx.getProgression()     : 0f;
        float quiz  = ctx.getScoreMoyenQuiz()  != null ? ctx.getScoreMoyenQuiz()  : 60f;

        int ptsInactivite  = 0;
        int ptsProgression = 0;
        int ptsQuiz        = 0;

        // ── Inactivité ────────────────────────────────────────────
        if      (jours > 21) ptsInactivite = 35;
        else if (jours > 14) ptsInactivite = 20;
        else if (jours >= 7) ptsInactivite = 8;

        // ── Progression ───────────────────────────────────────────
        if      (prog < 10)  ptsProgression = 25;
        else if (prog < 30)  ptsProgression = 15;
        else if (prog < 60)  ptsProgression = 5;

        // ── Score quiz ────────────────────────────────────────────
        if      (quiz < 40)  ptsQuiz = 25;
        else if (quiz < 60)  ptsQuiz = 12;

        float total = Math.min(ptsInactivite + ptsProgression + ptsQuiz, 85);

        log.debug("┌─ Score détaillé ───────────────────────────────");
        log.debug("│ Inactivité  {}j  → +{} pts", jours,      ptsInactivite);
        log.debug("│ Progression {}%  → +{} pts", (int) prog, ptsProgression);
        log.debug("│ Quiz        {}%  → +{} pts", (int) quiz, ptsQuiz);
        log.debug("│ TOTAL             = {} / 85 pts",         (int) total);
        log.debug("└────────────────────────────────────────────────");

        return total;
    }

    // ═══════════════════════════════════════════════════════
    // SYSTEM PROMPT
    // ═══════════════════════════════════════════════════════

    private String buildSystemPrompt() {
        return """
                Tu es un coach pédagogique expert en e-learning.
                Ton rôle est d'analyser les données d'apprentissage d'un apprenant
                et de générer un diagnostic précis et une recommandation motivante.
                
                Règles strictes :
                - Réponds UNIQUEMENT en JSON valide
                - Pas de markdown, pas de texte avant ou après le JSON
                - L'explication doit cibler la VRAIE cause principale (pas générique)
                - La recommandation doit être concrète, actionnable et encourageante
                - Utilise le prénom pour personnaliser
                - Langue : français uniquement
                """;
    }

    // ═══════════════════════════════════════════════════════
    // PROMPT UTILISATEUR
    // ═══════════════════════════════════════════════════════

    private String buildPrompt(AnalyseRisque ctx,
                               String niveau, float score) {

        int   jours  = ctx.getJoursInactivite()     != null ? ctx.getJoursInactivite()     : 0;
        float prog   = ctx.getProgression()          != null ? ctx.getProgression()          : 0f;
        float quiz   = ctx.getScoreMoyenQuiz()       != null ? ctx.getScoreMoyenQuiz()       : 60f;
        int   videos = ctx.getNbVideosVues()         != null ? ctx.getNbVideosVues()         : 0;
        int   nbQuiz = ctx.getNbQuizPasses()         != null ? ctx.getNbQuizPasses()         : 0;
        int   nbDocs = ctx.getNbDocumentsOuverts()   != null ? ctx.getNbDocumentsOuverts()   : 0;
        String prenom = ctx.getApprenantPrenom()     != null ? ctx.getApprenantPrenom()      : "l'apprenant";
        String titre  = ctx.getFormationTitre()      != null ? ctx.getFormationTitre()       : "la formation";

        String facteurPrincipal = identifierFacteurDominant(
                jours, prog, quiz, nbQuiz, videos);

        String contexteRisque = switch (niveau) {
            case "ELEVE"  -> "L'apprenant est en danger d'abandon imminent.";
            case "MOYEN"  -> "L'apprenant montre des signes d'essoufflement.";
            default       -> "L'apprenant progresse mais peut faire mieux.";
        };

        return String.format("""
                === PROFIL APPRENANT ===
                Prénom        : %s
                Formation     : %s
                Niveau risque : %s (score %d/85)
                Contexte      : %s
                
                === DONNÉES D'APPRENTISSAGE ===
                📊 Progression globale : %.1f%%
                ⏰ Inactivité          : %d jours sans activité
                📝 Quiz                : %d passé(s) — score moyen %.0f%%
                🎬 Vidéos vues         : %d
                📄 Documents ouverts   : %d
                
                === FACTEUR DOMINANT ===
                %s
                
                === CONSIGNE ===
                Génère un JSON avec exactement ces 2 champs :
                {
                  "explication": "<diagnostic précis en 1 phrase — cite les chiffres clés>",
                  "recommandation": "<conseil concret et motivant en 1-2 phrases — utilise le prénom>"
                }
                """,
                prenom, titre, niveau, (int) score,
                contexteRisque,
                prog, jours,
                nbQuiz, quiz,
                videos, nbDocs,
                facteurPrincipal
        );
    }

    // ═══════════════════════════════════════════════════════
    // IDENTIFICATION DU FACTEUR DOMINANT
    // ═══════════════════════════════════════════════════════

    private String identifierFacteurDominant(int jours, float prog,
                                             float quiz, int nbQuiz,
                                             int videos) {
        if (jours > 21) {
            return String.format(
                    "INACTIVITÉ CRITIQUE : %d jours sans aucune activité — " +
                            "risque d'abandon très élevé.", jours);
        }
        if (quiz < 40 && nbQuiz > 0) {
            return String.format(
                    "SCORE QUIZ TRÈS BAS : %.0f%% sur %d quiz passé(s) — " +
                            "difficultés de compréhension importantes.", quiz, nbQuiz);
        }
        if (prog < 10 && jours > 14) {
            return String.format(
                    "DOUBLE PROBLÈME : progression quasi nulle (%.0f%%) " +
                            "ET %d jours d'inactivité.", prog, jours);
        }
        if (videos == 0 && nbQuiz == 0) {
            return "AUCUN CONTENU CONSOMMÉ : ni vidéo vue, ni quiz passé — " +
                    "l'apprenant n'a pas encore commencé à apprendre.";
        }
        if (jours > 14) {
            return String.format(
                    "INACTIVITÉ PROLONGÉE : %d jours sans se connecter " +
                            "à la formation.", jours);
        }
        if (prog < 20) {
            return String.format(
                    "PROGRESSION FAIBLE : seulement %.0f%% du contenu " +
                            "complété — rythme insuffisant.", prog);
        }
        if (jours >= 7) {
            return String.format(
                    "INACTIVITÉ MODÉRÉE : %d jours sans activité — " +
                            "une semaine sans apprendre.", jours);
        }
        if (quiz < 60 && nbQuiz > 0) {
            return String.format(
                    "SCORE QUIZ MOYEN : %.0f%% — quelques difficultés " +
                            "de compréhension à adresser.", quiz);
        }
        return String.format(
                "ENGAGEMENT INSUFFISANT : progression %.0f%% " +
                        "avec un rythme irrégulier.", prog);
    }

    // ═══════════════════════════════════════════════════════
    // PARSING RÉPONSE MISTRAL
    // ═══════════════════════════════════════════════════════

    private AnalyseRisque parseMistralResponse(String body, AnalyseRisque ctx,
                                               float score, String niveau) {
        try {
            JsonNode root    = objectMapper.readTree(body);
            String   content = root.path("choices").get(0)
                    .path("message").path("content")
                    .asText("").trim();

            content = content.replaceAll("```json|```", "").trim();
            int start = content.indexOf('{');
            int end   = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            }

            JsonNode json        = objectMapper.readTree(content);
            String   explication = json.path("explication").asText("").trim();
            String   reco        = json.path("recommandation").asText("").trim();

            if (explication.isEmpty() || reco.isEmpty()) {
                log.warn("Mistral champs vides → fallback");
                return analyseLocaleComplete(ctx, score, niveau);
            }

            log.info("Explication    : {}", explication);
            log.info("Recommandation : {}", reco);

            ctx.setNiveauRisque(niveau);
            ctx.setScoreRisque(score);
            ctx.setExplication(explication);
            ctx.setRecommandationIA(reco);
            return ctx;

        } catch (Exception e) {
            log.warn("Parsing Mistral échoué : {} → fallback", e.getMessage());
            return analyseLocaleComplete(ctx, score, niveau);
        }
    }

    // ═══════════════════════════════════════════════════════
    // FALLBACK LOCAL — si Mistral indisponible
    // ═══════════════════════════════════════════════════════

    private AnalyseRisque analyseLocaleComplete(AnalyseRisque ctx,
                                                float score, String niveau) {
        int   jours  = ctx.getJoursInactivite()  != null ? ctx.getJoursInactivite()  : 0;
        float prog   = ctx.getProgression()       != null ? ctx.getProgression()       : 0f;
        float quiz   = ctx.getScoreMoyenQuiz()    != null ? ctx.getScoreMoyenQuiz()    : 60f;
        int   nbQuiz = ctx.getNbQuizPasses()      != null ? ctx.getNbQuizPasses()      : 0;
        int   videos = ctx.getNbVideosVues()      != null ? ctx.getNbVideosVues()      : 0;
        String prenom = ctx.getApprenantPrenom()  != null ? ctx.getApprenantPrenom()   : "";
        String titre  = ctx.getFormationTitre()   != null ? ctx.getFormationTitre()    : "votre formation";
        String ap     = prenom.isBlank() ? "" : prenom + ", ";

        // ── Explication selon facteur dominant ────────────────────
        String explication;
        if (jours > 21) {
            explication = String.format(
                    "Aucune activité depuis %d jours sur \"%s\" — " +
                            "risque d'abandon très élevé.", jours, titre);
        } else if (quiz < 40 && nbQuiz > 0) {
            explication = String.format(
                    "Score aux quiz très bas (%.0f%%) malgré %d quiz passé(s) — " +
                            "difficultés de compréhension détectées.", quiz, nbQuiz);
        } else if (prog < 10 && jours > 14) {
            explication = String.format(
                    "Progression quasi nulle (%.0f%%) combinée à " +
                            "%d jours d'inactivité.", prog, jours);
        } else if (videos == 0 && nbQuiz == 0) {
            explication = String.format(
                    "Aucun contenu consommé dans \"%s\" — " +
                            "ni vidéo vue ni quiz passé.", titre);
        } else if (jours > 14) {
            explication = String.format(
                    "%d jours sans activité sur \"%s\" — " +
                            "rythme d'apprentissage interrompu.", jours, titre);
        } else if (prog < 20) {
            explication = String.format(
                    "Progression faible (%.0f%%) — " +
                            "rythme d'apprentissage insuffisant.", prog);
        } else if (jours >= 7) {
            explication = String.format(
                    "Une semaine sans activité (%d jours) sur \"%s\" — " +
                            "la régularité fait défaut.", jours, titre);
        } else {
            explication = String.format(
                    "Engagement légèrement insuffisant avec " +
                            "%.0f%% de progression dans \"%s\".", prog, titre);
        }

        // ── Recommandation selon niveau et cause ──────────────────
        String recommandation;
        if ("ELEVE".equals(niveau)) {
            if (jours > 21) {
                recommandation = String.format(
                        "🚨 %scela fait %d jours sans activité dans \"%s\". " +
                                "Revenez dès aujourd'hui — même 10 minutes " +
                                "suffit pour reprendre le rythme !",
                        ap, jours, titre);
            } else if (quiz < 40 && nbQuiz > 0) {
                recommandation = String.format(
                        "🚨 %sun score de %.0f%% aux quiz signale des difficultés. " +
                                "Relisez les points clés et n'hésitez pas à revoir " +
                                "les vidéos — vous pouvez y arriver !",
                        ap, quiz);
            } else {
                recommandation = String.format(
                        "🚨 %svotre formation \"%s\" nécessite votre attention urgente. " +
                                "Consacrez 20 minutes aujourd'hui pour reprendre !",
                        ap, titre);
            }
        } else if ("MOYEN".equals(niveau)) {
            if (quiz < 40 && nbQuiz > 0) {
                recommandation = String.format(
                        "💪 %sun score de %.0f%% aux quiz peut être amélioré. " +
                                "Relisez les cours et retentez — " +
                                "vous avez les capacités pour réussir !",
                        ap, quiz);
            } else if (jours >= 7) {
                recommandation = String.format(
                        "💪 %svous êtes à %.0f%% dans \"%s\". " +
                                "Quelques sessions régulières et vous retrouverez " +
                                "votre rythme !",
                        ap, prog, titre);
            } else {
                recommandation = String.format(
                        "💪 %svous avez déjà %.0f%% de progression dans \"%s\". " +
                                "Un peu de régularité et le certificat " +
                                "est à votre portée !",
                        ap, prog, titre);
            }
        } else {
            recommandation = String.format(
                    "✅ %sbonne progression dans \"%s\" ! " +
                            "Continuez sur cette lancée — " +
                            "votre certificat vous attend !",
                    ap, titre);
        }

        ctx.setNiveauRisque(niveau);
        ctx.setScoreRisque(score);
        ctx.setExplication(explication);
        ctx.setRecommandationIA(recommandation);
        return ctx;
    }
}