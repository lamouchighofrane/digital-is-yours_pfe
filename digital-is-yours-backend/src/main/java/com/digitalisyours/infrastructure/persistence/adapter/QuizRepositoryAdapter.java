package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.OptionQuestion;
import com.digitalisyours.domain.model.Question;
import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.port.out.QuizRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.OptionQuestionEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuestionEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
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
public class QuizRepositoryAdapter implements QuizRepositoryPort {
    private final QuizJpaRepository      quizJpaRepository;
    private final CoursJpaRepository     coursJpaRepository;
    private final FormationJpaRepository formationJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ══════════════════════════════════════════════════════════
    // LECTURE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Optional<Quiz> findByCoursId(Long coursId) {
        // FIX MultipleBagFetchException : 2 requêtes séparées dans la même transaction
        // Requête 1 : quiz + questions
        List<QuizEntity> quizList = entityManager.createQuery(
                        "SELECT DISTINCT q FROM QuizEntity q " +
                                "LEFT JOIN FETCH q.questions " +
                                "WHERE q.cours.id = :coursId",
                        QuizEntity.class)
                .setParameter("coursId", coursId)
                .getResultList();

        if (quizList.isEmpty()) return Optional.empty();

        QuizEntity quiz = quizList.get(0);

        // Requête 2 : questions + options (Hibernate fusionne dans le cache L1)
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
    public boolean existsByCoursId(Long coursId) {
        return quizJpaRepository.existsByCoursId(coursId);
    }

    // ══════════════════════════════════════════════════════════
    // INFOS COURS
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCoursInfos(Long coursId) {
        CoursEntity c = coursJpaRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours introuvable : " + coursId));

        Map<String, Object> infos = new LinkedHashMap<>();
        infos.put("titre",       c.getTitre()       != null ? c.getTitre()       : "");
        infos.put("description", c.getDescription() != null ? c.getDescription() : "");
        infos.put("objectifs",   c.getObjectifs()   != null ? c.getObjectifs()   : "");
        infos.put("videoType",   c.getVideoType());
        infos.put("videoUrl",    c.getVideoUrl());
        return infos;
    }

    // ══════════════════════════════════════════════════════════
    // SAUVEGARDE
    //
    // Deux stratégies selon le cas :
    //
    // ── CAS 1 : quiz.getId() == null → CRÉATION (IA ou manuel)
    //    DELETE ancien quiz si existe + flush + INSERT nouveau quiz
    //
    // ── CAS 2 : quiz.getId() != null → MODIFICATION (édition formateur)
    //    Charger l'entité existante, mettre à jour ses champs et ses
    //    questions/options via entityManager.merge() pour éviter
    //    "detached entity passed to persist"
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

    // ── CAS 1 : Création (génération IA ou ajout de question qui crée un nouveau quiz)
    private Quiz saveNew(Quiz quiz) {
        Long coursId = quiz.getCoursId();

        CoursEntity cours = coursJpaRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé : " + coursId));

        // Supprimer l'ancien quiz si existant
        if (quizJpaRepository.existsByCoursId(coursId)) {
            quizJpaRepository.deleteByCoursId(coursId);
            quizJpaRepository.flush();
            log.debug("Quiz existant supprimé pour cours {}", coursId);
        }

        // Construire la nouvelle entité (toujours new, pas d'id)
        QuizEntity entity = new QuizEntity();
        entity.setType(quiz.getType() != null ? quiz.getType() : "MiniQuiz");
        entity.setNotePassage(quiz.getNotePassage() != null ? quiz.getNotePassage() : 70f);
        entity.setNombreTentatives(quiz.getNombreTentatives() != null ? quiz.getNombreTentatives() : 3);
        entity.setGenereParIA(quiz.getGenereParIA() != null ? quiz.getGenereParIA() : false);
        entity.setNiveauDifficulte(quiz.getNiveauDifficulte());
        entity.setInclureDefinitions(quiz.getInclureDefinitions() != null ? quiz.getInclureDefinitions() : true);
        entity.setInclureCasPratiques(quiz.getInclureCasPratiques() != null ? quiz.getInclureCasPratiques() : true);
        entity.setDateCreation(quiz.getDateCreation());
        entity.setCours(cours);
        entity.setQuestions(new ArrayList<>());

        if (quiz.getQuestions() != null) {
            for (Question q : quiz.getQuestions()) {
                entity.getQuestions().add(buildNewQuestionEntity(q, entity));
            }
        }

        QuizEntity saved = quizJpaRepository.saveAndFlush(entity);
        log.debug("Quiz inséré pour cours {}, id={}", coursId, saved.getId());
        return toDomain(saved);
    }

    // ── CAS 2 : Modification (updateQuestion, updateOption, setBonneReponse,
    //            addQuestion, deleteQuestion)
    //    On charge l'entité managée depuis la BD et on la met à jour en place.
    //    entityManager.merge() évite "detached entity passed to persist".
    private Quiz saveExisting(Quiz quiz) {
        // Charger l'entité quiz managée (avec ses questions et options déjà en cache L1)
        QuizEntity entity = quizJpaRepository.findById(quiz.getId())
                .orElseThrow(() -> new RuntimeException("Quiz introuvable : " + quiz.getId()));

        // Mettre à jour les champs scalaires du quiz
        if (quiz.getType() != null)              entity.setType(quiz.getType());
        if (quiz.getNotePassage() != null)        entity.setNotePassage(quiz.getNotePassage());
        if (quiz.getNombreTentatives() != null)   entity.setNombreTentatives(quiz.getNombreTentatives());
        if (quiz.getGenereParIA() != null)        entity.setGenereParIA(quiz.getGenereParIA());
        if (quiz.getNiveauDifficulte() != null)   entity.setNiveauDifficulte(quiz.getNiveauDifficulte());
        if (quiz.getInclureDefinitions() != null) entity.setInclureDefinitions(quiz.getInclureDefinitions());
        if (quiz.getInclureCasPratiques() != null)entity.setInclureCasPratiques(quiz.getInclureCasPratiques());

        // ── Synchroniser les questions ──────────────────────────
        // Construire un index des questions existantes en BD
        Map<Long, QuestionEntity> existingQMap = entity.getQuestions().stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(QuestionEntity::getId, q -> q));

        // Ids des questions attendues dans le domaine
        Set<Long> expectedQIds = quiz.getQuestions().stream()
                .filter(q -> q.getId() != null)
                .map(Question::getId)
                .collect(Collectors.toSet());

        // Supprimer les questions retirées (orphanRemoval les supprime en BD)
        entity.getQuestions().removeIf(q -> q.getId() != null && !expectedQIds.contains(q.getId()));

        // Mettre à jour ou ajouter chaque question du domaine
        for (Question domQ : quiz.getQuestions()) {
            if (domQ.getId() != null && existingQMap.containsKey(domQ.getId())) {
                // Question existante → mettre à jour ses champs
                QuestionEntity qEntity = existingQMap.get(domQ.getId());
                if (domQ.getTexte() != null)      qEntity.setTexte(domQ.getTexte());
                if (domQ.getExplication() != null) qEntity.setExplication(domQ.getExplication());
                if (domQ.getOrdre() != null)       qEntity.setOrdre(domQ.getOrdre());

                // Synchroniser les options de cette question
                syncOptions(qEntity, domQ.getOptions());
            } else {
                // Nouvelle question → créer et ajouter
                QuestionEntity newQ = buildNewQuestionEntity(domQ, entity);
                entity.getQuestions().add(newQ);
            }
        }

        // Renuméroter l'ordre des questions
        List<QuestionEntity> sortedQuestions = entity.getQuestions().stream()
                .sorted(Comparator.comparingInt(q -> q.getOrdre() != null ? q.getOrdre() : 999))
                .collect(Collectors.toList());
        for (int i = 0; i < sortedQuestions.size(); i++) {
            sortedQuestions.get(i).setOrdre(i + 1);
        }

        // saveAndFlush sur l'entité managée → Hibernate fait un UPDATE, pas un INSERT
        QuizEntity saved = quizJpaRepository.saveAndFlush(entity);
        log.debug("Quiz {} mis à jour pour cours {}", saved.getId(), saved.getCours().getId());
        return toDomain(saved);
    }

    // Synchronise les options d'une question existante
    private void syncOptions(QuestionEntity qEntity, List<OptionQuestion> domOptions) {
        if (domOptions == null) return;

        Map<Long, OptionQuestionEntity> existingOptMap = qEntity.getOptions().stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(OptionQuestionEntity::getId, o -> o));

        Set<Long> expectedOptIds = domOptions.stream()
                .filter(o -> o.getId() != null)
                .map(OptionQuestion::getId)
                .collect(Collectors.toSet());

        // Supprimer les options retirées
        qEntity.getOptions().removeIf(o -> o.getId() != null && !expectedOptIds.contains(o.getId()));

        for (OptionQuestion domOpt : domOptions) {
            if (domOpt.getId() != null && existingOptMap.containsKey(domOpt.getId())) {
                // Option existante → mettre à jour
                OptionQuestionEntity oEntity = existingOptMap.get(domOpt.getId());
                if (domOpt.getTexte() != null)       oEntity.setTexte(domOpt.getTexte());
                if (domOpt.getEstCorrecte() != null)  oEntity.setEstCorrecte(domOpt.getEstCorrecte());
                if (domOpt.getOrdre() != null)        oEntity.setOrdre(domOpt.getOrdre());
            } else {
                // Nouvelle option → créer et ajouter
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
    public void deleteByCoursId(Long coursId) {
        quizJpaRepository.deleteByCoursId(coursId);
        quizJpaRepository.flush();
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

    @Override
    @Transactional(readOnly = true)
    public boolean coursExistsInFormation(Long coursId, Long formationId) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(c) FROM CoursEntity c " +
                                "WHERE c.id = :coursId AND c.formation.id = :formationId",
                        Long.class)
                .setParameter("coursId", coursId)
                .setParameter("formationId", formationId)
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
                .questions(questions);

        if (e.getCours() != null) {
            builder.coursId(e.getCours().getId())
                    .coursTitre(e.getCours().getTitre())
                    .coursDescription(e.getCours().getDescription())
                    .coursObjectifs(e.getCours().getObjectifs())
                    .videoType(e.getCours().getVideoType())
                    .videoUrl(e.getCours().getVideoUrl());
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

    // ══════════════════════════════════════════════════════════
    // MAPPING Domain → Entity (nouvelle entité uniquement)
    // ══════════════════════════════════════════════════════════

    private QuestionEntity buildNewQuestionEntity(Question q, QuizEntity quizEntity) {
        QuestionEntity qEntity = QuestionEntity.builder()
                .texte(q.getTexte())
                .explication(q.getExplication())
                .ordre(q.getOrdre())
                .genereParIA(q.getGenereParIA() != null ? q.getGenereParIA() : false)
                .quiz(quizEntity)
                .options(new ArrayList<>())
                .build();
        // NE PAS copier l'id — on crée toujours une nouvelle entité ici

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
