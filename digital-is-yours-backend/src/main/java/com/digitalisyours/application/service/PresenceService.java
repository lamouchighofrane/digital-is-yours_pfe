package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.PresenceSeance;
import com.digitalisyours.domain.model.SeanceEnLigne;
import com.digitalisyours.domain.port.in.PresenceUseCase;
import com.digitalisyours.domain.port.out.PresenceRepositoryPort;
import com.digitalisyours.domain.port.out.SeanceRepositoryPort;
import com.digitalisyours.domain.port.out.UserRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.InscriptionEntity;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService implements PresenceUseCase {

    private final PresenceRepositoryPort presenceRepository;
    private final UserRepositoryPort userRepository;
    private final SeanceRepositoryPort seanceRepository;           // ← AJOUT
    private final InscriptionJpaRepository inscriptionJpaRepository; // ← AJOUT

    @Override
    public void apprenantRejoindre(Long seanceId, String email) {
        Optional<PresenceSeance> existing =
                presenceRepository.findBySeanceIdAndEmail(seanceId, email);

        String nom    = "";
        String prenom = "";
        try {
            var user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                nom    = user.get().getNom()    != null ? user.get().getNom()    : "";
                prenom = user.get().getPrenom() != null ? user.get().getPrenom() : "";
            }
        } catch (Exception e) { /* ignorer */ }

        if (existing.isEmpty()) {
            PresenceSeance presence = PresenceSeance.builder()
                    .seanceId(seanceId)
                    .apprenantEmail(email)
                    .apprenantNom(nom)
                    .apprenantPrenom(prenom)
                    .dateRejoindre(ZonedDateTime.now(ZoneId.of("Africa/Tunis")).toLocalDateTime())
                    .present(true)
                    .build();
            presenceRepository.save(presence);
        } else {
            PresenceSeance p = existing.get();
            p.setPresent(true);
            p.setApprenantNom(nom);
            p.setApprenantPrenom(prenom);
            p.setDateRejoindre(ZonedDateTime.now(ZoneId.of("Africa/Tunis")).toLocalDateTime());
            p.setDateQuitter(null);
            p.setDureeMinutes(null);
            presenceRepository.save(p);
        }
    }

    @Override
    public void apprenantQuitter(Long seanceId, String email) {
        presenceRepository.findBySeanceIdAndEmail(seanceId, email)
                .ifPresent(p -> {
                    LocalDateTime maintenant = ZonedDateTime.now(ZoneId.of("Africa/Tunis")).toLocalDateTime();
                    p.setDateQuitter(maintenant);
                    p.setPresent(true);
                    if (p.getDateRejoindre() != null) {
                        long minutes = java.time.Duration
                                .between(p.getDateRejoindre(), maintenant)
                                .toMinutes();
                        p.setDureeMinutes((int) Math.max(1, minutes));
                    }
                    presenceRepository.save(p);
                    log.info("Départ enregistré : {} séance {}, durée {}min",
                            email, seanceId, p.getDureeMinutes());
                });
    }

    @Override
    public List<PresenceSeance> getPresences(Long seanceId) {
        // 1. Récupérer la séance pour avoir le formationId
        SeanceEnLigne seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new RuntimeException("Séance non trouvée"));

        // 2. Tous les inscrits payés à cette formation
        List<InscriptionEntity> inscrits = inscriptionJpaRepository
                .findAllPayeesAvecApprenantEtFormation()
                .stream()
                .filter(i -> i.getFormation().getId().equals(seance.getFormationId()))
                .toList();

        // 3. Présences existantes en base
        List<PresenceSeance> presencesExistantes = presenceRepository.findBySeanceId(seanceId);

        // 4. Pour chaque inscrit, trouver sa présence ou créer un absent
        return inscrits.stream().map(insc -> {
            String email = insc.getApprenant().getEmail();
            return presencesExistantes.stream()
                    .filter(p -> p.getApprenantEmail().equals(email))
                    .findFirst()
                    .orElse(PresenceSeance.builder()
                            .seanceId(seanceId)
                            .apprenantEmail(email)
                            .apprenantNom(insc.getApprenant().getNom() != null
                                    ? insc.getApprenant().getNom() : "")
                            .apprenantPrenom(insc.getApprenant().getPrenom() != null
                                    ? insc.getApprenant().getPrenom() : "")
                            .present(false)
                            .dateRejoindre(null)
                            .dateQuitter(null)
                            .dureeMinutes(null)
                            .build());
        }).toList();
    }
}