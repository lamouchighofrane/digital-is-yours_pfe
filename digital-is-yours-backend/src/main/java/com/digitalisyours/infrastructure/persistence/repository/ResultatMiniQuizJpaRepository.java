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
}