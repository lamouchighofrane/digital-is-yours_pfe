package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.OptionQuestion;
import com.digitalisyours.domain.model.Question;
import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.port.out.QuizFinalFormationRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.QuizJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuizFinalFormationRepositoryAdapter implements QuizFinalFormationRepositoryPort {
    private final QuizJpaRepository quizJpaRepository;
    private final FormationJpaRepository formationJpaRepository;
    private final CoursJpaRepository coursJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ══════════════════════════════════════════════════════════
    // LECTURE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Optional<Quiz> findByFormationId(Long formationId) {
        List<QuizEntity> quizList = entityManager.createQuery(
                        "SELECT DISTINCT q FROM QuizEntity q " +
                                "LEFT JOIN FETCH q.questions " +
                                "WHERE q.formation.id = :formationId " +
                                "AND q.type = 'QuizFinal'",
                        QuizEntity.class)
                .setParameter("formationId", formationId)
                .getResultList();

        if (quizList.isEmpty()) return Optional.empty();
        QuizEntity quiz = quizList.get(0);

        entityManager.createQuery(
                        "SELECT DISTINCT qs FROM QuestionEntity qs " +
                                "LEFT JOIN FETCH qs.options " +
                                "WHERE qs.quiz.id = :quizId",
                        QuestionEntity.class)
                .setParameter("quizId", quiz.getId())
                .getResultList();

        return Optional.of(toDomain(quiz));
    }

    @Override
    public boolean existsByFormationId(Long formationId) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(q) FROM QuizEntity q " +
                                "WHERE q.formation.id = :formationId AND q.type = 'QuizFinal'",
                        Long.class)
                .setParameter("formationId", formationId)
                .getSingleResult();
        return count != null && count > 0;
    }

    // ══════════════════════════════════════════════════════════
    // INFOS FORMATION
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getFormationInfos(Long formationId) {
        FormationEntity f = formationJpaRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation introuvable : " + formationId));

        List<CoursEntity> cours = coursJpaRepository.findByFormationIdOrderByOrdre(formationId);

        Map<String, Object> infos = new LinkedHashMap<>();
        infos.put("formationId",           f.getId());
        infos.put("titre",                 f.getTitre()                 != null ? f.getTitre()                 : "");
        infos.put("description",           f.getDescription()           != null ? f.getDescription()           : "");
        infos.put("objectifsApprentissage", f.getObjectifsApprentissage() != null ? f.getObjectifsApprentissage() : "");
        infos.put("niveau",                f.getNiveau());
        infos.put("nbCours",               cours.size());

        List<Map<String, Object>> coursList = cours.stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id",           c.getId());
            cm.put("titre",        c.getTitre() != null ? c.getTitre() : "");
            cm.put("dureeEstimee", c.getDureeEstimee());
            cm.put("videoType",    c.getVideoType());
            return cm;
        }).collect(Collectors.toList());
        infos.put("cours", coursList);

        int dureeTotal = cours.stream()
                .mapToInt(c -> c.getDureeEstimee() != null ? c.getDureeEstimee() : 0)
                .sum();
        infos.put("dureeEstimeeTotal", dureeTotal);
        infos.put("quizFinalExistant", existsByFormationId(formationId));

        return infos;
    }

    // ══════════════════════════════════════════════════════════
    // SAUVEGARDE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public Quiz save(Quiz quiz) {
        if (quiz.getId() == null) {
            return saveNew(quiz);
        } else {
            return saveExisting(quiz);
        }
    }

    private Quiz saveNew(Quiz quiz) {
        Long formationId = quiz.getFormationId();
        FormationEntity formation = formationJpaRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée : " + formationId));

        // Supprimer l'ancien quiz final si existant — avec cascade manuelle
        if (existsByFormationId(formationId)) {
            supprimerQuizFinauxDeFormation(formationId);
            log.debug("Quiz final existant supprimé pour formation {}", formationId);
        }

        QuizEntity entity = new QuizEntity();
        entity.setType("QuizFinal");
        entity.setNotePassage(quiz.getNotePassage() != null ? quiz.getNotePassage() : 70f);
        entity.setNombreTentatives(quiz.getNombreTentatives() != null ? quiz.getNombreTentatives() : 3);
        entity.setGenereParIA(quiz.getGenereParIA() != null ? quiz.getGenereParIA() : false);
        entity.setNiveauDifficulte(quiz.getNiveauDifficulte());
        entity.setInclureDefinitions(quiz.getInclureDefinitions() != null ? quiz.getInclureDefinitions() : true);
        entity.setInclureCasPratiques(quiz.getInclureCasPratiques() != null ? quiz.getInclureCasPratiques() : true);
        entity.setDateCreation(quiz.getDateCreation());
        entity.setFormation(formation);
        entity.setDureeMinutes(quiz.getDureeMinutes() != null ? quiz.getDureeMinutes() : 60);
        entity.setCours(null);
        entity.setQuestions(new ArrayList<>());

        if (quiz.getQuestions() != null) {
            for (Question q : quiz.getQuestions()) {
                entity.getQuestions().add(buildNewQuestionEntity(q, entity));
            }
        }

        QuizEntity saved = quizJpaRepository.saveAndFlush(entity);
        log.debug("Quiz Final inséré pour formation {}, id={}", formationId, saved.getId());
        return toDomain(saved);
    }

    private Quiz saveExisting(Quiz quiz) {
        QuizEntity entity = quizJpaRepository.findById(quiz.getId())
                .orElseThrow(() -> new RuntimeException("Quiz Final introuvable : " + quiz.getId()));

        if (quiz.getNotePassage() != null)         entity.setNotePassage(quiz.getNotePassage());
        if (quiz.getNombreTentatives() != null)    entity.setNombreTentatives(quiz.getNombreTentatives());
        if (quiz.getGenereParIA() != null)         entity.setGenereParIA(quiz.getGenereParIA());
        if (quiz.getNiveauDifficulte() != null)    entity.setNiveauDifficulte(quiz.getNiveauDifficulte());
        if (quiz.getInclureDefinitions() != null)  entity.setInclureDefinitions(quiz.getInclureDefinitions());
        if (quiz.getInclureCasPratiques() != null) entity.setInclureCasPratiques(quiz.getInclureCasPratiques());
        if (quiz.getDureeMinutes() != null)        entity.setDureeMinutes(quiz.getDureeMinutes());

        Map<Long, QuestionEntity> existingQMap = entity.getQuestions().stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(QuestionEntity::getId, q -> q));

        Set<Long> expectedQIds = quiz.getQuestions().stream()
                .filter(q -> q.getId() != null)
                .map(Question::getId)
                .collect(Collectors.toSet());

        entity.getQuestions().removeIf(q -> q.getId() != null && !expectedQIds.contains(q.getId()));

        for (Question domQ : quiz.getQuestions()) {
            if (domQ.getId() != null && existingQMap.containsKey(domQ.getId())) {
                QuestionEntity qEntity = existingQMap.get(domQ.getId());
                if (domQ.getTexte() != null)       qEntity.setTexte(domQ.getTexte());
                if (domQ.getExplication() != null) qEntity.setExplication(domQ.getExplication());
                if (domQ.getOrdre() != null)       qEntity.setOrdre(domQ.getOrdre());
                syncOptions(qEntity, domQ.getOptions());
            } else {
                entity.getQuestions().add(buildNewQuestionEntity(domQ, entity));
            }
        }

        List<QuestionEntity> sorted = entity.getQuestions().stream()
                .sorted(Comparator.comparingInt(q -> q.getOrdre() != null ? q.getOrdre() : 999))
                .collect(Collectors.toList());
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).setOrdre(i + 1);

        QuizEntity saved = quizJpaRepository.saveAndFlush(entity);
        log.debug("Quiz Final {} mis à jour pour formation {}", saved.getId(), saved.getFormation().getId());
        return toDomain(saved);
    }

    private void syncOptions(QuestionEntity qEntity, List<OptionQuestion> domOptions) {
        if (domOptions == null) return;
        Map<Long, OptionQuestionEntity> existingOptMap = qEntity.getOptions().stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(OptionQuestionEntity::getId, o -> o));
        Set<Long> expectedOptIds = domOptions.stream()
                .filter(o -> o.getId() != null)
                .map(OptionQuestion::getId)
                .collect(Collectors.toSet());
        qEntity.getOptions().removeIf(o -> o.getId() != null && !expectedOptIds.contains(o.getId()));
        for (OptionQuestion domOpt : domOptions) {
            if (domOpt.getId() != null && existingOptMap.containsKey(domOpt.getId())) {
                OptionQuestionEntity oEntity = existingOptMap.get(domOpt.getId());
                if (domOpt.getTexte() != null)      oEntity.setTexte(domOpt.getTexte());
                if (domOpt.getEstCorrecte() != null) oEntity.setEstCorrecte(domOpt.getEstCorrecte());
                if (domOpt.getOrdre() != null)       oEntity.setOrdre(domOpt.getOrdre());
            } else {
                OptionQuestionEntity newOpt = OptionQuestionEntity.builder()
                        .texte(domOpt.getTexte())
                        .estCorrecte(domOpt.getEstCorrecte() != null ? domOpt.getEstCorrecte() : false)
                        .ordre(domOpt.getOrdre())
                        .question(qEntity)
                        .build();
                qEntity.getOptions().add(newOpt);
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // SUPPRESSION
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void deleteByFormationId(Long formationId) {
        supprimerQuizFinauxDeFormation(formationId);
        log.debug("Quiz Final supprimé pour formation {}", formationId);
    }

    /**
     * Supprime dans le bon ordre : options → questions → quiz
     * pour respecter les contraintes de clé étrangère MySQL.
     */
    private void supprimerQuizFinauxDeFormation(Long formationId) {
        // 1. Récupérer les IDs des quiz finaux
        List<Long> quizIds = entityManager.createQuery(
                        "SELECT q.id FROM QuizEntity q " +
                                "WHERE q.formation.id = :fId AND q.type = 'QuizFinal'",
                        Long.class)
                .setParameter("fId", formationId)
                .getResultList();

        if (quizIds.isEmpty()) return;

        for (Long quizId : quizIds) {
            // 2. Récupérer les IDs des questions
            List<Long> questionIds = entityManager.createQuery(
                            "SELECT qs.id FROM QuestionEntity qs WHERE qs.quiz.id = :quizId",
                            Long.class)
                    .setParameter("quizId", quizId)
                    .getResultList();

            if (!questionIds.isEmpty()) {
                // 3. Supprimer les options
                entityManager.createQuery(
                                "DELETE FROM OptionQuestionEntity o WHERE o.question.id IN :questionIds")
                        .setParameter("questionIds", questionIds)
                        .executeUpdate();

                // 4. Supprimer les questions
                entityManager.createQuery(
                                "DELETE FROM QuestionEntity qs WHERE qs.quiz.id = :quizId")
                        .setParameter("quizId", quizId)
                        .executeUpdate();
            }
        }

        // 5. Supprimer le quiz
        entityManager.createQuery(
                        "DELETE FROM QuizEntity q " +
                                "WHERE q.formation.id = :fId AND q.type = 'QuizFinal'")
                .setParameter("fId", formationId)
                .executeUpdate();

        entityManager.flush();
    }

    // ══════════════════════════════════════════════════════════
    // ACCÈS FORMATEUR
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public boolean isFormateurOfFormation(Long formationId, String email) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(f) FROM FormationEntity f " +
                                "WHERE f.id = :formationId AND f.formateur.email = :email",
                        Long.class)
                .setParameter("formationId", formationId)
                .setParameter("email", email)
                .getSingleResult();
        return count != null && count > 0;
    }

    // ══════════════════════════════════════════════════════════
    // MAPPING Entity → Domain
    // ══════════════════════════════════════════════════════════

    private Quiz toDomain(QuizEntity e) {
        List<Question> questions = e.getQuestions() == null ? new ArrayList<>()
                : e.getQuestions().stream()
                .sorted((a, b) -> {
                    int oa = a.getOrdre() != null ? a.getOrdre() : 0;
                    int ob = b.getOrdre() != null ? b.getOrdre() : 0;
                    return Integer.compare(oa, ob);
                })
                .map(this::questionToDomain)
                .collect(Collectors.toList());

        Quiz.QuizBuilder builder = Quiz.builder()
                .id(e.getId())
                .type(e.getType())
                .notePassage(e.getNotePassage())
                .nombreTentatives(e.getNombreTentatives())
                .genereParIA(e.getGenereParIA())
                .niveauDifficulte(e.getNiveauDifficulte())
                .inclureDefinitions(e.getInclureDefinitions())
                .inclureCasPratiques(e.getInclureCasPratiques())
                .dateCreation(e.getDateCreation())
                .dureeMinutes(e.getDureeMinutes())
                .questions(questions);

        if (e.getFormation() != null) {
            builder.formationId(e.getFormation().getId())
                    .formationTitre(e.getFormation().getTitre());
        }

        return builder.build();
    }

    private Question questionToDomain(QuestionEntity e) {
        List<OptionQuestion> options = e.getOptions() == null ? new ArrayList<>()
                : e.getOptions().stream()
                .sorted((a, b) -> {
                    String oa = a.getOrdre() != null ? a.getOrdre() : "Z";
                    String ob = b.getOrdre() != null ? b.getOrdre() : "Z";
                    return oa.compareTo(ob);
                })
                .map(this::optionToDomain)
                .collect(Collectors.toList());

        return Question.builder()
                .id(e.getId())
                .texte(e.getTexte())
                .explication(e.getExplication())
                .ordre(e.getOrdre())
                .genereParIA(e.getGenereParIA())
                .quizId(e.getQuiz() != null ? e.getQuiz().getId() : null)
                .options(options)
                .build();
    }

    private OptionQuestion optionToDomain(OptionQuestionEntity e) {
        return OptionQuestion.builder()
                .id(e.getId())
                .texte(e.getTexte())
                .estCorrecte(e.getEstCorrecte())
                .ordre(e.getOrdre())
                .questionId(e.getQuestion() != null ? e.getQuestion().getId() : null)
                .build();
    }

    private QuestionEntity buildNewQuestionEntity(Question q, QuizEntity quizEntity) {
        QuestionEntity qEntity = QuestionEntity.builder()
                .texte(q.getTexte())
                .explication(q.getExplication())
                .ordre(q.getOrdre())
                .genereParIA(q.getGenereParIA() != null ? q.getGenereParIA() : false)
                .quiz(quizEntity)
                .options(new ArrayList<>())
                .build();

        if (q.getOptions() != null) {
            for (OptionQuestion opt : q.getOptions()) {
                OptionQuestionEntity oEntity = OptionQuestionEntity.builder()
                        .texte(opt.getTexte())
                        .estCorrecte(opt.getEstCorrecte() != null ? opt.getEstCorrecte() : false)
                        .ordre(opt.getOrdre())
                        .question(qEntity)
                        .build();
                qEntity.getOptions().add(oEntity);
            }
        }
        return qEntity;
    }
}
