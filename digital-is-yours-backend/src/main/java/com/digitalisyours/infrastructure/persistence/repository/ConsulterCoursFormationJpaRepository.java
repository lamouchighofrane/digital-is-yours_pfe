package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsulterCoursFormationJpaRepository extends JpaRepository<CoursEntity, Long> {
    /**
     * Retourne uniquement les cours PUBLIÉS d'une formation, triés par ordre croissant.
     */
    @Query("SELECT c FROM CoursEntity c " +
            "WHERE c.formation.id = :formationId " +
            "AND c.statut = 'PUBLIE' " +
            "ORDER BY c.ordre ASC")
    List<CoursEntity> findCoursPubiesByFormationId(@Param("formationId") Long formationId);
    @Query("SELECT q FROM QuizEntity q WHERE q.type = 'QuizFinal' AND q.formation.id = :formationId")
    Optional<QuizEntity> findQuizFinalByFormationId(@Param("formationId") Long formationId);
}
