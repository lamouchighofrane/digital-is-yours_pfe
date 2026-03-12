package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Cours;

import java.util.List;
import java.util.Optional;

public interface CoursRepositoryPort {
    List<Cours> findByFormationIdOrderByOrdre(Long formationId);
    Optional<Cours> findById(Long coursId);
    Optional<Cours> findByIdAndFormationId(Long coursId, Long formationId);
    Integer findMaxOrdreByFormationId(Long formationId);
    Cours save(Cours cours);
    void deleteById(Long coursId);
    long countDocumentsByCours(Long coursId);

    // Vérification accès formateur
    boolean isFormateurOfFormation(Long formationId, String email);
    boolean formationExists(Long formationId);
}
