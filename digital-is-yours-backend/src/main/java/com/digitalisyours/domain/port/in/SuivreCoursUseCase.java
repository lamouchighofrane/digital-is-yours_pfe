package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Document;

import java.util.List;

public interface SuivreCoursUseCase {
    /**
     * Retourne la liste des documents d'un cours.
     * Vérifie que l'apprenant est bien inscrit et a payé.
     *
     * @param email       email de l'apprenant (extrait du JWT)
     * @param formationId identifiant de la formation
     * @param coursId     identifiant du cours
     * @return liste des documents associés au cours
     */
    List<Document> getDocumentsDuCours(String email, Long formationId, Long coursId);

    /**
     * Vérifie que l'apprenant a accès à un cours d'une formation.
     * Utilisé avant de streamer la vidéo.
     *
     * @param email       email de l'apprenant
     * @param formationId identifiant de la formation
     * @param coursId     identifiant du cours
     * @throws SecurityException si l'accès est refusé
     */
    void verifierAccesCours(String email, Long formationId, Long coursId);
}
