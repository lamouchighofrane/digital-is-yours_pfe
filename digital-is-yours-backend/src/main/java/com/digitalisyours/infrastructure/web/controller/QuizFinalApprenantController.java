package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.*;
import com.digitalisyours.domain.port.in.QuizFinalApprenantUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apprenant/formations/{formationId}/quiz-final")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class QuizFinalApprenantController {

    private final QuizFinalApprenantUseCase quizFinalApprenantUseCase;
    private final JwtUtil jwtUtil;

    // ══════════════════════════════════════════════════════════════
    // GET — Récupérer le quiz final (sans les bonnes réponses)
    // ══════════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<?> getInfosQuizFinal(
            @PathVariable Long formationId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            InfosQuizFinalApprenant infos =
                    quizFinalApprenantUseCase.getInfosQuizFinal(email, formationId);

            if (!infos.isExiste()) {
                return ResponseEntity.ok(Map.of("exists", false));
            }

            return ResponseEntity.ok(toInfosResponse(infos));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason()));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // POST — Soumettre les réponses et obtenir la correction
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/soumettre")
    public ResponseEntity<?> soumettre(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            Map<Long, Long> reponses = parseReponses(payload);
            int tempsPasse = payload.get("tempsPasse") != null
                    ? Integer.parseInt(payload.get("tempsPasse").toString()) : 0;

            // ── Parser le rapport de fraude ───────────────────────
            RapportFraude rapportFraude = parseRapportFraude(payload);

            SoumissionQuizFinal soumission = SoumissionQuizFinal.builder()
                    .email(email)
                    .formationId(formationId)
                    .reponses(reponses)
                    .tempsPasse(tempsPasse)
                    .rapportFraude(rapportFraude)
                    .build();

            ResultatQuizFinal resultat = quizFinalApprenantUseCase.soumettre(soumission);

            log.info("QuizFinal soumis : apprenant={} formation={} scoreFinal={}% reussi={} infractions={}",
                    email, formationId, resultat.getScore(), resultat.getReussi(),
                    resultat.getNbInfractions());

            return ResponseEntity.ok(toResultatResponse(resultat));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            boolean epuisees = e.getStatusCode().value() == 403 &&
                    e.getReason() != null && e.getReason().contains("épuisé");
            if (epuisees) {
                return ResponseEntity.status(403).body(Map.of(
                        "message", e.getReason(),
                        "tentativesEpuisees", true));
            }
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason()));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            boolean epuisees = e.getMessage() != null && e.getMessage().contains("épuisé");
            if (epuisees) {
                return ResponseEntity.status(403).body(Map.of(
                        "message", e.getMessage(),
                        "tentativesEpuisees", true));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SÉRIALISATION — InfosQuizFinalApprenant → Map JSON
    // ══════════════════════════════════════════════════════════════

    private Map<String, Object> toInfosResponse(InfosQuizFinalApprenant infos) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("exists",              true);
        map.put("quizId",              infos.getQuizId());
        map.put("notePassage",         infos.getNotePassage());
        map.put("nombreTentatives",    infos.getNombreTentatives());
        map.put("tentativesUtilisees", infos.getTentativesUtilisees());
        map.put("tentativesRestantes", infos.getTentativesRestantes());
        map.put("dureeMinutes",        infos.getDureeMinutes());
        map.put("nbQuestions",         infos.getNbQuestions());
        map.put("peutPasser",          infos.isPeutPasser());

        if (infos.getQuiz() != null && infos.getQuiz().getQuestions() != null) {
            List<Map<String, Object>> questions = infos.getQuiz().getQuestions().stream()
                    .map(this::toQuestionResponse)
                    .collect(Collectors.toList());
            map.put("questions", questions);
        }

        if (infos.getDernierResultat() != null) {
            InfosQuizFinalApprenant.DernierResultat dr = infos.getDernierResultat();
            Map<String, Object> dernierResultat = new LinkedHashMap<>();
            dernierResultat.put("score",               dr.getScore());
            dernierResultat.put("reussi",               dr.getReussi());
            dernierResultat.put("nombreBonnesReponses", dr.getNombreBonnesReponses());
            dernierResultat.put("nombreQuestions",      dr.getNombreQuestions());
            dernierResultat.put("datePassage",          dr.getDatePassage());
            dernierResultat.put("tentativeNumero",      dr.getTentativeNumero());
            map.put("dernierResultat", dernierResultat);
        }

        return map;
    }

    private Map<String, Object> toQuestionResponse(Question q) {
        Map<String, Object> qm = new LinkedHashMap<>();
        qm.put("id",    q.getId());
        qm.put("texte", q.getTexte());
        qm.put("ordre", q.getOrdre());

        if (q.getOptions() != null) {
            List<Map<String, Object>> options = q.getOptions().stream()
                    .map(opt -> {
                        Map<String, Object> om = new LinkedHashMap<>();
                        om.put("id",    opt.getId());
                        om.put("texte", opt.getTexte());
                        om.put("ordre", opt.getOrdre());
                        return om;
                    })
                    .collect(Collectors.toList());
            qm.put("options", options);
        }
        return qm;
    }

    // ══════════════════════════════════════════════════════════════
    // SÉRIALISATION — ResultatQuizFinal → Map JSON
    // ══════════════════════════════════════════════════════════════

    private Map<String, Object> toResultatResponse(ResultatQuizFinal resultat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("score",                resultat.getScore());
        map.put("scoreBrut",            resultat.getScoreBrut());
        map.put("penaliteAppliquee",    resultat.getPenaliteAppliquee());
        map.put("nombreBonnesReponses", resultat.getNombreBonnesReponses());
        map.put("nombreQuestions",      resultat.getNombreQuestions());
        map.put("reussi",               resultat.getReussi());
        map.put("notePassage",          resultat.getNotePassage());
        map.put("tempsPasse",           resultat.getTempsPasse());
        map.put("tentativeNumero",      resultat.getTentativeNumero());
        map.put("tentativesRestantes",  resultat.getTentativesRestantes());
        map.put("datePassage",          resultat.getDatePassage());
        map.put("nbInfractions",        resultat.getNbInfractions());
        map.put("suspectFraude",        resultat.getSuspectFraude());

        if (resultat.getReponses() != null) {
            List<Map<String, Object>> reponses = resultat.getReponses().stream()
                    .map(r -> {
                        Map<String, Object> rm = new LinkedHashMap<>();
                        rm.put("questionId",         r.getQuestionId());
                        rm.put("questionTexte",      r.getQuestionTexte());
                        rm.put("optionChoisieId",    r.getOptionChoisieId());
                        rm.put("optionChoisieTexte", r.getOptionChoisieTexte());
                        rm.put("estCorrecte",        r.getEstCorrecte());
                        rm.put("explication",        r.getExplication());
                        rm.put("bonneReponseTexte",  r.getBonneReponseTexte());
                        return rm;
                    })
                    .collect(Collectors.toList());
            map.put("reponses", reponses);
        }

        return map;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS — Parsing
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<Long, Long> parseReponses(Map<String, Object> payload) {
        Map<Long, Long> result = new HashMap<>();
        Object reponsesRaw = payload.get("reponses");
        if (reponsesRaw instanceof Map<?, ?> rawMap) {
            rawMap.forEach((k, v) -> {
                try {
                    result.put(Long.parseLong(k.toString()), Long.parseLong(v.toString()));
                } catch (NumberFormatException ignored) {}
            });
        }
        return result;
    }

    /**
     * Parse le rapport de fraude depuis le payload JSON.
     * Structure attendue :
     * {
     *   "rapportFraude": {
     *     "nombreInfractions": 2,
     *     "infractions": [
     *       { "type": "onglet_quitte", "message": "...", "horodatage": "..." }
     *     ]
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private RapportFraude parseRapportFraude(Map<String, Object> payload) {
        Object raw = payload.get("rapportFraude");
        if (raw == null) {
            log.warn("rapportFraude absent du payload");
            return null;
        }

        log.info("rapportFraude reçu : {}", raw);

        try {
            Map<String, Object> rfMap = (Map<String, Object>) raw;

            // ── Lire nombreInfractions de façon robuste ──────────────
            int nombreInfractions = 0;
            Object nbRaw = rfMap.get("nombreInfractions");
            if (nbRaw != null) {
                nombreInfractions = ((Number) nbRaw).intValue();
            }

            log.info("nombreInfractions parsé : {}", nombreInfractions);

            // ── Lire la liste des infractions ────────────────────────
            List<RapportFraude.Infraction> infractions = new ArrayList<>();
            Object infRaw = rfMap.get("infractions");

            if (infRaw instanceof List<?> infList) {
                for (Object item : infList) {
                    if (item instanceof Map<?, ?> infMap) {
                        infractions.add(RapportFraude.Infraction.builder()
                                .type(infMap.get("type") != null
                                        ? infMap.get("type").toString() : "")
                                .message(infMap.get("message") != null
                                        ? infMap.get("message").toString() : "")
                                .horodatage(infMap.get("horodatage") != null
                                        ? infMap.get("horodatage").toString() : "")
                                .build());
                    }
                }
            }

            log.info("infractions parsées : {}", infractions.size());

            return RapportFraude.builder()
                    .nombreInfractions(nombreInfractions)
                    .infractions(infractions)
                    .build();

        } catch (Exception e) {
            log.error("Erreur parsing rapportFraude : {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
    }

    private ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(403).body(Map.of("message", message));
    }
}