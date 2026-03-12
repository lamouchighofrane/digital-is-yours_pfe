package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Competence;
import com.digitalisyours.domain.port.out.CompetenceRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CompetenceEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.repository.CompetenceJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompetenceRepositoryAdapter implements CompetenceRepositoryPort {
    private final CompetenceJpaRepository competenceJpaRepository;
    private final FormationJpaRepository formationJpaRepository;

    @Override
    public List<Competence> findAllOrderByNom() {
        return competenceJpaRepository.findAllByOrderByNomAsc()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<String> findAllCategories() {
        return competenceJpaRepository.findAllCategories();
    }

    @Override
    public List<Competence> findByFormationId(Long formationId) {
        return competenceJpaRepository.findByFormationId(formationId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Competence> findById(Long id) {
        return competenceJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return competenceJpaRepository.existsById(id);
    }

    @Override
    public boolean existsByNom(String nom) {
        return competenceJpaRepository.existsByNom(nom);
    }

    @Override
    public Competence save(Competence competence) {
        CompetenceEntity entity = toEntity(competence);
        return toDomain(competenceJpaRepository.save(entity));
    }

    @Override
    public void deleteById(Long id) {
        competenceJpaRepository.deleteById(id);
    }

    @Override
    public void deleteFormationLinks(Long competenceId) {
        competenceJpaRepository.deleteFormationLinks(competenceId);
    }

    @Override
    @Transactional
    public void associerCompetencesFormation(Long formationId, List<Long> competenceIds) {
        FormationEntity formation = formationJpaRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        Set<CompetenceEntity> competences = new HashSet<>();
        if (competenceIds != null) {
            competences = competenceIds.stream()
                    .map(cid -> competenceJpaRepository.findById(cid)
                            .orElseThrow(() -> new RuntimeException("Compétence introuvable : " + cid)))
                    .collect(Collectors.toSet());
        }
        formation.setCompetences(competences);
        formationJpaRepository.save(formation);
    }

    // ── Mapping ──────────────────────────────────────────────

    private Competence toDomain(CompetenceEntity e) {
        return Competence.builder()
                .id(e.getId())
                .nom(e.getNom())
                .categorie(e.getCategorie())
                .build();
    }

    private CompetenceEntity toEntity(Competence d) {
        return CompetenceEntity.builder()
                .id(d.getId())
                .nom(d.getNom())
                .categorie(d.getCategorie())
                .build();
    }
}
