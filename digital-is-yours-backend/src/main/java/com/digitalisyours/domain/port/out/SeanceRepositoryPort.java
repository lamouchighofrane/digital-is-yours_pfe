package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.SeanceEnLigne;
import java.util.List;
import java.util.Optional;

public interface SeanceRepositoryPort {
    SeanceEnLigne save(SeanceEnLigne seance);
    Optional<SeanceEnLigne> findById(Long id);
    List<SeanceEnLigne> findByFormateurId(Long formateurId);
    List<SeanceEnLigne> findByFormationId(Long formationId);
    List<SeanceEnLigne> findSeancesForApprenant(String email);
    void deleteById(Long id);
}