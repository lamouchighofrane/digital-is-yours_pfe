package com.digitalisyours.infrastructure.persistence.repository;


import com.digitalisyours.infrastructure.persistence.entity.FormateurEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface FormateurJpaRepository extends JpaRepository<FormateurEntity, Long> {

    Optional<FormateurEntity> findByEmail(String email);

    boolean existsByEmail(String email);


    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO formateurs (user_id) VALUES (:userId)", nativeQuery = true)
    void insertLigneFormateurManquante(@Param("userId") Long userId);
}
