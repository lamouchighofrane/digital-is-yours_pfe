package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.ResultatMiniQuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResultatMiniQuizJpaRepository
        extends JpaRepository<ResultatMiniQuizEntity, Long> {

    List<ResultatMiniQuizEntity> findByApprenantEmailOrderByDatePassageDesc(String email);

    @Query("SELECT r FROM ResultatMiniQuizEntity r " +
            "WHERE r.apprenantEmail = :email AND r.formationId = :formationId " +
            "ORDER BY r.datePassage DESC")
    List<ResultatMiniQuizEntity> findByEmailAndFormation(
            @Param("email") String email,
            @Param("formationId") Long formationId);

    @Query("SELECT AVG(r.score) FROM ResultatMiniQuizEntity r " +
            "WHERE r.apprenantEmail = :email AND r.formationId = :formationId")
    Optional<Double> findScoreMoyenByEmailAndFormation(
            @Param("email") String email,
            @Param("formationId") Long formationId);

    @Query("SELECT COUNT(r) FROM ResultatMiniQuizEntity r " +
            "WHERE r.apprenantEmail = :email AND r.formationId = :formationId " +
            "AND r.datePassage >= :since")
    long countRecentByEmailAndFormation(
            @Param("email") String email,
            @Param("formationId") Long formationId,
            @Param("since") LocalDateTime since);
    // ── NOUVEAU — pour dashboard infractions ─────────────────────
    @Query("SELECT COUNT(r) FROM ResultatMiniQuizEntity r WHERE r.suspectFraude = true")
    long countBySuspectFraudeTrue();

    @Query("SELECT COALESCE(SUM(r.nbInfractions), 0) FROM ResultatMiniQuizEntity r")
    long sumNbInfractions();

    @Query("SELECT r.id, r.apprenantEmail, " +
            "u.prenom, u.nom, " +
            "f.titre, c.titre, " +
            "r.nbInfractions, r.scoreBrut, r.penaliteAppliquee, r.score, " +
            "r.datePassage, r.detailInfractions " +
            "FROM ResultatMiniQuizEntity r " +
            "LEFT JOIN ApprenantEntity a ON a.email = r.apprenantEmail " +
            "LEFT JOIN UserEntity u ON u.id = a.id " +
            "LEFT JOIN FormationEntity f ON f.id = r.formationId " +
            "LEFT JOIN CoursEntity c ON c.id = r.coursId " +
            "WHERE r.suspectFraude = true " +
            "ORDER BY r.datePassage DESC")
    List<Object[]> findSuspectsWithDetails();
    // ── NOUVEAU — pour dashboard formateur ───────────────────────
    @Query("SELECT r.id, r.apprenantEmail, " +
            "u.prenom, u.nom, " +
            "f.id, f.titre, c.titre, " +
            "r.nbInfractions, r.scoreBrut, r.penaliteAppliquee, r.score, " +
            "r.datePassage, r.detailInfractions " +
            "FROM ResultatMiniQuizEntity r " +
            "LEFT JOIN ApprenantEntity a ON a.email = r.apprenantEmail " +
            "LEFT JOIN UserEntity u ON u.id = a.id " +
            "LEFT JOIN FormationEntity f ON f.id = r.formationId " +
            "LEFT JOIN CoursEntity c ON c.id = r.coursId " +
            "WHERE r.suspectFraude = true " +
            "AND r.formationId IN :formationIds " +
            "ORDER BY r.datePassage DESC")
    List<Object[]> findSuspectsForFormations(
            @Param("formationIds") List<Long> formationIds);
}