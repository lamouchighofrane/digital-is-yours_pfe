package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.*;
import com.digitalisyours.domain.port.in.MiniQuizApprenantUseCase;
import com.digitalisyours.domain.port.out.MiniQuizApprenantRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.ResultatMiniQuizEntity;
import com.digitalisyours.infrastructure.persistence.repository.ResultatMiniQuizJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ResultatMiniQuizJpaRepository   miniQuizResultatRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ══════════════════════════════════════════════════════════════
    // RÉCUPÉRER LE QUIZ (sans révéler les bonnes réponses)
    // ══════════════════════════════════════════════════════════════

    @Override
    public Optional<Quiz> getMiniQuiz(String email, Long formationId, Long coursId) {
        verifierAcces(email, formationId, coursId);

        Optional<Quiz> quizOpt = repositoryPort.findMiniQuizByCoursId(coursId);

        quizOpt.ifPresent(quiz -> {
            if (quiz.getQuestions() != null) {
                for (Question q : quiz.getQuestions()) {
                    if (q.getOptions() != null) {
                        for (OptionQuestion opt : q.getOptions()) {
                            opt.setEstCorrecte(null);
                        }
                    }
                }
            }
        });

        return quizOpt;
    }

    // ══════════════════════════════════════════════════════════════
    // SOUMETTRE ET CORRIGER
    // ══════════════════════════════════════════════════════════════

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
                OptionQuestion bonneOption   = trouverBonneReponse(question);
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

        // ── Calculer le score BRUT ────────────────────────────────
        float scoreBrut = totalQuestions > 0
                ? Math.round((float) bonnesReponses / totalQuestions * 100.0f)
                : 0f;

        // ── APPLIQUER LE MALUS ANTI-FRAUDE ────────────────────────
        RapportFraude rapportFraude  = soumission.getRapportFraude();
        int malus                    = 0;
        int nbInfractions            = 0;
        boolean suspectFraude        = false;
        String detailInfractionsJson = null;

        if (rapportFraude != null) {
            malus         = rapportFraude.calculerMalus();
            nbInfractions = rapportFraude.getNombreInfractions();
            suspectFraude = rapportFraude.estSuspect();

            try {
                detailInfractionsJson = objectMapper.writeValueAsString(
                        rapportFraude.getInfractions());
            } catch (Exception e) {
                log.warn("Impossible de sérialiser les infractions mini-quiz : {}", e.getMessage());
            }

            if (suspectFraude) {
                log.warn("⚠️ Fraude détectée (mini-quiz) — apprenant={} cours={} infractions={} malus={}pts",
                        soumission.getEmail(), soumission.getCoursId(), nbInfractions, malus);
            }
        }

        // Score final = scoreBrut - malus (jamais en dessous de 0)
        float scoreApresMalus = Math.max(0f, scoreBrut - malus);

        // ─────────────────────────────────────────────────────────

        float notePassage = quiz.getNotePassage() != null ? quiz.getNotePassage() : 70f;
        boolean reussi    = scoreApresMalus >= notePassage;

        log.info("Mini-quiz corrigé : apprenant={} cours={} scoreBrut={}% malus={} scoreFinal={}% reussi={}",
                soumission.getEmail(), soumission.getCoursId(),
                scoreBrut, malus, scoreApresMalus, reussi);

        // Persister le résultat mini-quiz avec données anti-fraude
        try {
            ResultatMiniQuizEntity entity = ResultatMiniQuizEntity.builder()
                    .apprenantEmail(soumission.getEmail())
                    .quizId(quiz.getId())
                    .coursId(soumission.getCoursId())
                    .formationId(soumission.getFormationId())
                    .scoreBrut(scoreBrut)
                    .penaliteAppliquee(malus)
                    .score(scoreApresMalus)
                    .nombreBonnesReponses(bonnesReponses)
                    .nombreQuestions(totalQuestions)
                    .tempsPasse(soumission.getTempsPasse())
                    .reussi(reussi)
                    .notePassage(notePassage)
                    .tentativeNumero(1)
                    .nbInfractions(nbInfractions)
                    .suspectFraude(suspectFraude)
                    .detailInfractions(detailInfractionsJson)
                    .build();
            miniQuizResultatRepository.save(entity);
        } catch (Exception e) {
            log.error("Erreur sauvegarde résultat mini-quiz (non bloquant) : {}", e.getMessage());
        }

        return ResultatMiniQuiz.builder()
                .quizId(quiz.getId())
                .coursId(soumission.getCoursId())
                .formationId(soumission.getFormationId())
                .scoreBrut(scoreBrut)
                .penaliteAppliquee(malus)
                .score(scoreApresMalus)
                .nombreBonnesReponses(bonnesReponses)
                .nombreQuestions(totalQuestions)
                .tempsPasse(soumission.getTempsPasse())
                .reussi(reussi)
                .notePassage(notePassage)
                .nbInfractions(nbInfractions)
                .suspectFraude(suspectFraude)
                .datePassage(LocalDateTime.now())
                .reponses(details)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════════

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