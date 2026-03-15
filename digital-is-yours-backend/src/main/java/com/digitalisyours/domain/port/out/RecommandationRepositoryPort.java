package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.RecommandationIA;

import java.util.List;
import java.util.Optional;

public interface RecommandationRepositoryPort {
    /**
     * Récupère le profil complet de l'apprenant (niveau, domaines, disponibilités, objectifs).
     */
    Optional<Apprenant> findApprenantByEmail(String email);

    /**
     * Récupère toutes les formations publiées où l'apprenant n'est PAS encore inscrit.
     */
    List<RecommandationIA> findFormationsDisponibles(String emailApprenant);
}
