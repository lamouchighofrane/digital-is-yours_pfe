package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.ListeCoursFormation;

public interface ConsulterCoursFormationUseCase {
    /**
     * Retourne la liste des cours publiés d'une formation
     * ainsi que les infos du quiz final, si l'apprenant est inscrit et a payé.
     *
     * @param formationId identifiant de la formation
     * @param email       email de l'apprenant extrait du token JWT
     * @return            ListeCoursFormation (cours + quiz)

     *         si l'apprenant n'est pas inscrit ou n'a pas payé
     */
    ListeCoursFormation getCoursDeFormation(Long formationId, String email);
}
