package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.AnalyseRisqueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyseRisqueJpaRepository
        extends JpaRepository<AnalyseRisqueEntity, Long> {

    @Query("SELECT a FROM AnalyseRisqueEntity a " +
            "WHERE a.apprenantId = :apprenantId " +
            "ORDER BY a.dateAnalyse DESC")
    List<AnalyseRisqueEntity> findByApprenantId(@Param("apprenantId") Long apprenantId);

    @Query("SELECT a FROM AnalyseRisqueEntity a " +
            "WHERE a.apprenantId = :apprenantId AND a.formationId = :formationId " +
            "ORDER BY a.dateAnalyse DESC")
    Optional<AnalyseRisqueEntity> findLastByApprenantAndFormation(
            @Param("apprenantId") Long apprenantId,
            @Param("formationId") Long formationId);

    @Query("SELECT a FROM AnalyseRisqueEntity a " +
            "WHERE a.niveauRisque IN ('MOYEN','ELEVE') " +
            "AND a.notificationEnvoyee = false " +
            "ORDER BY a.dateAnalyse DESC")
    List<AnalyseRisqueEntity> findNonNotifiesARisque();
    /**
     * Vérifie si une notification a déjà été envoyée récemment
     * pour éviter le spam de notifications.
     */
    @Query("SELECT COUNT(a) > 0 FROM AnalyseRisqueEntity a " +
            "WHERE a.apprenantId = :apprenantId " +
            "AND a.formationId = :formationId " +
            "AND a.notificationEnvoyee = true " +
            "AND a.dateAnalyse >= :depuis")
    boolean existsNotificationRecentePourApprenant(
            @Param("apprenantId") Long apprenantId,
            @Param("formationId") Long formationId,
            @Param("depuis") LocalDateTime depuis);
}