package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.RecommandationIA;
import com.digitalisyours.domain.port.out.RecommandationRepositoryPort;
import com.digitalisyours.infrastructure.persistence.repository.ApprenantJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommandationRepositoryAdapter implements RecommandationRepositoryPort {
    private final ApprenantJpaRepository apprenantRepository;
    private final FormationJpaRepository formationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Optional<Apprenant> findApprenantByEmail(String email) {
        return apprenantRepository.findByEmail(email).map(e -> {
            Apprenant a = new Apprenant();
            a.setId(e.getId());
            a.setPrenom(e.getPrenom());
            a.setNom(e.getNom());
            a.setEmail(e.getEmail());
            a.setBio(e.getBio());
            a.setNiveauActuel(e.getNiveauActuel());
            a.setObjectifsApprentissage(e.getObjectifsApprentissage());
            a.setDisponibilitesHeuresParSemaine(e.getDisponibilitesHeuresParSemaine());
            a.setDomainesInteret(parseJsonList(e.getDomainesInteret()));
            a.setDisponibilites(parseJsonList(e.getDisponibilites()));
            return a;
        });
    }

    @Override
    public List<RecommandationIA> findFormationsDisponibles(String emailApprenant) {
        // Récupérer toutes les formations publiées où l'apprenant n'est pas inscrit
        return formationRepository.findFormationsNonInscrites(emailApprenant)
                .stream()
                .map(f -> RecommandationIA.builder()
                        .formationId(f.getId())
                        .titre(f.getTitre())
                        .niveau(f.getNiveau() != null ? f.getNiveau() : "DEBUTANT")
                        .description(f.getDescription())
                        .imageCouverture(f.getImageCouverture())
                        .categorie(f.getCategorie() != null ? f.getCategorie().getNom() : null)
                        .dureeEstimee(f.getDureeEstimee())
                        .noteMoyenne(f.getNoteMoyenne())
                        .nombreInscrits(f.getNombreInscrits())
                        .scoreCompatibilite(0)
                        .raison("")
                        .pointsForts("")
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> parseJsonList(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<>() {}); // ← ici
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
