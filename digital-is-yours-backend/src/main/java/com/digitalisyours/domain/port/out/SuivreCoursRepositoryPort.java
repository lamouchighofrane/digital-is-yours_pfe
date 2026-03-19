package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Document;

import java.util.List;

public interface SuivreCoursRepositoryPort {
    /**
     * Vérifie que l'apprenant est inscrit et a payé pour la formation.
     */
    boolean estInscritEtPaye(String email, Long formationId);

    /**
     * Vérifie qu'un cours appartient bien à une formation.
     */
    boolean coursAppartientAFormation(Long coursId, Long formationId);

    /**
     * Retourne les documents d'un cours, triés par date d'ajout DESC.
     */
    List<Document> findDocumentsByCoursId(Long coursId);
}
