package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Inscription;

import java.util.List;
import java.util.Optional;

public interface InscriptionRepositoryPort {
    List<Inscription> findByApprenantEmail(String email);

    Optional<Inscription> findByApprenantEmailAndFormationId(String email, Long formationId);

    Inscription save(Inscription inscription);

    boolean existsByApprenantEmailAndFormationIdAndStatutPaiement(
            String email, Long formationId, String statut);
}