package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.PresenceSeance;
import com.digitalisyours.domain.port.out.PresenceRepositoryPort;

import com.digitalisyours.infrastructure.persistence.entity.PresenceSeanceEntity;
import com.digitalisyours.infrastructure.persistence.repository.PresenceSeanceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PresenceRepositoryAdapter implements PresenceRepositoryPort {

    private final PresenceSeanceJpaRepository jpaRepository;

    @Override
    public PresenceSeance save(PresenceSeance presence) {
        PresenceSeanceEntity entity = toEntity(presence);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<PresenceSeance> findBySeanceIdAndEmail(
            Long seanceId, String email) {
        return jpaRepository
                .findBySeanceIdAndApprenantEmail(seanceId, email)
                .map(this::toDomain);
    }

    @Override
    public List<PresenceSeance> findBySeanceId(Long seanceId) {
        return jpaRepository.findBySeanceId(seanceId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // ── Mappers ──────────────────────────────────────────
    private PresenceSeanceEntity toEntity(PresenceSeance p) {
        return PresenceSeanceEntity.builder()
                .id(p.getId())
                .seanceId(p.getSeanceId())
                .apprenantEmail(p.getApprenantEmail())
                .apprenantNom(p.getApprenantNom())
                .apprenantPrenom(p.getApprenantPrenom())
                .dateRejoindre(p.getDateRejoindre())
                .dateQuitter(p.getDateQuitter())
                .present(p.isPresent())
                .dureeMinutes(p.getDureeMinutes())
                .build();
    }

    private PresenceSeance toDomain(PresenceSeanceEntity e) {
        return PresenceSeance.builder()
                .id(e.getId())
                .seanceId(e.getSeanceId())
                .apprenantEmail(e.getApprenantEmail())
                .apprenantNom(e.getApprenantNom())
                .apprenantPrenom(e.getApprenantPrenom())
                .dateRejoindre(e.getDateRejoindre())
                .dateQuitter(e.getDateQuitter())
                .present(e.isPresent())
                .dureeMinutes(e.getDureeMinutes())
                .build();
    }
}