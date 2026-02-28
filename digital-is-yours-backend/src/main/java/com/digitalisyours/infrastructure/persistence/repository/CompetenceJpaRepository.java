package com.digitalisyours.infrastructure.persistence.repository;


import com.digitalisyours.infrastructure.persistence.entity.CompetenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompetenceJpaRepository extends JpaRepository<CompetenceEntity, Long> {
    boolean existsByNom(String nom);

    Optional<CompetenceEntity> findByNom(String nom);

    List<CompetenceEntity> findAllByOrderByNomAsc();

    @Query("SELECT DISTINCT c.categorie FROM CompetenceEntity c WHERE c.categorie IS NOT NULL AND c.categorie <> '' ORDER BY c.categorie")
    List<String> findAllCategories();

    @Query("SELECT c FROM CompetenceEntity c JOIN c.formations f WHERE f.id = :formationId ORDER BY c.nom")
    List<CompetenceEntity> findByFormationId(@Param("formationId") Long formationId);

    // ✅ AJOUT : supprimer les liens dans formation_competences avant de supprimer la compétence
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM formation_competences WHERE competence_id = :competenceId", nativeQuery = true)
    void deleteFormationLinks(@Param("competenceId") Long competenceId);
}
