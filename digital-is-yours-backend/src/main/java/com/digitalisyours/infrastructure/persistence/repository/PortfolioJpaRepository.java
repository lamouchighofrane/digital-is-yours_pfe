package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.PortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PortfolioJpaRepository extends JpaRepository<PortfolioEntity, Long> {

    Optional<PortfolioEntity> findByApprenantId(Long apprenantId);

    Optional<PortfolioEntity> findBySlug(String slug);

    boolean existsByApprenantId(Long apprenantId);
}