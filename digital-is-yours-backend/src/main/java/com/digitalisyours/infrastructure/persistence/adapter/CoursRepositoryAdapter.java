package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Cours;
import com.digitalisyours.domain.port.out.CoursRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.DocumentJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CoursRepositoryAdapter implements CoursRepositoryPort {
    private final CoursJpaRepository coursJpaRepository;
    private final FormationJpaRepository formationJpaRepository;
    private final DocumentJpaRepository documentJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Cours> findByFormationIdOrderByOrdre(Long formationId) {
        return coursJpaRepository.findByFormationIdOrderByOrdre(formationId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cours> findById(Long coursId) {
        return coursJpaRepository.findById(coursId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cours> findByIdAndFormationId(Long coursId, Long formationId) {
        return coursJpaRepository.findById(coursId)
                .filter(c -> c.getFormation().getId().equals(formationId))
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findMaxOrdreByFormationId(Long formationId) {
        return coursJpaRepository.findMaxOrdreByFormationId(formationId);
    }

    @Override
    @Transactional
    public Cours save(Cours cours) {
        FormationEntity formation = formationJpaRepository.findById(cours.getFormationId())
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        CoursEntity entity;
        if (cours.getId() != null) {
            entity = coursJpaRepository.findById(cours.getId())
                    .orElse(new CoursEntity());
        } else {
            entity = new CoursEntity();
        }

        entity.setTitre(cours.getTitre());
        entity.setDescription(cours.getDescription());
        entity.setObjectifs(cours.getObjectifs());
        entity.setDureeEstimee(cours.getDureeEstimee());
        entity.setOrdre(cours.getOrdre());
        entity.setStatut(cours.getStatut());
        entity.setVideoType(cours.getVideoType());
        entity.setVideoUrl(cours.getVideoUrl());
        entity.setFormation(formation);

        return toDomain(coursJpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long coursId) {
        coursJpaRepository.deleteById(coursId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countDocumentsByCours(Long coursId) {
        return documentJpaRepository.countByCoursId(coursId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFormateurOfFormation(Long formationId, String email) {
        return formationJpaRepository.findById(formationId)
                .map(f -> f.getFormateur() != null
                        && f.getFormateur().getEmail().equals(email))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean formationExists(Long formationId) {
        return formationJpaRepository.existsById(formationId);
    }

    // ── Mapping ───────────────────────────────────────────────

    private Cours toDomain(CoursEntity e) {
        return Cours.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .description(e.getDescription())
                .objectifs(e.getObjectifs())
                .dureeEstimee(e.getDureeEstimee())
                .ordre(e.getOrdre())
                .statut(e.getStatut())
                .videoType(e.getVideoType())
                .videoUrl(e.getVideoUrl())
                .formationId(e.getFormation() != null ? e.getFormation().getId() : null)
                .formationTitre(e.getFormation() != null ? e.getFormation().getTitre() : null)
                .dateCreation(e.getDateCreation())
                .dateModification(e.getDateModification())
                .build();
    }
}
