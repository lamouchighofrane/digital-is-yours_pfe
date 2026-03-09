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

    // ══════════════════════════════════════════════════════════
    // GÉNÉRER UN MINI-QUIZ IA
    // ══════════════════════════════════════════════════════════

    /**
     * POST /api/formateur/formations/{formationId}/cours/{coursId}/mini-quiz/generer-ia
     *
     * Génère un quiz IA basé sur :
     * - Le titre/description/objectifs du cours
     * - Le contenu des PDFs associés au cours
     * - La transcription YouTube si une vidéo est associée
     */
    @PostMapping("/generer-ia")
    @Transactional
    public ResponseEntity<?> genererMiniQuizIA(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        // ── Authentification ──────────────────────────────────
        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit à cette formation"));

        CoursEntity cours = getCoursFromFormation(coursId, formationId);
        if (cours == null)
            return ResponseEntity.notFound().build();

        // ── Paramètres de génération ──────────────────────────
        int nbQuestions       = getInt(payload, "nombreQuestions", 5);
        String difficulte     = getString(payload, "difficulte", "MOYEN");
        boolean inclDef       = getBool(payload, "inclureDefinitions", true);
        boolean inclPrat      = getBool(payload, "inclureCasPratiques", true);
        float notePassage     = getFloat(payload, "notePassage", 70f);
        int nombreTentatives  = getInt(payload, "nombreTentatives", 3);

        // Validation
        nbQuestions = Math.max(3, Math.min(10, nbQuestions));
        if (!List.of("FACILE", "MOYEN", "DIFFICILE").contains(difficulte.toUpperCase())) {
            difficulte = "MOYEN";
        }

        // ── Construction du contexte cours ────────────────────
        CoursInfoIA coursInfo = new CoursInfoIA(
                cours.getTitre(),
                cours.getDescription(),
                cours.getObjectifs()
        );

        // ── Suppression de l'ancien quiz s'il existe ─────────
        // La suppression doit être flushée AVANT l'insertion pour éviter
        // l'erreur "Duplicate entry" sur la contrainte unique cours_id
        if (quizRepository.existsByCoursId(coursId)) {
            quizRepository.deleteByCoursId(coursId);
            quizRepository.flush(); // ← force le DELETE en base AVANT l'INSERT
            log.info("Ancien quiz supprimé pour le cours {}", coursId);
        }

        // ── Génération IA — avec contenu PDF + YouTube ────────
        log.info("Génération mini-quiz IA pour le cours '{}' (id={}) | nbQ={} diff={} videoType={}",
                cours.getTitre(), coursId, nbQuestions, difficulte, cours.getVideoType());

        List<QuizQuestionIA> questionsIA = iaQuizService.genererQuiz(
                coursInfo,
                cours.getId(),          // ← pour extraction PDF
                cours.getVideoUrl(),    // ← URL YouTube ou null
                cours.getVideoType(),   // ← "YOUTUBE", "LOCAL" ou null
                nbQuestions,
                difficulte,
                inclDef,
                inclPrat
        );

        if (questionsIA == null || questionsIA.isEmpty()) {
            return ResponseEntity.status(500)
                    .body(Map.of("message", "Erreur lors de la génération IA. Veuillez réessayer."));
        }

        // ── Persistence en base ───────────────────────────────
        QuizEntity quiz = QuizEntity.builder()
                .type("MiniQuiz")
                .notePassage(notePassage)
                .nombreTentatives(nombreTentatives)
                .genereParIA(true)
                .niveauDifficulte(difficulte.toUpperCase())
                .inclureDefinitions(inclDef)
                .inclureCasPratiques(inclPrat)
                .dateCreation(LocalDateTime.now())
                .cours(cours)
                .questions(new ArrayList<>())
                .build();

        quizRepository.save(quiz);

        for (int i = 0; i < questionsIA.size(); i++) {
            QuizQuestionIA qIA = questionsIA.get(i);

            QuestionEntity question = QuestionEntity.builder()
                    .texte(qIA.getTexte())
                    .explication(qIA.getExplication())
                    .ordre(i + 1)
                    .genereParIA(true)
                    .quiz(quiz)
                    .options(new ArrayList<>())
                    .build();

            if (qIA.getOptions() != null) {
                for (OptionIA optIA : qIA.getOptions()) {
                    OptionQuestionEntity option = OptionQuestionEntity.builder()
                            .texte(optIA.getTexte())
                            .estCorrecte(optIA.isEstCorrecte())
                            .ordre(optIA.getOrdre())
                            .question(question)
                            .build();
                    question.getOptions().add(option);
                }
            }

            quiz.getQuestions().add(question);
        }

        quizRepository.save(quiz);

        log.info("Quiz IA créé avec {} questions pour le cours '{}'",
                questionsIA.size(), cours.getTitre());

        return ResponseEntity.ok(toQuizResponse(quiz));
    }

    // ══════════════════════════════════════════════════════════
    // RÉCUPÉRER LE QUIZ EXISTANT
    // ══════════════════════════════════════════════════════════

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMiniQuiz(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        Optional<QuizEntity> quizOpt = quizRepository.findByCoursIdWithQuestions(coursId);
        if (quizOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("exists", false));
        }

        return ResponseEntity.ok(toQuizResponse(quizOpt.get()));
    }

    // ══════════════════════════════════════════════════════════
    // RÉCUPÉRER LE CONTEXTE (infos cours + documents + vidéo)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/contexte")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getContexte(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        CoursEntity cours = getCoursFromFormation(coursId, formationId);
        if (cours == null) return ResponseEntity.notFound().build();

        Map<String, Object> contexte = new HashMap<>();
        contexte.put("coursId",      cours.getId());
        contexte.put("coursTitre",   cours.getTitre());
        contexte.put("description",  cours.getDescription());
        contexte.put("objectifs",    cours.getObjectifs());
        contexte.put("videoType",    cours.getVideoType());
        contexte.put("videoUrl",     cours.getVideoUrl());
        contexte.put("quizExistant", quizRepository.existsByCoursId(coursId));

        return ResponseEntity.ok(contexte);
    }

    // ══════════════════════════════════════════════════════════
    // MODIFIER UNE QUESTION
    // ══════════════════════════════════════════════════════════

    @PutMapping("/questions/{questionId}")
    @Transactional
    public ResponseEntity<?> updateQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        Optional<QuizEntity> quizOpt = quizRepository.findByCoursIdWithQuestions(coursId);
        if (quizOpt.isEmpty())
            return ResponseEntity.notFound().build();

        QuizEntity quiz = quizOpt.get();
        Optional<QuestionEntity> qOpt = quiz.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst();

        if (qOpt.isEmpty())
            return ResponseEntity.notFound().build();

        QuestionEntity question = qOpt.get();

        if (payload.containsKey("texte"))
            question.setTexte((String) payload.get("texte"));
        if (payload.containsKey("explication"))
            question.setExplication((String) payload.get("explication"));

        quizRepository.save(quiz);
        return ResponseEntity.ok(toQuizResponse(quiz));
    }

    // ══════════════════════════════════════════════════════════
    // SUPPRIMER UNE QUESTION
    // ══════════════════════════════════════════════════════════

    @DeleteMapping("/questions/{questionId}")
    @Transactional
    public ResponseEntity<?> deleteQuestion(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long questionId,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        Optional<QuizEntity> quizOpt = quizRepository.findByCoursIdWithQuestions(coursId);
        if (quizOpt.isEmpty())
            return ResponseEntity.notFound().build();

        QuizEntity quiz = quizOpt.get();
        quiz.getQuestions().removeIf(q -> q.getId().equals(questionId));
        quizRepository.save(quiz);

        return ResponseEntity.ok(Map.of("success", true, "message", "Question supprimée"));
    }

    // ══════════════════════════════════════════════════════════
    // SUPPRIMER LE QUIZ ENTIER
    // ══════════════════════════════════════════════════════════

    @DeleteMapping
    @Transactional
    public ResponseEntity<?> deleteMiniQuiz(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        if (!quizRepository.existsByCoursId(coursId))
            return ResponseEntity.notFound().build();

        quizRepository.deleteByCoursId(coursId);
        log.info("Quiz supprimé pour le cours {}", coursId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Quiz supprimé"));
    }

    // ══════════════════════════════════════════════════════════
    // MODIFIER LES PARAMÈTRES DU QUIZ
    // ══════════════════════════════════════════════════════════

    @PatchMapping("/parametres")
    @Transactional
    public ResponseEntity<?> updateParametres(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        Optional<QuizEntity> quizOpt = quizRepository.findByCoursIdWithQuestions(coursId);
        if (quizOpt.isEmpty())
            return ResponseEntity.notFound().build();

        QuizEntity quiz = quizOpt.get();

        if (payload.containsKey("notePassage"))
            quiz.setNotePassage(getFloat(payload, "notePassage", quiz.getNotePassage()));
        if (payload.containsKey("nombreTentatives"))
            quiz.setNombreTentatives(getInt(payload, "nombreTentatives", quiz.getNombreTentatives()));

        quizRepository.save(quiz);
        return ResponseEntity.ok(toQuizResponse(quiz));
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════

    private UserEntity getFormateurFromRequest(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            if (!jwtUtil.isValid(token)) return null;
            String email = jwtUtil.extractEmail(token);
            UserEntity user = userRepository.findByEmail(email).orElse(null);
            if (user == null || user.getRole() != Role.FORMATEUR) return null;
            return user;
        } catch (Exception e) {
            log.warn("Erreur JWT: {}", e.getMessage());
            return null;
        }
    }

    private FormationEntity getFormationAuthorisee(Long formationId, UserEntity formateur) {
        FormationEntity formation = formationRepository.findById(formationId).orElse(null);
        if (formation == null) return null;
        if (formation.getFormateur() == null ||
                !formation.getFormateur().getId().equals(formateur.getId())) return null;
        return formation;
    }

    private CoursEntity getCoursFromFormation(Long coursId, Long formationId) {
        return coursRepository.findById(coursId)
                .filter(c -> c.getFormation().getId().equals(formationId))
                .orElse(null);
    }

    private Map<String, Object> toQuizResponse(QuizEntity quiz) {
        Map<String, Object> map = new HashMap<>();
        map.put("exists",            true);
        map.put("id",                quiz.getId());
        map.put("type",              quiz.getType());
        map.put("notePassage",       quiz.getNotePassage());
        map.put("nombreTentatives",  quiz.getNombreTentatives());
        map.put("genereParIA",       quiz.getGenereParIA());
        map.put("niveauDifficulte",  quiz.getNiveauDifficulte());
        map.put("inclureDefinitions",  quiz.getInclureDefinitions());
        map.put("inclureCasPratiques", quiz.getInclureCasPratiques());
        map.put("dateCreation",      quiz.getDateCreation());
        map.put("nbQuestions",       quiz.getQuestions() != null ? quiz.getQuestions().size() : 0);

        if (quiz.getQuestions() != null) {
            List<Map<String, Object>> questions = quiz.getQuestions().stream()
                    .sorted(Comparator.comparing(QuestionEntity::getOrdre))
                    .map(q -> {
                        Map<String, Object> qMap = new HashMap<>();
                        qMap.put("id",          q.getId());
                        qMap.put("texte",        q.getTexte());
                        qMap.put("explication",  q.getExplication());
                        qMap.put("ordre",        q.getOrdre());
                        qMap.put("genereParIA",  q.getGenereParIA());

                        if (q.getOptions() != null) {
                            List<Map<String, Object>> options = q.getOptions().stream()
                                    .sorted(Comparator.comparing(OptionQuestionEntity::getOrdre))
                                    .map(o -> {
                                        Map<String, Object> oMap = new HashMap<>();
                                        oMap.put("id",          o.getId());
                                        oMap.put("texte",       o.getTexte());
                                        oMap.put("estCorrecte", o.getEstCorrecte());
                                        oMap.put("ordre",       o.getOrdre());
                                        return oMap;
                                    })
                                    .collect(Collectors.toList());
                            qMap.put("options", options);
                        }
                        return qMap;
                    })
                    .collect(Collectors.toList());
            map.put("questions", questions);
        }

        return map;
    }

    // ── Helpers de parsing ────────────────────────────────────

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        return Integer.parseInt(val.toString());
    }

    private float getFloat(Map<String, Object> map, String key, float defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        return Float.parseFloat(val.toString());
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        return val.toString();
    }

    private boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        return Boolean.parseBoolean(val.toString());
    }
}
