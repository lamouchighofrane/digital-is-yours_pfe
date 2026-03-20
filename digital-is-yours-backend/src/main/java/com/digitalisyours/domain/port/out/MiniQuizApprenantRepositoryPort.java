package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Quiz;

import java.util.Optional;

public interface MiniQuizApprenantRepositoryPort {
    /**
     * Vérifie que l'apprenant est inscrit à la formation et a payé.
     */
    boolean estInscritEtPaye(String email, Long formationId);

    /**
     * Retourne le mini-quiz d'un cours avec ses questions et options.
     * Les options incluent estCorrecte (usage interne uniquement).
     */
    Optional<Quiz> findMiniQuizByCoursId(Long coursId);

    /**
     * Vérifie qu'un cours appartient bien à une formation.
     */
    boolean coursAppartientAFormation(Long coursId, Long formationId);
}
