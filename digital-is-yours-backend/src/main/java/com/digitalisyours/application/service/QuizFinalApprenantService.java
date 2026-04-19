package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.*;
import com.digitalisyours.domain.port.in.CertificatUseCase;
import com.digitalisyours.domain.port.in.QuizFinalApprenantUseCase;
import com.digitalisyours.domain.port.out.QuizFinalApprenantRepositoryPort;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizFinalApprenantService implements QuizFinalApprenantUseCase {

    private final QuizFinalApprenantRepositoryPort repositoryPort;
    private final CertificatUseCase               certificatUseCase;
    private final CertificatEmailService           certificatEmailService;
    private final InscriptionJpaRepository         inscriptionJpaRepository;
    private final PortfolioService portfolioService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ══════════════════════════════════════════════════════════════
    // RÉCUPÉRER LES INFOS DU QUIZ FINAL (sans bonnes réponses)
    // ══════════════════════════════════════════════════════════════

    @Override
    public InfosQuizFinalApprenant getInfosQuizFinal(String email, Long formationId) {

        if (!repositoryPort.estInscritEtPaye(email, formationId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vous n'êtes pas inscrit à cette formation ou le paiement est en attente.");
        }

        Quiz quiz = repositoryPort.findQuizFinalByFormationId(formationId).orElse(null);

        if (quiz == null) {
            return InfosQuizFinalApprenant.builder().existe(false).build();
        }

        long tentativesUtilisees = repositoryPort.countTentatives(email, quiz.getId());
        int  maxTentatives       = quiz.getNombreTentatives() != null ? quiz.getNombreTentatives() : 3;
        long tentativesRestantes = Math.max(0, maxTentatives - tentativesUtilisees);

        masquerBonnesReponses(quiz);

        InfosQuizFinalApprenant.DernierResultat dernierResultat =
                repositoryPort.findDernierResultat(email, quiz.getId()).orElse(null);

        return InfosQuizFinalApprenant.builder()
                .existe(true)
                .quizId(quiz.getId())
                .notePassage(quiz.getNotePassage() != null ? quiz.getNotePassage() : 75f)
                .nombreTentatives(maxTentatives)
                .tentativesUtilisees((int) tentativesUtilisees)
                .tentativesRestantes((int) tentativesRestantes)
                .dureeMinutes(quiz.getDureeMinutes() != null ? quiz.getDureeMinutes() : 20)
                .nbQuestions(quiz.getQuestions() != null ? quiz.getQuestions().size() : 0)
                .peutPasser(tentativesRestantes > 0)
                .quiz(quiz)
                .dernierResultat(dernierResultat)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // SOUMETTRE ET CORRIGER
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResultatQuizFinal soumettre(SoumissionQuizFinal soumission) {

        // 1. Vérifier inscription + paiement
        if (!repositoryPort.estInscritEtPaye(soumission.getEmail(), soumission.getFormationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé.");
        }

        // 2. Récupérer le quiz avec estCorrecte (usage interne uniquement)
        Quiz quiz = repositoryPort.findQuizFinalByFormationId(soumission.getFormationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucun quiz final trouvé pour cette formation."));

        // 3. Vérifier tentatives restantes
        long tentativesUtilisees = repositoryPort.countTentatives(
                soumission.getEmail(), quiz.getId());
        int maxTentatives = quiz.getNombreTentatives() != null ? quiz.getNombreTentatives() : 3;

        if (tentativesUtilisees >= maxTentatives) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vous avez épuisé toutes vos tentatives (" + maxTentatives + "/" + maxTentatives + ").");
        }

        // 4. Correction question par question
        Map<Long, Long> reponsesApprenant =
                soumission.getReponses() != null ? soumission.getReponses() : Map.of();

        List<ResultatQuizFinal.ReponseDetail> details = new ArrayList<>();
        int bonnesReponses = 0;
        int totalQuestions = quiz.getQuestions() != null ? quiz.getQuestions().size() : 0;

        if (quiz.getQuestions() != null) {
            for (Question question : quiz.getQuestions()) {
                Long optionChoisieId  = reponsesApprenant.get(question.getId());
                OptionQuestion bonneOption   = trouverBonneReponse(question);
                OptionQuestion optionChoisie = trouverOption(question, optionChoisieId);

                boolean estCorrecte = optionChoisie != null
                        && Boolean.TRUE.equals(optionChoisie.getEstCorrecte());

                if (estCorrecte) bonnesReponses++;

                details.add(ResultatQuizFinal.ReponseDetail.builder()
                        .questionId(question.getId())
                        .questionTexte(question.getTexte())
                        .optionChoisieId(optionChoisie   != null ? optionChoisie.getId()    : null)
                        .optionChoisieTexte(optionChoisie != null ? optionChoisie.getTexte() : null)
                        .estCorrecte(estCorrecte)
                        .explication(question.getExplication())
                        .bonneReponseTexte(bonneOption != null ? bonneOption.getTexte() : null)
                        .build());
            }
        }

        // 5. Calculer le score BRUT
        float scoreBrut = totalQuestions > 0
                ? Math.round((float) bonnesReponses / totalQuestions * 100.0f) : 0f;

        // ── 6. APPLIQUER LE MALUS ANTI-FRAUDE ────────────────────
        RapportFraude rapportFraude  = soumission.getRapportFraude();
        int malus                    = 0;
        int nbInfractions            = 0;
        boolean suspectFraude        = false;
        String detailInfractionsJson = null;

        if (rapportFraude != null) {
            malus         = rapportFraude.calculerMalus();
            nbInfractions = rapportFraude.getNombreInfractions();
            suspectFraude = rapportFraude.estSuspect();

            // Sérialiser le détail des infractions en JSON
            try {
                detailInfractionsJson = objectMapper.writeValueAsString(
                        rapportFraude.getInfractions());
            } catch (Exception e) {
                log.warn("Impossible de sérialiser les infractions : {}", e.getMessage());
            }

            if (suspectFraude) {
                log.warn("⚠️ Fraude détectée — apprenant={} formation={} infractions={} malus={}pts",
                        soumission.getEmail(), soumission.getFormationId(), nbInfractions, malus);
            }
        }

        // Score final = scoreBrut - malus (jamais en dessous de 0)
        float scoreApresmalus = Math.max(0f, scoreBrut - malus);

        // ─────────────────────────────────────────────────────────

        float   notePassage         = quiz.getNotePassage() != null ? quiz.getNotePassage() : 75f;
        boolean reussi              = scoreApresmalus >= notePassage;
        int     tentativeNumero     = (int) tentativesUtilisees + 1;
        int     tentativesRestantes = (int) Math.max(0, maxTentatives - tentativeNumero);

        log.info("QuizFinal corrigé : apprenant={} formation={} scoreBrut={}% malus={} scoreFinal={}% reussi={} tentative={}/{}",
                soumission.getEmail(), soumission.getFormationId(),
                scoreBrut, malus, scoreApresmalus, reussi, tentativeNumero, maxTentatives);

        // 7. Persister le résultat avec les données anti-fraude
        ResultatQuizFinal resultat = ResultatQuizFinal.builder()
                .apprenantEmail(soumission.getEmail())
                .quizId(quiz.getId())
                .formationId(soumission.getFormationId())
                .scoreBrut(scoreBrut)
                .penaliteAppliquee(malus)
                .score(scoreApresmalus)
                .nombreBonnesReponses(bonnesReponses)
                .nombreQuestions(totalQuestions)
                .tempsPasse(soumission.getTempsPasse())
                .reussi(reussi)
                .notePassage(notePassage)
                .tentativeNumero(tentativeNumero)
                .tentativesRestantes(tentativesRestantes)
                .datePassage(LocalDateTime.now())
                .nbInfractions(nbInfractions)
                .suspectFraude(suspectFraude)
                .detailInfractions(detailInfractionsJson)
                .reponses(details)
                .build();

        ResultatQuizFinal saved = repositoryPort.saveResultat(resultat);

        // 8. Générer le certificat + envoyer email si réussi (non bloquant)
        if (reussi) {
            try {
                Long apprenantId = repositoryPort.findApprenantIdByEmail(soumission.getEmail());
                Certificat cert = certificatUseCase.genererCertificat(
                        apprenantId,
                        soumission.getFormationId(),
                        quiz.getId(),
                        scoreApresmalus   // ← on utilise le score après malus
                );
                log.info("Certificat généré : apprenant={} formation={}",
                        soumission.getEmail(), soumission.getFormationId());
                // Génération automatique du portfolio (non bloquant)
                try {
                    portfolioService.genererOuMettreAJour(apprenantId);
                    log.info("Portfolio généré/mis à jour pour apprenant={}", apprenantId);
                } catch (Exception portfolioEx) {
                    log.error("Erreur génération portfolio (non bloquant) : {}", portfolioEx.getMessage());
                }

                inscriptionJpaRepository.updateStatutApprenant(
                        apprenantId,
                        soumission.getFormationId(),
                        "CERTIFIE"
                );

                if (cert != null) {
                    try {
                        certificatEmailService.envoyerCertificat(cert);
                        log.info("Email certificat envoyé automatiquement à {}",
                                soumission.getEmail());
                    } catch (Exception emailEx) {
                        log.error("Erreur envoi email certificat (non bloquant) : {}",
                                emailEx.getMessage());
                    }
                }

            } catch (Exception e) {
                log.error("Erreur génération certificat (non bloquant) : {}", e.getMessage());
            }
        }

        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════════

    private void masquerBonnesReponses(Quiz quiz) {
        if (quiz.getQuestions() == null) return;
        for (Question q : quiz.getQuestions()) {
            if (q.getOptions() == null) continue;
            for (OptionQuestion opt : q.getOptions()) {
                opt.setEstCorrecte(null);
            }
        }
    }

    private OptionQuestion trouverBonneReponse(Question question) {
        if (question.getOptions() == null) return null;
        return question.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getEstCorrecte()))
                .findFirst().orElse(null);
    }

    private OptionQuestion trouverOption(Question question, Long optionId) {
        if (question.getOptions() == null || optionId == null) return null;
        return question.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst().orElse(null);
    }
}