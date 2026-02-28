package com.digitalisyours.infrastructure.persistence.repository;


import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormationJpaRepository extends JpaRepository<FormationEntity, Long> {
    @Query("SELECT f FROM FormationEntity f LEFT JOIN FETCH f.categorie LEFT JOIN FETCH f.formateur ORDER BY f.dateCreation DESC")
    List<FormationEntity> findAllWithCategorie();

    @Query("SELECT f FROM FormationEntity f LEFT JOIN FETCH f.categorie WHERE f.categorie.id = :catId ORDER BY f.dateCreation DESC")
    List<FormationEntity> findByCategorieWithDetails(@Param("catId") Long catId);

    @Query("SELECT COUNT(f) FROM FormationEntity f")
    long countAll();

    @Query("SELECT COUNT(f) FROM FormationEntity f WHERE f.statut = 'PUBLIE'")
    long countPubliees();

    @Query("SELECT COUNT(f) FROM FormationEntity f WHERE f.statut = 'BROUILLON'")
    long countBrouillons();
}
