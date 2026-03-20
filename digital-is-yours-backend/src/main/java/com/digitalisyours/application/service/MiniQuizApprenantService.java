package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.*;
import com.digitalisyours.domain.port.in.MiniQuizApprenantUseCase;
import com.digitalisyours.domain.port.out.MiniQuizApprenantRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MiniQuizApprenantService implements MiniQuizApprenantUseCase {
    private final MiniQuizApprenantRepositoryPort repositoryPort;

    // ══════════════════════════════════════════════════════
    // RÉCUPÉRER LE QUIZ (sans révéler les bonnes réponses)
    // ══════════════════════════════════════════════════════

    @Override
    public Optional<Quiz> getMiniQuiz(String email, Long formationId, Long coursId) {
        verifierAcces(email, formationId, coursId);

        Optional<Quiz> quizOpt = repositoryPort.findMiniQuizByCoursId(coursId);

        // ── Masquer estCorrecte avant d'envoyer au frontend ──
        quizOpt.ifPresent(quiz -> {
            if (quiz.getQuestions() != null) {
                for (Question q : quiz.getQuestions()) {
                    if (q.getOptions() != null) {
                        for (OptionQuestion opt : q.getOptions()) {
                            opt.setEstCorrecte(null); // ← sécurité : ne pas exposer la bonne réponse
                        }
                    }
                }
            }
        });

        return quizOpt;
    }

    // ══════════════════════════════════════════════════════
    // SOUMETTRE ET CORRIGER
    // ══════════════════════════════════════════════════════

    @Override
    public ResultatMiniQuiz soumettre(SoumissionMiniQuiz soumission) {

        verifierAcces(soumission.getEmail(), soumission.getFormationId(), soumission.getCoursId());

        // Récupérer le quiz complet AVEC estCorrecte (usage interne)
        Quiz quiz = repositoryPort.findMiniQuizByCoursId(soumission.getCoursId())
                .orElseThrow(() -> new RuntimeException(
                        "Aucun mini-quiz trouvé pour le cours " + soumission.getCoursId()));

        Map<Long, Long> reponsesApprenant = soumission.getReponses();
        if (reponsesApprenant == null) reponsesApprenant = Map.of();

        // ── Corriger chaque question ──────────────────────────────
        List<ResultatMiniQuiz.ReponseDetail> details = new ArrayList<>();
        int bonnesReponses = 0;
        int totalQuestions = quiz.getQuestions() != null ? quiz.getQuestions().size() : 0;

        if (quiz.getQuestions() != null) {
            for (Question question : quiz.getQuestions()) {

                Long optionChoisieId = reponsesApprenant.get(question.getId());

                // Trouver la bonne réponse
                OptionQuestion bonneOption = trouverBonneReponse(question);

                // Trouver l'option choisie par l'apprenant
                OptionQuestion optionChoisie = trouverOption(question, optionChoisieId);

                boolean estCorrecte = optionChoisie != null
                        && Boolean.TRUE.equals(optionChoisie.getEstCorrecte());

                if (estCorrecte) bonnesReponses++;

                details.add(ResultatMiniQuiz.ReponseDetail.builder()
                        .questionId(question.getId())
                        .questionTexte(question.getTexte())
                        .optionChoisieId(optionChoisie != null ? optionChoisie.getId()    : null)
                        .optionChoisieTexte(optionChoisie != null ? optionChoisie.getTexte() : null)
                        .estCorrecte(estCorrecte)
                        .explication(question.getExplication())
                        .bonneReponseTexte(bonneOption != null ? bonneOption.getTexte() : null)
                        .build());
            }

        }

        // ── Calculer le score ─────────────────────────────────────
        float score = totalQuestions > 0
                ? Math.round((float) bonnesReponses / totalQuestions * 100.0f)
                : 0f;

        float notePassage = quiz.getNotePassage() != null ? quiz.getNotePassage() : 70f;
        boolean reussi    = score >= notePassage;

        log.info("Mini-quiz corrigé : apprenant={} cours={} score={}% reussi={}",
                soumission.getEmail(), soumission.getCoursId(), score, reussi);

        return ResultatMiniQuiz.builder()
                .quizId(quiz.getId())
                .coursId(soumission.getCoursId())
                .formationId(soumission.getFormationId())
                .score(score)
                .nombreBonnesReponses(bonnesReponses)
                .nombreQuestions(totalQuestions)
                .tempsPasse(soumission.getTempsPasse())
                .reussi(reussi)
                .notePassage(notePassage)
                .datePassage(LocalDateTime.now())
                .reponses(details)
                .build();
    }

    // ══════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════

    private void verifierAcces(String email, Long formationId, Long coursId) {
        if (!repositoryPort.estInscritEtPaye(email, formationId)) {
            throw new SecurityException("Vous n'êtes pas inscrit à cette formation.");
        }
        if (!repositoryPort.coursAppartientAFormation(coursId, formationId)) {
            throw new RuntimeException("Cours non trouvé dans cette formation.");
        }
    }

    private OptionQuestion trouverBonneReponse(Question question) {
        if (question.getOptions() == null) return null;
        return question.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getEstCorrecte()))
                .findFirst()
                .orElse(null);
    }

    private OptionQuestion trouverOption(Question question, Long optionId) {
        if (question.getOptions() == null || optionId == null) return null;
        return question.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElse(null);
    }
}
