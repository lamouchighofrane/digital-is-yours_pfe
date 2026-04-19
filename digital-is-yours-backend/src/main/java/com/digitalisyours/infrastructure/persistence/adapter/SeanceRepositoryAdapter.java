package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.SeanceEnLigne;
import com.digitalisyours.domain.port.out.SeanceRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.SeanceEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.SeanceJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SeanceRepositoryAdapter implements SeanceRepositoryPort {

    private final SeanceJpaRepository seanceJpaRepository;
    private final FormationJpaRepository formationJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Override
    @Transactional
    public SeanceEnLigne save(SeanceEnLigne seance) {
        FormationEntity formation = formationJpaRepository.findById(seance.getFormationId())
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));
        UserEntity formateur = userJpaRepository.findById(seance.getFormateurId())
                .orElseThrow(() -> new RuntimeException("Formateur non trouvé"));

        SeanceEntity entity;
        if (seance.getId() != null) {
            entity = seanceJpaRepository.findById(seance.getId())
                    .orElse(new SeanceEntity());
        } else {
            entity = new SeanceEntity();
        }

        entity.setFormation(formation);
        entity.setFormateur(formateur);
        entity.setTitre(seance.getTitre());
        entity.setDateSeance(seance.getDateSeance());
        entity.setDureeMinutes(seance.getDureeMinutes());
        entity.setDescription(seance.getDescription());
        entity.setLienJitsi(seance.getLienJitsi());
        entity.setRoomName(seance.getRoomName());
        entity.setStatut(seance.getStatut());
        entity.setNotifEnvoyee(seance.getNotifEnvoyee());
        entity.setDateCreation(seance.getDateCreation());

        return toDomain(seanceJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeanceEnLigne> findById(Long id) {
        return seanceJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeanceEnLigne> findByFormateurId(Long formateurId) {
        return seanceJpaRepository.findByFormateurId(formateurId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeanceEnLigne> findByFormationId(Long formationId) {
        return seanceJpaRepository.findByFormationId(formationId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeanceEnLigne> findSeancesForApprenant(String email) {
        return seanceJpaRepository.findSeancesForApprenant(email)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        seanceJpaRepository.deleteById(id);
    }

    private SeanceEnLigne toDomain(SeanceEntity e) {
        SeanceEnLigne s = SeanceEnLigne.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .dateSeance(e.getDateSeance())
                .dureeMinutes(e.getDureeMinutes())
                .description(e.getDescription())
                .lienJitsi(e.getLienJitsi())
                .roomName(e.getRoomName())
                .statut(e.getStatut())
                .notifEnvoyee(e.getNotifEnvoyee())
                .dateCreation(e.getDateCreation())
                .build();
        try {
            if (e.getFormation() != null) {
                s.setFormationId(e.getFormation().getId());
                s.setFormationTitre(e.getFormation().getTitre());
            }
        } catch (Exception ex) {}
        try {
            if (e.getFormateur() != null) {
                s.setFormateurId(e.getFormateur().getId());
                s.setFormateurNom(e.getFormateur().getNom());
                s.setFormateurPrenom(e.getFormateur().getPrenom());
            }
        } catch (Exception ex) {}
        return s;
    }
}