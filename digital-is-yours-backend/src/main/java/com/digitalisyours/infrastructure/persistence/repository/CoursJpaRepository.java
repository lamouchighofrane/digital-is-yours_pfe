package com.digitalisyours.infrastructure.persistence.repository;


import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoursJpaRepository extends JpaRepository<CoursEntity, Long> {
    @Query("SELECT c FROM CoursEntity c WHERE c.formation.id = :formationId ORDER BY c.ordre ASC, c.dateCreation ASC")
    List<CoursEntity> findByFormationIdOrderByOrdre(@Param("formationId") Long formationId);

    @Query("SELECT COUNT(c) FROM CoursEntity c WHERE c.formation.id = :formationId")
    long countByFormationId(@Param("formationId") Long formationId);

    @Query("SELECT COUNT(c) FROM CoursEntity c WHERE c.formation.id = :formationId AND c.statut = 'PUBLIE'")
    long countPubliesByFormationId(@Param("formationId") Long formationId);

    @Query("SELECT MAX(c.ordre) FROM CoursEntity c WHERE c.formation.id = :formationId")
    Integer findMaxOrdreByFormationId(@Param("formationId") Long formationId);

    // ── Requêtes vidéo ──────────────────────────────────────────────────────
    /** Nombre de cours avec une vidéo locale dans une formation */
    @Query("SELECT COUNT(c) FROM CoursEntity c WHERE c.formation.id = :formationId AND c.videoType = 'LOCAL'")
    long countCoursWithVideoLocaleByFormation(@Param("formationId") Long formationId);

    // ── US-032 : Vérification cours ↔ formation (évite le lazy loading) ────
    /**
     * Vérifie qu'un cours appartient à une formation donnée.
     * Spring Data génère automatiquement la requête SQL :
     * SELECT COUNT(*) > 0 FROM cours WHERE id = ? AND formation_id = ?
     */
    boolean existsByIdAndFormationId(Long id, Long formationId);
}
