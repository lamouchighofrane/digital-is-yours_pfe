package com.digitalisyours.infrastructure.persistence.repository;
import com.digitalisyours.infrastructure.persistence.entity.ResultatQuizFinalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResultatQuizFinalJpaRepository
        extends JpaRepository<ResultatQuizFinalEntity, Long> {

    /**
     * Compte le nombre de tentatives utilisées par un apprenant pour un quiz donné.
     */
    @Query("SELECT COUNT(r) FROM ResultatQuizFinalEntity r " +
            "WHERE r.apprenantEmail = :email AND r.quizId = :quizId")
    long countByApprenantEmailAndQuizId(
            @Param("email")  String email,
            @Param("quizId") Long   quizId);

    /**
     * Retourne le dernier résultat (le plus récent) d'un apprenant pour un quiz.
     */
    Optional<ResultatQuizFinalEntity>
    findTopByApprenantEmailAndQuizIdOrderByDatePassageDesc(
            String apprenantEmail, Long quizId);
}
