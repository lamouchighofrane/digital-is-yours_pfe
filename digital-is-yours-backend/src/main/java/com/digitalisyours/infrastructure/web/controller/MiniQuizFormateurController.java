package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.OptionQuestion;
import com.digitalisyours.domain.model.Question;
import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.port.in.QuizFormateurUseCase;

import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/formateur/formations/{formationId}/cours/{coursId}/mini-quiz")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class MiniQuizFormateurController {
    private final QuizFormateurUseCase quizUseCase;
    private final JwtUtil jwtUtil;

    // ══════════════════════════════════════════════════════
    // GET — Récupérer le quiz existant
    // ══════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<?> getMiniQuiz(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            Optional<Quiz> quizOpt = quizUseCase.getMiniQuiz(formationId, coursId, email);
            return quizOpt.isEmpty()
                    ? ResponseEntity.ok(Map.of("exists", false))
                    : ResponseEntity.ok(toResponse(quizOpt.get()));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // GET — Contexte du cours pour la génération
    // ══════════════════════════════════════════════════════

    @GetMapping("/contexte")
    public ResponseEntity<?> getContexte(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            return ResponseEntity.ok(quizUseCase.getContexte(formationId, coursId, email));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ══════════════════════════════════════════════════════
    // POST — Générer le quiz avec l'IA
    // ══════════════════════════════════════════════════════

    @PostMapping("/generer-ia")
    public ResponseEntity<?> generer(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            int     nbQ        = clamp(getInt(payload,   "nombreQuestions",  5), 3, 10);
            String  diff       = difficulte(getString(payload, "difficulte", "MOYEN"));
            boolean inclDef    = getBool(payload, "inclureDefinitions",  true);
            boolean inclPrat   = getBool(payload, "inclureCasPratiques", true);
            float   note       = getFloat(payload, "notePassage",        70f);
            int     tentatives = getInt(payload,  "nombreTentatives",    3);

            Quiz quiz = quizUseCase.genererQuizIA(
                    formationId, coursId, email,
                    nbQ, diff, inclDef, inclPrat, note, tentatives
            );

            log.info("Quiz IA généré pour le cours {} par {}", coursId, email);
            return ResponseEntity.ok(toResponse(quiz));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // PUT — Modifier texte + explication d'une question
    // ══════════════════════════════════════════════════════

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<?> updateQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String texte      = (String) payload.get("texte");
            String explication = payload.get("explication") != null
                    ? payload.get("explication").toString() : null;

            Quiz quiz = quizUseCase.updateQuestion(
                    formationId, coursId, questionId, email, texte, explication);
            return ResponseEntity.ok(toResponse(quiz));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // PATCH — Modifier le texte d'une option
    // ══════════════════════════════════════════════════════

    @PatchMapping("/questions/{questionId}/options/{optionId}")
    public ResponseEntity<?> updateOption(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            @PathVariable Long optionId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String texte = (String) payload.get("texte");
            quizUseCase.updateOption(formationId, coursId, questionId, optionId, email, texte);
            return ResponseEntity.ok(Map.of("success", true));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // PATCH — Définir la bonne réponse
    // ══════════════════════════════════════════════════════

    @PatchMapping("/questions/{questionId}/bonne-reponse/{optionId}")
    public ResponseEntity<?> setBonneReponse(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            @PathVariable Long optionId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            Quiz quiz = quizUseCase.setBonneReponse(
                    formationId, coursId, questionId, optionId, email);
            return ResponseEntity.ok(toResponse(quiz));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // POST — Ajouter une question manuelle
    // ══════════════════════════════════════════════════════

    @PostMapping("/questions")
    public ResponseEntity<?> addQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String texte       = (String) payload.get("texte");
            String explication = payload.get("explication") != null
                    ? payload.get("explication").toString() : null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> options =
                    (List<Map<String, Object>>) payload.get("options");

            Quiz quiz = quizUseCase.addQuestion(
                    formationId, coursId, email, texte, explication, options);
            return ResponseEntity.ok(toResponse(quiz));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // DELETE — Supprimer une question
    // ══════════════════════════════════════════════════════

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            quizUseCase.deleteQuestion(formationId, coursId, questionId, email);
            return ResponseEntity.ok(Map.of("success", true, "message", "Question supprimée."));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ══════════════════════════════════════════════════════
    // DELETE — Supprimer le quiz entier
    // ══════════════════════════════════════════════════════

    @DeleteMapping
    public ResponseEntity<?> deleteMiniQuiz(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            quizUseCase.deleteMiniQuiz(formationId, coursId, email);
            return ResponseEntity.ok(Map.of("success", true));

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ══════════════════════════════════════════════════════
    // SERIALISATION Quiz → Map (réponse JSON)
    // ══════════════════════════════════════════════════════

    private Map<String, Object> toResponse(Quiz quiz) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("exists",              true);
        map.put("id",                  quiz.getId());
        map.put("type",                quiz.getType());
        map.put("notePassage",         quiz.getNotePassage());
        map.put("nombreTentatives",    quiz.getNombreTentatives());
        map.put("genereParIA",         quiz.getGenereParIA());
        map.put("niveauDifficulte",    quiz.getNiveauDifficulte());
        map.put("difficulte",          quiz.getNiveauDifficulte());
        map.put("inclureDefinitions",  quiz.getInclureDefinitions());
        map.put("inclureCasPratiques", quiz.getInclureCasPratiques());
        map.put("dateCreation",        quiz.getDateCreation());

        int nb = quiz.getQuestions() != null ? quiz.getQuestions().size() : 0;
        map.put("nbQuestions",     nb);
        map.put("nombreQuestions", nb);

        if (quiz.getQuestions() != null) {
            List<Map<String, Object>> questions = quiz.getQuestions().stream()
                    .map(this::questionToMap)
                    .collect(Collectors.toList());
            map.put("questions", questions);
        }
        return map;
    }

    private Map<String, Object> questionToMap(Question q) {
        Map<String, Object> qm = new LinkedHashMap<>();
        qm.put("id",          q.getId());
        qm.put("texte",       q.getTexte());
        qm.put("explication", q.getExplication());
        qm.put("ordre",       q.getOrdre());
        qm.put("genereParIA", q.getGenereParIA());

        if (q.getOptions() != null) {
            qm.put("options", q.getOptions().stream()
                    .map(this::optionToMap)
                    .collect(Collectors.toList()));
        }
        return qm;
    }

    private Map<String, Object> optionToMap(OptionQuestion o) {
        Map<String, Object> om = new LinkedHashMap<>();
        om.put("id",          o.getId());
        om.put("texte",       o.getTexte());
        om.put("estCorrecte", o.getEstCorrecte());
        om.put("ordre",       o.getOrdre());
        return om;
    }

    // ══════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════

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

    private int     getInt   (Map<String,Object> m, String k, int    d) { Object v=m.get(k); return v==null?d:Integer.parseInt(v.toString()); }
    private float   getFloat (Map<String,Object> m, String k, float  d) { Object v=m.get(k); return v==null?d:Float.parseFloat(v.toString()); }
    private String  getString(Map<String,Object> m, String k, String d) { Object v=m.get(k); return v==null?d:v.toString(); }
    private boolean getBool  (Map<String,Object> m, String k, boolean d){ Object v=m.get(k); return v==null?d:Boolean.parseBoolean(v.toString()); }
    private int     clamp    (int v, int min, int max)                   { return Math.max(min, Math.min(max, v)); }
    private String  difficulte(String d) {
        return List.of("FACILE","MOYEN","DIFFICILE").contains(d.toUpperCase()) ? d.toUpperCase() : "MOYEN";
    }
}
