package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.AnalyseRisque;
import com.digitalisyours.domain.port.in.RisqueAbandonUseCase;
import com.digitalisyours.domain.port.out.DeepSeekRisqueAnalysePort;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RisqueAbandonService implements RisqueAbandonUseCase {

    private final DeepSeekRisqueAnalysePort            deepSeekPort;
    private final AnalyseRisqueJpaRepository           analyseRepo;
    private final InscriptionJpaRepository             inscriptionRepo;
    private final ApprenantJpaRepository               apprenantRepo;
    private final ProgressionCoursJpaRepository        progressionRepo;
    private final ResultatMiniQuizJpaRepository        miniQuizRepo;
    private final NotificationJpaRepository            notifRepo;
    private final UserJpaRepository                    userRepo;
    private final RisqueEmailService                   risqueEmailService;
    private final ConsulterCoursFormationJpaRepository coursRepository;

    // ═══════════════════════════════════════════════════════
    // SCHEDULER — toutes les 6h : 2h, 8h, 14h, 20h
    // ═══════════════════════════════════════════════════════

    @Scheduled(cron = "0 0 2,8,14,20 * * *")
    @Transactional
    public void analyserTousLesApprenants() {
        log.info("═══ Démarrage analyse risque abandon ═══");

        List<InscriptionEntity> inscriptions =
                inscriptionRepo.findAllPayeesAvecApprenantEtFormation();

        log.info("Inscriptions actives à analyser : {}", inscriptions.size());

        int analysees = 0;
        for (InscriptionEntity inscription : inscriptions) {
            try {
                analyserInscriptionInterne(inscription);
                analysees++;
            } catch (Exception e) {
                log.error("Erreur analyse apprenant {} formation {} : {}",
                        inscription.getApprenant().getEmail(),
                        inscription.getFormation().getId(),
                        e.getMessage(), e);
            }
        }
        log.info("═══ Analyse terminée : {}/{} analysés ═══",
                analysees, inscriptions.size());
    }

    // Lance l'analyse 2 minutes après le démarrage
    @Scheduled(initialDelay = 120_000, fixedDelay = Long.MAX_VALUE)
    @Transactional
    public void analyserAuDemarrage() {
        log.info("═══ Analyse risque au démarrage ═══");
        analyserTousLesApprenants();
    }

    // ═══════════════════════════════════════════════════════
    // ANALYSE D'UNE INSCRIPTION
    // ═══════════════════════════════════════════════════════

    private void analyserInscriptionInterne(InscriptionEntity inscription) {

        ApprenantEntity apprenant      = inscription.getApprenant();
        Long            formationId    = inscription.getFormation().getId();
        String          email          = apprenant.getEmail();
        String          formationTitre = inscription.getFormation().getTitre();

        // ── 1. Jours d'inactivité depuis progression_cours ───────
        int joursInactivite = calculerJoursInactivite(inscription, apprenant);

        // ── 2. Progression RÉELLE depuis progression_cours ───────
        float progression = calculerProgressionReelle(email, formationId);

        // ── 3. Vidéos + docs + quiz ──────────────────────────────
        int videosVues       = progressionRepo.countVideosVues(email, formationId);
        int documentsOuverts = progressionRepo.countDocumentsOuverts(email, formationId);
        int quizPasses       = progressionRepo.countQuizPasses(email, formationId);

        // ── 4. Score moyen mini-quiz ─────────────────────────────
        // IMPORTANT : isPresent() sans > 0
        // car score=0% est un vrai score valide (pas un fallback)
        float scoreMoyen;
        Optional<Double> scoreBD =
                miniQuizRepo.findScoreMoyenByEmailAndFormation(email, formationId);

        if (scoreBD.isPresent()) {
            // Score réel depuis resultats_mini_quiz (même si 0%)
            scoreMoyen = scoreBD.get().floatValue();
            log.debug("Score quiz depuis BD : {}%", (int) scoreMoyen);
        } else {
            // Aucun résultat en BD → pas encore passé → neutre
            scoreMoyen = 60f;
            log.debug("Aucun résultat quiz en BD → score neutre 60%");
        }

        log.info("Données [{} / formation={}] : prog={}% | inact={}j | " +
                        "quiz={}% | quizPassés={} | vidéos={}",
                email, formationId,
                (int) progression, joursInactivite,
                (int) scoreMoyen, quizPasses, videosVues);

        // ── 5. Construire le contexte ────────────────────────────
        AnalyseRisque contexte = AnalyseRisque.builder()
                .apprenantId(apprenant.getId())
                .apprenantEmail(email)
                .apprenantPrenom(apprenant.getPrenom())
                .apprenantNom(apprenant.getNom())
                .formationId(formationId)
                .formationTitre(formationTitre)
                .joursInactivite(joursInactivite)
                .progression(progression)
                .scoreMoyenQuiz(scoreMoyen)
                .nbQuizPasses(quizPasses)
                .nbVideosVues(videosVues)
                .nbDocumentsOuverts(documentsOuverts)
                .build();

        // ── 6. Appeler Mistral (ou fallback local) ───────────────
        AnalyseRisque resultat = deepSeekPort.analyserRisque(contexte);

        log.info("Résultat : niveau={} score={} | {}",
                resultat.getNiveauRisque(),
                resultat.getScoreRisque() != null
                        ? resultat.getScoreRisque().intValue() : 0,
                resultat.getExplication());

        // ── 7. Persister l'analyse ───────────────────────────────
        AnalyseRisqueEntity entity = AnalyseRisqueEntity.builder()
                .apprenantId(apprenant.getId())
                .apprenantEmail(email)
                .formationId(formationId)
                .formationTitre(formationTitre)
                .niveauRisque(resultat.getNiveauRisque())
                .scoreRisque(resultat.getScoreRisque())
                .joursInactivite(joursInactivite)
                .progression(progression)
                .scoreMoyenQuiz(scoreMoyen)
                .nbQuizPasses(quizPasses)
                .nbVideosVues(videosVues)
                .nbDocumentsOuverts(documentsOuverts)
                .explication(resultat.getExplication())
                .recommandationIA(resultat.getRecommandationIA())
                .notificationEnvoyee(false)
                .emailEnvoye(false)
                .dateAnalyse(LocalDateTime.now())
                .build();

        AnalyseRisqueEntity saved = analyseRepo.save(entity);

        // ── 8. Notifier TOUS les niveaux (anti-spam 24h) ─────────
        boolean dejaNotifie = analyseRepo.existsNotificationRecentePourApprenant(
                apprenant.getId(),
                formationId,
                LocalDateTime.now().minusHours(24)
        );

        if (!dejaNotifie) {
            envoyerNotificationEtEmail(apprenant, saved);
        } else {
            log.info("Déjà notifié dans les 24h : {} formation={}",
                    email, formationId);
        }
    }

    // ═══════════════════════════════════════════════════════
    // CALCUL PROGRESSION RÉELLE depuis progression_cours
    // ═══════════════════════════════════════════════════════

    private float calculerProgressionReelle(String email, Long formationId) {
        try {
            int totalCours =
                    coursRepository.findCoursPubiesByFormationId(formationId).size();
            if (totalCours == 0) return 0f;

            int videosVues       = progressionRepo.countVideosVues(email, formationId);
            int documentsOuverts = progressionRepo.countDocumentsOuverts(email, formationId);
            int quizPasses       = progressionRepo.countQuizPasses(email, formationId);

            double prog =
                    ((double) videosVues       / totalCours * 50)
                            + ((double) documentsOuverts / totalCours * 20)
                            + ((double) quizPasses       / totalCours * 30);

            float result = (float) Math.min(
                    Math.round(prog * 10.0) / 10.0, 100.0);

            log.debug("Progression réelle {} formation={} : {}% " +
                            "(vidéos={} docs={} quiz={} / total={})",
                    email, formationId, result,
                    videosVues, documentsOuverts, quizPasses, totalCours);

            return result;

        } catch (Exception e) {
            log.warn("Erreur calcul progression réelle pour {} : {}",
                    email, e.getMessage());
            return 0f;
        }
    }

    // ═══════════════════════════════════════════════════════
    // CALCUL JOURS D'INACTIVITÉ
    // ═══════════════════════════════════════════════════════

    private int calculerJoursInactivite(InscriptionEntity inscription,
                                        ApprenantEntity apprenant) {

        Long formationId = inscription.getFormation().getId();
        Long apprenantId = apprenant.getId();

        // Priorité 1 : progression_cours
        try {
            LocalDateTime derniereDate =
                    progressionRepo.findDerniereActiviteParFormation(
                            apprenantId, formationId);
            if (derniereDate != null) {
                int jours = (int) ChronoUnit.DAYS.between(
                        derniereDate, LocalDateTime.now());
                log.debug("Inactivité depuis progression_cours : {}j ({})",
                        jours, apprenant.getEmail());
                return Math.max(0, jours);
            }
        } catch (Exception e) {
            log.warn("Erreur calcul inactivité : {}", e.getMessage());
        }

        // Priorité 2 : dernier_activite
        if (inscription.getDernierActivite() != null) {
            return Math.max(0, (int) ChronoUnit.DAYS.between(
                    inscription.getDernierActivite(), LocalDateTime.now()));
        }

        // Priorité 3 : dernière connexion
        if (apprenant.getDerniereConnexion() != null) {
            return Math.max(0, (int) ChronoUnit.DAYS.between(
                    apprenant.getDerniereConnexion(), LocalDateTime.now()));
        }

        // Priorité 4 : date d'inscription
        if (inscription.getDateInscription() != null) {
            return Math.max(0, (int) ChronoUnit.DAYS.between(
                    inscription.getDateInscription(), LocalDateTime.now()));
        }

        return 0;
    }

    // ═══════════════════════════════════════════════════════
    // NOTIFICATION + EMAIL — TOUS les niveaux
    // ═══════════════════════════════════════════════════════

    private void envoyerNotificationEtEmail(ApprenantEntity apprenant,
                                            AnalyseRisqueEntity analyse) {
        try {
            UserEntity user = userRepo.findById(apprenant.getId()).orElse(null);
            if (user == null) {
                log.warn("UserEntity non trouvé id={}", apprenant.getId());
                return;
            }

            String emoji = switch (analyse.getNiveauRisque()) {
                case "ELEVE"  -> "🚨";
                case "MOYEN"  -> "⚠️";
                default       -> "✅";
            };

            String titre = switch (analyse.getNiveauRisque()) {
                case "ELEVE"  -> emoji + " Reprenez votre formation !";
                case "MOYEN"  -> emoji + " Continuez vos efforts !";
                default       -> emoji + " Excellent travail, continuez !";
            };

            String message = switch (analyse.getNiveauRisque()) {
                case "ELEVE"  ->
                        "Votre formation vous attend. " +
                                (analyse.getRecommandationIA() != null
                                        ? analyse.getRecommandationIA() : "Revenez dès maintenant !");
                case "MOYEN"  ->
                        "Quelques efforts et vous y êtes. " +
                                (analyse.getRecommandationIA() != null
                                        ? analyse.getRecommandationIA() : "Restez régulier !");
                default       ->
                        analyse.getRecommandationIA() != null
                                ? analyse.getRecommandationIA()
                                : "Continuez sur cette lancée !";
            };

            // Notification in-app
            NotificationEntity notif = NotificationEntity.builder()
                    .user(user)
                    .type("MOTIVATION")
                    .titre(titre)
                    .message(message)
                    .formationId(analyse.getFormationId())
                    .build();

            notifRepo.save(notif);
            analyse.setNotificationEnvoyee(true);
            analyseRepo.save(analyse);

            log.info("✓ Notification {} → {}", analyse.getNiveauRisque(),
                    apprenant.getEmail());

            // Email
            if (!analyse.isEmailEnvoye()) {
                try {
                    risqueEmailService.envoyerEmailMotivation(apprenant, analyse);
                    analyse.setEmailEnvoye(true);
                    analyseRepo.save(analyse);
                    log.info("✓ Email {} → {}", analyse.getNiveauRisque(),
                            apprenant.getEmail());
                } catch (Exception e) {
                    log.error("Erreur email : {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Erreur notification/email {} : {}",
                    apprenant.getEmail(), e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════
    // USE CASE — MÉTHODES PUBLIQUES
    // ═══════════════════════════════════════════════════════

    @Override
    @Transactional
    public AnalyseRisque analyserApprenant(Long apprenantId, Long formationId) {
        InscriptionEntity inscription = inscriptionRepo
                .findByApprenantIdAndFormationId(apprenantId, formationId)
                .orElseThrow(() -> new RuntimeException(
                        "Inscription introuvable : apprenant="
                                + apprenantId + " formation=" + formationId));

        analyserInscriptionInterne(inscription);

        return analyseRepo
                .findLastByApprenantAndFormation(apprenantId, formationId)
                .map(this::toDomain)
                .orElseThrow(() -> new RuntimeException(
                        "Analyse non trouvée après calcul"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyseRisque> getMesAnalyses(String email) {
        ApprenantEntity apprenant = apprenantRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Apprenant introuvable : " + email));

        List<AnalyseRisqueEntity> toutes =
                analyseRepo.findByApprenantId(apprenant.getId());

        // Garder UNIQUEMENT la dernière par formation
        Map<Long, AnalyseRisqueEntity> parFormation = new LinkedHashMap<>();
        for (AnalyseRisqueEntity a : toutes) {
            AnalyseRisqueEntity existing = parFormation.get(a.getFormationId());
            if (existing == null ||
                    a.getDateAnalyse().isAfter(existing.getDateAnalyse())) {
                parFormation.put(a.getFormationId(), a);
            }
        }

        return parFormation.values().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyseRisque getDerniereAnalyse(String email, Long formationId) {
        ApprenantEntity apprenant = apprenantRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Apprenant introuvable : " + email));
        return analyseRepo
                .findLastByApprenantAndFormation(apprenant.getId(), formationId)
                .map(this::toDomain)
                .orElse(null);
    }

    // ═══════════════════════════════════════════════════════
    // MAPPING Entity → Domain
    // ═══════════════════════════════════════════════════════

    private AnalyseRisque toDomain(AnalyseRisqueEntity e) {
        return AnalyseRisque.builder()
                .id(e.getId())
                .apprenantId(e.getApprenantId())
                .apprenantEmail(e.getApprenantEmail())
                .formationId(e.getFormationId())
                .formationTitre(e.getFormationTitre())
                .niveauRisque(e.getNiveauRisque())
                .scoreRisque(e.getScoreRisque())
                .joursInactivite(e.getJoursInactivite())
                .progression(e.getProgression())
                .scoreMoyenQuiz(e.getScoreMoyenQuiz())
                .nbQuizPasses(e.getNbQuizPasses())
                .nbVideosVues(e.getNbVideosVues())
                .nbDocumentsOuverts(e.getNbDocumentsOuverts())
                .explication(e.getExplication())
                .recommandationIA(e.getRecommandationIA())
                .notificationEnvoyee(e.isNotificationEnvoyee())
                .emailEnvoye(e.isEmailEnvoye())
                .dateAnalyse(e.getDateAnalyse())
                .build();
    }
}