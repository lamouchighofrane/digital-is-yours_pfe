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

    // ══════════════════════════════════════════════════════
    // GET — Récupérer le quiz final (sans les bonnes réponses)
    // ══════════════════════════════════════════════════════

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

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // POST — Soumettre les réponses et obtenir la correction
    // ══════════════════════════════════════════════════════

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

            SoumissionQuizFinal soumission = SoumissionQuizFinal.builder()
                    .email(email)
                    .formationId(formationId)
                    .reponses(reponses)
                    .tempsPasse(tempsPasse)
                    .build();

            ResultatQuizFinal resultat = quizFinalApprenantUseCase.soumettre(soumission);

            log.info("QuizFinal soumis : apprenant={} formation={} score={}% reussi={}",
                    email, formationId, resultat.getScore(), resultat.getReussi());

            return ResponseEntity.ok(toResultatResponse(resultat));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            // Tentatives épuisées → 403 avec flag spécial pour le frontend
            boolean epuisees = e.getMessage() != null && e.getMessage().contains("épuisé");
            if (epuisees) {
                return ResponseEntity.status(403).body(Map.of(
                        "message", e.getMessage(),
                        "tentativesEpuisees", true));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // SÉRIALISATION — InfosQuizFinalApprenant → Map JSON
    // ══════════════════════════════════════════════════════

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

        // Quiz avec questions (estCorrecte déjà masqué par le service)
        if (infos.getQuiz() != null && infos.getQuiz().getQuestions() != null) {
            List<Map<String, Object>> questions = infos.getQuiz().getQuestions().stream()
                    .map(this::toQuestionResponse)
                    .collect(Collectors.toList());
            map.put("questions", questions);
        }

        // Dernier résultat éventuel
        if (infos.getDernierResultat() != null) {
            InfosQuizFinalApprenant.DernierResultat dr = infos.getDernierResultat();
            Map<String, Object> dernierResultat = new LinkedHashMap<>();
            dernierResultat.put("score",                dr.getScore());
            dernierResultat.put("reussi",               dr.getReussi());
            dernierResultat.put("nombreBonnesReponses",  dr.getNombreBonnesReponses());
            dernierResultat.put("nombreQuestions",       dr.getNombreQuestions());
            dernierResultat.put("datePassage",           dr.getDatePassage());
            dernierResultat.put("tentativeNumero",       dr.getTentativeNumero());
            map.put("dernierResultat", dernierResultat);
        }

        return map;
    }

    private Map<String, Object> toQuestionResponse(Question q) {
        Map<String, Object> qm = new LinkedHashMap<>();
        qm.put("id",    q.getId());
        qm.put("texte", q.getTexte());
        qm.put("ordre", q.getOrdre());
        // explication NON exposée avant soumission

        if (q.getOptions() != null) {
            List<Map<String, Object>> options = q.getOptions().stream()
                    .map(opt -> {
                        Map<String, Object> om = new LinkedHashMap<>();
                        om.put("id",    opt.getId());
                        om.put("texte", opt.getTexte());
                        om.put("ordre", opt.getOrdre());
                        // estCorrecte NON exposé avant soumission (déjà null via service)
                        return om;
                    })
                    .collect(Collectors.toList());
            qm.put("options", options);
        }
        return qm;
    }

    // ══════════════════════════════════════════════════════
    // SÉRIALISATION — ResultatQuizFinal → Map JSON
    // ══════════════════════════════════════════════════════

    private Map<String, Object> toResultatResponse(ResultatQuizFinal resultat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("score",                resultat.getScore());
        map.put("nombreBonnesReponses", resultat.getNombreBonnesReponses());
        map.put("nombreQuestions",      resultat.getNombreQuestions());
        map.put("reussi",               resultat.getReussi());
        map.put("notePassage",          resultat.getNotePassage());
        map.put("tempsPasse",           resultat.getTempsPasse());
        map.put("tentativeNumero",      resultat.getTentativeNumero());
        map.put("tentativesRestantes",  resultat.getTentativesRestantes());
        map.put("datePassage",          resultat.getDatePassage());

        if (resultat.getReponses() != null) {
            List<Map<String, Object>> reponses = resultat.getReponses().stream()
                    .map(r -> {
                        Map<String, Object> rm = new LinkedHashMap<>();
                        rm.put("questionId",        r.getQuestionId());
                        rm.put("questionTexte",     r.getQuestionTexte());
                        rm.put("optionChoisieId",   r.getOptionChoisieId());
                        rm.put("optionChoisieTexte",r.getOptionChoisieTexte());
                        rm.put("estCorrecte",       r.getEstCorrecte());
                        rm.put("explication",       r.getExplication());
                        rm.put("bonneReponseTexte", r.getBonneReponseTexte());
                        return rm;
                    })
                    .collect(Collectors.toList());
            map.put("reponses", reponses);
        }

        return map;
    }

    // ══════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════

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