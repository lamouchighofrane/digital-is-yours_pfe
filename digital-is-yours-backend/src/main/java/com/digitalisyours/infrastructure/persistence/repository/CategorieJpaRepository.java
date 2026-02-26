package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.CategorieEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategorieJpaRepository extends JpaRepository<CategorieEntity, Long> {
    boolean existsByNom(String nom);

    Optional<CategorieEntity> findByNom(String nom);

    List<CategorieEntity> findAllByOrderByOrdreAffichageAsc();

    List<CategorieEntity> findByVisibleCatalogueTrue();
}
