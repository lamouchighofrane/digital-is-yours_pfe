package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Competence;

import java.util.List;
import java.util.Optional;

public interface CompetenceRepositoryPort {
    List<Competence> findAllOrderByNom();
    List<String> findAllCategories();
    List<Competence> findByFormationId(Long formationId);
    Optional<Competence> findById(Long id);
    boolean existsById(Long id);
    boolean existsByNom(String nom);
    Competence save(Competence competence);
    void deleteById(Long id);
    void deleteFormationLinks(Long competenceId);
    void associerCompetencesFormation(Long formationId, List<Long> competenceIds);
}
