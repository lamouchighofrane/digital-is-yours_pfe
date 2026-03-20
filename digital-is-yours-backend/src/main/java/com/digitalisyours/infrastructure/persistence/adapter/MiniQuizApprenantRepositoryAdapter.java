package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.OptionQuestion;
import com.digitalisyours.domain.model.Question;
import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.port.out.MiniQuizApprenantRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.OptionQuestionEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuestionEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.QuizJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MiniQuizApprenantRepositoryAdapter implements MiniQuizApprenantRepositoryPort {
    private final InscriptionJpaRepository inscriptionRepository;
    private final CoursJpaRepository coursRepository;
    private final QuizJpaRepository quizRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ══════════════════════════════════════════════════════
    // Vérification inscription
    // ══════════════════════════════════════════════════════

    @Override
    public boolean estInscritEtPaye(String email, Long formationId) {
        return inscriptionRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(email, formationId, "PAYE");
    }

    // ══════════════════════════════════════════════════════
    // Vérification cours ↔ formation
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public boolean coursAppartientAFormation(Long coursId, Long formationId) {
        return coursRepository.findById(coursId)
                .map(c -> c.getFormation() != null
                        && formationId.equals(c.getFormation().getId()))
                .orElse(false);
    }

    // ══════════════════════════════════════════════════════
    // Récupérer le MiniQuiz avec questions + options (2 requêtes séparées
    // pour éviter MultipleBagFetchException)
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Optional<Quiz> findMiniQuizByCoursId(Long coursId) {

        // Requête 1 : quiz + questions
        List<QuizEntity> quizList = entityManager.createQuery(
                        "SELECT DISTINCT q FROM QuizEntity q " +
                                "LEFT JOIN FETCH q.questions " +
                                "WHERE q.cours.id = :coursId " +
                                "AND q.type = 'MiniQuiz'",
                        QuizEntity.class)
                .setParameter("coursId", coursId)
                .getResultList();

        if (quizList.isEmpty()) return Optional.empty();

        QuizEntity quiz = quizList.get(0);

        // Requête 2 : questions + options (fusion dans le cache L1 Hibernate)
        entityManager.createQuery(
                        "SELECT DISTINCT qs FROM QuestionEntity qs " +
                                "LEFT JOIN FETCH qs.options " +
                                "WHERE qs.quiz.id = :quizId",
                        QuestionEntity.class)
                .setParameter("quizId", quiz.getId())
                .getResultList();

        return Optional.of(toDomain(quiz));
    }

    // ══════════════════════════════════════════════════════
    // Mapping Entity → Domain
    // ══════════════════════════════════════════════════════

    private Quiz toDomain(QuizEntity e) {
        List<Question> questions = e.getQuestions() == null ? new ArrayList<>()
                : e.getQuestions().stream()
                .sorted(Comparator.comparingInt(q -> q.getOrdre() != null ? q.getOrdre() : 0))
                .map(this::questionToDomain)
                .collect(Collectors.toList());

        return Quiz.builder()
                .id(e.getId())
                .type(e.getType())
                .notePassage(e.getNotePassage())
                .nombreTentatives(e.getNombreTentatives())
                .genereParIA(e.getGenereParIA())
                .niveauDifficulte(e.getNiveauDifficulte())
                .inclureDefinitions(e.getInclureDefinitions())
                .inclureCasPratiques(e.getInclureCasPratiques())
                .dateCreation(e.getDateCreation())
                .coursId(e.getCours() != null ? e.getCours().getId() : null)
                .coursTitre(e.getCours() != null ? e.getCours().getTitre() : null)
                .questions(questions)
                .build();
    }

    private Question questionToDomain(QuestionEntity e) {
        List<OptionQuestion> options = e.getOptions() == null ? new ArrayList<>()
                : e.getOptions().stream()
                .sorted(Comparator.comparing(o -> o.getOrdre() != null ? o.getOrdre() : "Z"))
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
                .estCorrecte(e.getEstCorrecte())   // ← inclus ici (usage interne service)
                .ordre(e.getOrdre())
                .questionId(e.getQuestion() != null ? e.getQuestion().getId() : null)
                .build();
    }
}
