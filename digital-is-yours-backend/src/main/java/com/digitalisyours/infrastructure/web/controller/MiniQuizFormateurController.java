package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.ia.CoursInfoIA;
import com.digitalisyours.infrastructure.ia.IAQuizService;
import com.digitalisyours.infrastructure.ia.OptionIA;
import com.digitalisyours.infrastructure.ia.QuizQuestionIA;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.QuizJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final IAQuizService iaQuizService;
    private final QuizJpaRepository quizRepository;
    private final CoursJpaRepository coursRepository;
    private final FormationJpaRepository formationRepository;
    private final UserJpaRepository userRepository;
    private final JwtUtil jwtUtil;

    // ══════════════════════════════════════════════════════
    // GET — Récupérer le quiz existant
    // ══════════════════════════════════════════════════════
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMiniQuiz(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null)
            return unauthorized();

        Optional<QuizEntity> quizOpt = quizRepository.findByCoursIdWithQuestions(coursId);
        return quizOpt.isEmpty()
                ? ResponseEntity.ok(Map.of("exists", false))
                : ResponseEntity.ok(toResponse(quizOpt.get()));
    }

    // ══════════════════════════════════════════════════════
    // GET — Contexte du cours pour la génération
    // ══════════════════════════════════════════════════════
    @GetMapping("/contexte")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getContexte(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null)
            return unauthorized();

        CoursEntity cours = getCours(coursId, formationId);
        if (cours == null) return ResponseEntity.notFound().build();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("coursId",          cours.getId());
        ctx.put("coursTitre",       cours.getTitre());
        ctx.put("coursDescription", cours.getDescription());
        ctx.put("coursObjectifs",   cours.getObjectifs());
        ctx.put("videoType",        cours.getVideoType());
        ctx.put("videoUrl",         cours.getVideoUrl());
        ctx.put("hasVideo",         cours.getVideoType() != null);
        ctx.put("quizExistant",     quizRepository.existsByCoursId(coursId));
        return ResponseEntity.ok(ctx);
    }

    // ══════════════════════════════════════════════════════
    // POST — Générer le quiz avec l'IA
    // ══════════════════════════════════════════════════════
    @PostMapping("/generer-ia")
    @Transactional
    public ResponseEntity<?> generer(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null)
            return unauthorized();

        CoursEntity cours = getCours(coursId, formationId);
        if (cours == null) return ResponseEntity.notFound().build();

        int     nbQ       = clamp(getInt(payload, "nombreQuestions", 5), 3, 10);
        String  diff      = difficulte(getString(payload, "difficulte", "MOYEN"));
        boolean inclDef   = getBool(payload, "inclureDefinitions",  true);
        boolean inclPrat  = getBool(payload, "inclureCasPratiques", true);
        float   note      = getFloat(payload, "notePassage",        70f);
        int     tentatives= getInt(payload,  "nombreTentatives",    3);

        // Supprimer le quiz existant si présent
        if (quizRepository.existsByCoursId(coursId)) {
            quizRepository.deleteByCoursId(coursId);
            quizRepository.flush();
        }

        CoursInfoIA info = new CoursInfoIA(cours.getTitre(), cours.getDescription(), cours.getObjectifs());
        List<QuizQuestionIA> questionsIA = iaQuizService.genererQuiz(
                info, cours.getId(), cours.getVideoUrl(), cours.getVideoType(),
                nbQ, diff, inclDef, inclPrat);

        if (questionsIA == null || questionsIA.isEmpty())
            return ResponseEntity.status(500).body(Map.of("message", "Échec de la génération IA."));

        QuizEntity quiz = QuizEntity.builder()
                .type("MiniQuiz").notePassage(note).nombreTentatives(tentatives)
                .genereParIA(true).niveauDifficulte(diff)
                .inclureDefinitions(inclDef).inclureCasPratiques(inclPrat)
                .dateCreation(LocalDateTime.now()).cours(cours).questions(new ArrayList<>())
                .build();
        quizRepository.save(quiz);

        for (int i = 0; i < questionsIA.size(); i++) {
            QuizQuestionIA qIA = questionsIA.get(i);
            QuestionEntity question = QuestionEntity.builder()
                    .texte(qIA.getTexte()).explication(qIA.getExplication())
                    .ordre(i + 1).genereParIA(true).quiz(quiz).options(new ArrayList<>())
                    .build();
            if (qIA.getOptions() != null) {
                for (OptionIA o : qIA.getOptions()) {
                    question.getOptions().add(OptionQuestionEntity.builder()
                            .texte(o.getTexte()).estCorrecte(o.isEstCorrecte())
                            .ordre(o.getOrdre()).question(question).build());
                }
            }
            quiz.getQuestions().add(question);
        }
        quizRepository.save(quiz);
        log.info("Quiz IA généré : {} questions pour le cours '{}'", questionsIA.size(), cours.getTitre());
        return ResponseEntity.ok(toResponse(quiz));
    }

    // ══════════════════════════════════════════════════════
    // PUT — Modifier texte + explication d'une question
    // ══════════════════════════════════════════════════════
    @PutMapping("/questions/{questionId}")
    @Transactional
    public ResponseEntity<?> updateQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null) return unauthorized();

        QuizEntity quiz = findQuiz(coursId);
        if (quiz == null) return ResponseEntity.notFound().build();

        QuestionEntity q = findQuestion(quiz, questionId);
        if (q == null) return ResponseEntity.notFound().build();

        String texte = (String) payload.get("texte");
        if (texte == null || texte.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Le texte de la question est obligatoire."));

        q.setTexte(texte.trim());
        if (payload.containsKey("explication")) {
            Object expl = payload.get("explication");
            q.setExplication(expl != null ? expl.toString().trim() : null);
        }
        quizRepository.save(quiz);
        return ResponseEntity.ok(toResponse(quiz));
    }

    // ══════════════════════════════════════════════════════
    // PATCH — Modifier le texte d'une option
    // ══════════════════════════════════════════════════════
    @PatchMapping("/questions/{questionId}/options/{optionId}")
    @Transactional
    public ResponseEntity<?> updateOption(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            @PathVariable Long optionId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null) return unauthorized();

        QuizEntity quiz = findQuiz(coursId);
        if (quiz == null) return ResponseEntity.notFound().build();

        QuestionEntity q = findQuestion(quiz, questionId);
        if (q == null) return ResponseEntity.notFound().build();

        OptionQuestionEntity opt = q.getOptions().stream()
                .filter(o -> o.getId().equals(optionId)).findFirst().orElse(null);
        if (opt == null) return ResponseEntity.notFound().build();

        String texte = (String) payload.get("texte");
        if (texte == null || texte.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Le texte de l'option est obligatoire."));

        opt.setTexte(texte.trim());
        quizRepository.save(quiz);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ══════════════════════════════════════════════════════
    // PATCH — Définir la bonne réponse d'une question
    // ══════════════════════════════════════════════════════
    @PatchMapping("/questions/{questionId}/bonne-reponse/{optionId}")
    @Transactional
    public ResponseEntity<?> setBonneReponse(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            @PathVariable Long optionId,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null) return unauthorized();

        QuizEntity quiz = findQuiz(coursId);
        if (quiz == null) return ResponseEntity.notFound().build();

        QuestionEntity q = findQuestion(quiz, questionId);
        if (q == null) return ResponseEntity.notFound().build();

        boolean optExiste = q.getOptions().stream().anyMatch(o -> o.getId().equals(optionId));
        if (!optExiste)
            return ResponseEntity.badRequest().body(Map.of("message", "Option introuvable pour cette question."));

        // Désactiver toutes, activer uniquement celle choisie
        q.getOptions().forEach(o -> o.setEstCorrecte(o.getId().equals(optionId)));
        quizRepository.save(quiz);
        return ResponseEntity.ok(toResponse(quiz));
    }

    // ══════════════════════════════════════════════════════
    // POST — Ajouter une question manuelle
    // ══════════════════════════════════════════════════════
    @PostMapping("/questions")
    @Transactional
    public ResponseEntity<?> addQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null) return unauthorized();

        QuizEntity quiz = findQuiz(coursId);
        if (quiz == null)
            return ResponseEntity.badRequest().body(Map.of("message", "Aucun quiz trouvé. Générez d'abord un quiz avec l'IA."));

        String texte = (String) payload.get("texte");
        if (texte == null || texte.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Le texte de la question est obligatoire."));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optionsPayload = (List<Map<String, Object>>) payload.get("options");
        if (optionsPayload == null || optionsPayload.size() < 2)
            return ResponseEntity.badRequest().body(Map.of("message", "Au moins 2 options sont requises."));

        long nbCorrects = optionsPayload.stream()
                .filter(o -> Boolean.TRUE.equals(o.get("estCorrecte"))).count();
        if (nbCorrects != 1)
            return ResponseEntity.badRequest().body(Map.of("message", "Exactement 1 bonne réponse est requise."));

        for (Map<String, Object> o : optionsPayload) {
            String ot = (String) o.get("texte");
            if (ot == null || ot.isBlank())
                return ResponseEntity.badRequest().body(Map.of("message", "Toutes les options doivent avoir un texte."));
        }

        int nextOrdre = quiz.getQuestions().stream()
                .mapToInt(q -> q.getOrdre() != null ? q.getOrdre() : 0).max().orElse(0) + 1;

        QuestionEntity newQ = QuestionEntity.builder()
                .texte(texte.trim())
                .explication(payload.get("explication") != null ? payload.get("explication").toString().trim() : null)
                .ordre(nextOrdre).genereParIA(false).quiz(quiz).options(new ArrayList<>())
                .build();

        String[] letters = {"A","B","C","D"};
        for (int i = 0; i < optionsPayload.size(); i++) {
            Map<String, Object> om = optionsPayload.get(i);
            String ordre = om.get("ordre") != null ? om.get("ordre").toString()
                    : (i < letters.length ? letters[i] : String.valueOf((char)('A'+i)));
            newQ.getOptions().add(OptionQuestionEntity.builder()
                    .texte(om.get("texte").toString().trim())
                    .estCorrecte(Boolean.TRUE.equals(om.get("estCorrecte")))
                    .ordre(ordre).question(newQ).build());
        }
        quiz.getQuestions().add(newQ);
        quizRepository.save(quiz);

        // Recharger pour avoir les IDs
        return quizRepository.findByCoursIdWithQuestions(coursId)
                .map(q -> ResponseEntity.ok(toResponse(q)))
                .orElse(ResponseEntity.ok(toResponse(quiz)));
    }

    // ══════════════════════════════════════════════════════
    // DELETE — Supprimer une question
    // ══════════════════════════════════════════════════════
    @DeleteMapping("/questions/{questionId}")
    @Transactional
    public ResponseEntity<?> deleteQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null) return unauthorized();

        QuizEntity quiz = findQuiz(coursId);
        if (quiz == null) return ResponseEntity.notFound().build();

        boolean removed = quiz.getQuestions().removeIf(q -> q.getId().equals(questionId));
        if (!removed) return ResponseEntity.notFound().build();

        // Réordonner
        for (int i = 0; i < quiz.getQuestions().size(); i++)
            quiz.getQuestions().get(i).setOrdre(i + 1);

        quizRepository.save(quiz);
        return ResponseEntity.ok(Map.of("success", true, "message", "Question supprimée."));
    }

    // ══════════════════════════════════════════════════════
    // DELETE — Supprimer le quiz entier
    // ══════════════════════════════════════════════════════
    @DeleteMapping
    @Transactional
    public ResponseEntity<?> deleteMiniQuiz(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        if (getFormateur(request, formationId) == null) return unauthorized();
        if (!quizRepository.existsByCoursId(coursId)) return ResponseEntity.notFound().build();
        quizRepository.deleteByCoursId(coursId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ══════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════

    private UserEntity getFormateur(HttpServletRequest req, Long formationId) {
        try {
            String auth = req.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            if (!jwtUtil.isValid(token)) return null;
            String email = jwtUtil.extractEmail(token);
            UserEntity user = userRepository.findByEmail(email).orElse(null);
            if (user == null || user.getRole() != Role.FORMATEUR) return null;
            FormationEntity formation = formationRepository.findById(formationId).orElse(null);
            if (formation == null) return null;
            if (!formation.getFormateur().getId().equals(user.getId())) return null;
            return user;
        } catch (Exception e) {
            log.warn("Auth error: {}", e.getMessage());
            return null;
        }
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(403).body(Map.of("message", "Accès non autorisé."));
    }

    private CoursEntity getCours(Long coursId, Long formationId) {
        return coursRepository.findById(coursId)
                .filter(c -> c.getFormation().getId().equals(formationId))
                .orElse(null);
    }

    private QuizEntity findQuiz(Long coursId) {
        return quizRepository.findByCoursIdWithQuestions(coursId).orElse(null);
    }

    private QuestionEntity findQuestion(QuizEntity quiz, Long questionId) {
        return quiz.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId)).findFirst().orElse(null);
    }

    private Map<String, Object> toResponse(QuizEntity quiz) {
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
                    .sorted(Comparator.comparing(q -> q.getOrdre() != null ? q.getOrdre() : 0))
                    .map(q -> {
                        Map<String, Object> qm = new LinkedHashMap<>();
                        qm.put("id",          q.getId());
                        qm.put("texte",       q.getTexte());
                        qm.put("explication", q.getExplication());
                        qm.put("ordre",       q.getOrdre());
                        qm.put("genereParIA", q.getGenereParIA());
                        if (q.getOptions() != null) {
                            qm.put("options", q.getOptions().stream()
                                    .sorted(Comparator.comparing(o -> o.getOrdre() != null ? o.getOrdre() : "Z"))
                                    .map(o -> {
                                        Map<String, Object> om = new LinkedHashMap<>();
                                        om.put("id",          o.getId());
                                        om.put("texte",       o.getTexte());
                                        om.put("estCorrecte", o.getEstCorrecte());
                                        om.put("ordre",       o.getOrdre());
                                        return om;
                                    }).collect(Collectors.toList()));
                        }
                        return qm;
                    }).collect(Collectors.toList());
            map.put("questions", questions);
        }
        return map;
    }

    private int    getInt   (Map<String,Object> m, String k, int    d) { Object v=m.get(k); return v==null?d:Integer.parseInt(v.toString()); }
    private float  getFloat (Map<String,Object> m, String k, float  d) { Object v=m.get(k); return v==null?d:Float.parseFloat(v.toString()); }
    private String getString(Map<String,Object> m, String k, String d) { Object v=m.get(k); return v==null?d:v.toString(); }
    private boolean getBool (Map<String,Object> m, String k, boolean d){ Object v=m.get(k); return v==null?d:Boolean.parseBoolean(v.toString()); }
    private int    clamp    (int v, int min, int max)                   { return Math.max(min, Math.min(max, v)); }
    private String difficulte(String d) {
        return List.of("FACILE","MOYEN","DIFFICILE").contains(d.toUpperCase()) ? d.toUpperCase() : "MOYEN";
    }
}
