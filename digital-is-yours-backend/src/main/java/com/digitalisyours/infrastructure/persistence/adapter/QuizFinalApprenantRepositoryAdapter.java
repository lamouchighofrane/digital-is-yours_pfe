package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.*;
import com.digitalisyours.domain.port.out.QuizFinalApprenantRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class QuizFinalApprenantRepositoryAdapter implements QuizFinalApprenantRepositoryPort {

    private final InscriptionJpaRepository       inscriptionRepository;
    private final ResultatQuizFinalJpaRepository resultatRepository;
    private final ApprenantJpaRepository         apprenantJpaRepository;

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
    // Récupérer le quiz final avec questions + options
    // (2 requêtes séparées pour éviter MultipleBagFetchException)
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Optional<Quiz> findQuizFinalByFormationId(Long formationId) {

        // Requête 1 : quiz + questions
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
    // Tentatives
    // ══════════════════════════════════════════════════════

    @Override
    public long countTentatives(String email, Long quizId) {
        return resultatRepository.countByApprenantEmailAndQuizId(email, quizId);
    }

    @Override
    public Optional<InfosQuizFinalApprenant.DernierResultat> findDernierResultat(
            String email, Long quizId) {

        return resultatRepository
                .findTopByApprenantEmailAndQuizIdOrderByDatePassageDesc(email, quizId)
                .map(r -> InfosQuizFinalApprenant.DernierResultat.builder()
                        .score(r.getScore())
                        .reussi(r.isReussi())
                        .nombreBonnesReponses(r.getNombreBonnesReponses())
                        .nombreQuestions(r.getNombreQuestions())
                        .datePassage(r.getDatePassage())
                        .tentativeNumero(r.getTentativeNumero())
                        .build());
    }

    // ══════════════════════════════════════════════════════
    // Sauvegarder résultat
    // ══════════════════════════════════════════════════════

    @Override
    @Transactional
    public ResultatQuizFinal saveResultat(ResultatQuizFinal resultat) {

        ResultatQuizFinalEntity entity = ResultatQuizFinalEntity.builder()
                .apprenantEmail(resultat.getApprenantEmail())
                .quizId(resultat.getQuizId())
                .formationId(resultat.getFormationId())
                .score(resultat.getScore())
                .nombreBonnesReponses(resultat.getNombreBonnesReponses())
                .nombreQuestions(resultat.getNombreQuestions())
                .tempsPasse(resultat.getTempsPasse())
                .reussi(Boolean.TRUE.equals(resultat.getReussi()))
                .notePassage(resultat.getNotePassage())
                .tentativeNumero(resultat.getTentativeNumero())
                .datePassage(resultat.getDatePassage())
                .build();

        ResultatQuizFinalEntity saved = resultatRepository.save(entity);
        resultat.setId(saved.getId());
        return resultat;
    }

    // ══════════════════════════════════════════════════════
    // Retrouver l'ID apprenant depuis son email
    // ══════════════════════════════════════════════════════

    @Override
    public Long findApprenantIdByEmail(String email) {
        return apprenantJpaRepository.findByEmail(email)
                .map(a -> a.getId())
                .orElseThrow(() -> new RuntimeException(
                        "Apprenant introuvable pour email : " + email));
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
                .dureeMinutes(e.getDureeMinutes())
                .formationId(e.getFormation() != null ? e.getFormation().getId() : null)
                .formationTitre(e.getFormation() != null ? e.getFormation().getTitre() : null)
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
                .estCorrecte(e.getEstCorrecte())
                .ordre(e.getOrdre())
                .questionId(e.getQuestion() != null ? e.getQuestion().getId() : null)
                .build();
    }
}