package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.ProgressionCoursEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgressionCoursJpaRepository extends JpaRepository<ProgressionCoursEntity, Long> {

    @Query("SELECT p FROM ProgressionCoursEntity p " +
            "WHERE p.apprenant.email = :email AND p.cours.id = :coursId")
    Optional<ProgressionCoursEntity> findByEmailAndCoursId(
            @Param("email") String email,
            @Param("coursId") Long coursId);

    @Query("SELECT p.cours.id FROM ProgressionCoursEntity p " +
            "WHERE p.apprenant.email = :email " +
            "AND p.formation.id = :formationId " +
            "AND p.statut = 'TERMINE'")
    List<Long> findCoursTerminesIds(
            @Param("email") String email,
            @Param("formationId") Long formationId);

    @Query("SELECT p FROM ProgressionCoursEntity p " +
            "JOIN FETCH p.cours c " +
            "WHERE p.apprenant.email = :email " +
            "AND p.formation.id = :formationId " +
            "ORDER BY c.ordre ASC")
    List<ProgressionCoursEntity> findByEmailAndFormationId(
            @Param("email") String email,
            @Param("formationId") Long formationId);

    // ── Compteurs pour la formule de progression ──────────────────────────

    /** Nombre de vidéos vues dans la formation */
    @Query("SELECT COUNT(p) FROM ProgressionCoursEntity p " +
            "WHERE p.apprenant.email = :email " +
            "AND p.formation.id = :formationId " +
            "AND p.videoVue = true")
    int countVideosVues(@Param("email") String email,
                        @Param("formationId") Long formationId);

    /** Nombre de documents ouverts dans la formation */
    @Query("SELECT COUNT(p) FROM ProgressionCoursEntity p " +
            "WHERE p.apprenant.email = :email " +
            "AND p.formation.id = :formationId " +
            "AND p.documentOuvert = true")
    int countDocumentsOuverts(@Param("email") String email,
                              @Param("formationId") Long formationId);

    /** Nombre de quiz passés dans la formation */
    @Query("SELECT COUNT(p) FROM ProgressionCoursEntity p " +
            "WHERE p.apprenant.email = :email " +
            "AND p.formation.id = :formationId " +
            "AND p.quizPasse = true")
    int countQuizPasses(@Param("email") String email,
                        @Param("formationId") Long formationId);

    /** Nombre de cours terminés (les 3 conditions remplies) */
    @Query("SELECT COUNT(p) FROM ProgressionCoursEntity p " +
            "WHERE p.apprenant.email = :email " +
            "AND p.formation.id = :formationId " +
            "AND p.statut = 'TERMINE'")
    int countCoursTermines(@Param("email") String email,
                           @Param("formationId") Long formationId);
}