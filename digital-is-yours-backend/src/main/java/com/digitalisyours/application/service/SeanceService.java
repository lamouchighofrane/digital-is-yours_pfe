package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.SeanceEnLigne;
import com.digitalisyours.domain.port.in.SeanceUseCase;
import com.digitalisyours.domain.port.out.*;
import com.digitalisyours.infrastructure.persistence.entity.InscriptionEntity;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeanceService implements SeanceUseCase {

    private final SeanceRepositoryPort seanceRepository;
    private final ProfilApprenantRepositoryPort apprenantRepository;
    private final NotificationRepositoryPort notificationRepository;
    private final FormationRepositoryPort formationRepository;
    private final SeanceEmailService emailService;
    private final InscriptionJpaRepository inscriptionJpaRepository;
    private final UserJpaRepository userJpaRepository;

    private static final String JITSI_BASE = "https://meet.jit.si/";

    @Override
    public SeanceEnLigne creerSeance(String emailFormateur, Map<String, Object> payload) {
        // 1. Récupérer l'ID du formateur
        Long formateurId = userJpaRepository.findByEmail(emailFormateur)
                .orElseThrow(() -> new RuntimeException("Formateur non trouvé"))
                .getId();

        Long formationId = Long.valueOf(payload.get("formationId").toString());
        String titre = (String) payload.get("titre");
        LocalDateTime dateSeance = LocalDateTime.parse(
                payload.get("dateSeance").toString(),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Integer dureeMinutes = payload.get("dureeMinutes") != null
                ? Integer.valueOf(payload.get("dureeMinutes").toString()) : 60;
        String description = (String) payload.get("description");

        // 2. Générer un nom de salle unique et sécurisé
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String dateStr = dateSeance.format(DateTimeFormatter.ofPattern("ddMMyyyy-HHmm"));
        String roomName = "DIY-F" + formationId + "-" + dateStr + "-" + suffix;
        String lienJitsi = JITSI_BASE + roomName;

        // 3. Récupérer les infos formation
        var formation = formationRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        // 4. Sauvegarder la séance
        SeanceEnLigne seance = SeanceEnLigne.builder()
                .formationId(formationId)
                .formationTitre(formation.getTitre())
                .formateurId(formateurId)
                .titre(titre)
                .dateSeance(dateSeance)
                .dureeMinutes(dureeMinutes)
                .description(description)
                .lienJitsi(lienJitsi)
                .roomName(roomName)
                .statut("PLANIFIEE")
                .notifEnvoyee(false)
                .dateCreation(LocalDateTime.now())
                .build();

        seance = seanceRepository.save(seance);
        final SeanceEnLigne seanceFinal = seance;

        // 5. Notifier tous les apprenants inscrits à cette formation
        notifierApprenants(seanceFinal);

        return seanceFinal;
    }

    private void notifierApprenants(SeanceEnLigne seance) {
        // Trouver toutes les inscriptions PAYÉES pour cette formation
        List<InscriptionEntity> inscriptions = inscriptionJpaRepository
                .findAllPayeesAvecApprenantEtFormation()
                .stream()
                .filter(i -> i.getFormation().getId().equals(seance.getFormationId()))
                .toList();

        String dateFormatee = seance.getDateSeance()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", java.util.Locale.FRENCH));

        for (InscriptionEntity insc : inscriptions) {
            try {
                String emailApprenant = insc.getApprenant().getEmail();
                String prenomApprenant = insc.getApprenant().getPrenom() != null
                        ? insc.getApprenant().getPrenom() : "Apprenant";

                // a) Email
                emailService.envoyerInvitation(emailApprenant, prenomApprenant, seance);

                // b) Notification in-app
                com.digitalisyours.infrastructure.persistence.entity.NotificationEntity notif =
                        com.digitalisyours.infrastructure.persistence.entity.NotificationEntity.builder()
                                .user(insc.getApprenant())
                                .type("SEANCE_EN_LIGNE")
                                .titre("📹 Séance en ligne — " + seance.getFormationTitre())
                                .message("Une séance en ligne est planifiée le " + dateFormatee
                                        + " pour la formation \"" + seance.getFormationTitre() + "\". "
                                        + "Cliquez pour rejoindre : " + seance.getLienJitsi())
                                .formationId(seance.getFormationId())
                                .formationTitre(seance.getFormationTitre())
                                .build();

                // Accéder au NotificationJpaRepository via l'adapter est plus propre
                // On va utiliser le port directement
                saveNotificationDirectly(notif);

                log.info("Apprenant notifié : {}", emailApprenant);
            } catch (Exception e) {
                log.error("Erreur notification apprenant {} : {}",
                        insc.getApprenant().getEmail(), e.getMessage());
            }
        }

        // Mettre à jour notifEnvoyee
        seance.setNotifEnvoyee(true);
        seanceRepository.save(seance);
    }

    // Injection directe pour les notifications (évite une dépendance circulaire)
    private final com.digitalisyours.infrastructure.persistence.repository.NotificationJpaRepository notificationJpaRepository;

    private void saveNotificationDirectly(
            com.digitalisyours.infrastructure.persistence.entity.NotificationEntity notif) {
        notificationJpaRepository.save(notif);
    }

    @Override
    public List<SeanceEnLigne> getMesSeances(String emailFormateur) {
        Long formateurId = userJpaRepository.findByEmail(emailFormateur)
                .orElseThrow(() -> new RuntimeException("Formateur non trouvé"))
                .getId();
        return seanceRepository.findByFormateurId(formateurId);
    }

    @Override
    public List<SeanceEnLigne> getSeancesApprenant(String emailApprenant) {
        return seanceRepository.findSeancesForApprenant(emailApprenant);
    }

    @Override
    public SeanceEnLigne annulerSeance(Long seanceId, String emailFormateur) {
        SeanceEnLigne seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new RuntimeException("Séance non trouvée"));
        seance.setStatut("ANNULEE");
        return seanceRepository.save(seance);
    }

    @Override
    public void supprimerSeance(Long seanceId, String emailFormateur) {
        seanceRepository.deleteById(seanceId);
    }
}