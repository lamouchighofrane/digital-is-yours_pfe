package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.port.out.FormateurRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FormateurRepositoryAdapter implements FormateurRepositoryPort {
    private final FormationJpaRepository formationJpaRepository;

    @Override
    public List<Formation> findFormationsByFormateurEmail(String email) {
        return formationJpaRepository.findAllWithCategorie().stream()
                .filter(f -> f.getFormateur() != null
                        && f.getFormateur().getEmail().equals(email)
                        && "PUBLIE".equals(f.getStatut()))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private Formation toDomain(FormationEntity e) {
        Formation f = Formation.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .description(e.getDescription())
                .imageCouverture(e.getImageCouverture())
                .dureeEstimee(e.getDureeEstimee())
                .niveau(e.getNiveau())
                .statut(e.getStatut())
                .dateCreation(e.getDateCreation())
                .nombreInscrits(e.getNombreInscrits())
                .nombreCertifies(e.getNombreCertifies())
                .noteMoyenne(e.getNoteMoyenne())
                .tauxReussite(e.getTauxReussite())
                .build();
        if (e.getCategorie() != null) {
            f.setCategorieId(e.getCategorie().getId());
            f.setCategorieNom(e.getCategorie().getNom());
        }
        return f;
    }
}
