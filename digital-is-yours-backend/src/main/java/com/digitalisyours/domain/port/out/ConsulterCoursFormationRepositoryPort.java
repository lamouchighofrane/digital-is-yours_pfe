package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.CoursFormation;
import com.digitalisyours.domain.model.QuizFinalInfo;

import java.util.List;
import java.util.Optional;

public interface ConsulterCoursFormationRepositoryPort {
    /**
     * Vérifie que l'apprenant est inscrit à la formation avec statut PAYE.
     */
    boolean estInscritEtPaye(String email, Long formationId);

    /**
     * Retourne les cours publiés de la formation, triés par ordre croissant.
     */
    List<CoursFormation> findCoursPubiesParFormation(Long formationId);

    /**
     * Retourne les infos du quiz final de la formation, si il existe.
     */
    Optional<QuizFinalInfo> findQuizFinalInfo(Long formationId);

    /**
     * Vérifie si un MiniQuiz existe pour un cours donné.
     */
    boolean existsMiniQuizForCours(Long coursId);
}
