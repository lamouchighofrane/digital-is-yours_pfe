package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.domain.model.OtpCode;
import com.digitalisyours.infrastructure.persistence.entity.OtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpJpaRepository extends JpaRepository<OtpEntity, Long> {
    @Query("SELECT o FROM OtpEntity o WHERE o.email = :email " +
            "AND o.type = :type AND o.used = false " +
            "AND o.expiresAt > :now " +
            "ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpEntity> findLatestValid(String email, OtpCode.OtpType type, LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE OtpEntity o SET o.used = true WHERE o.id = :id")
    void markAsUsed(Long id);

    @Modifying
    @Transactional
    void deleteByEmail(String email);
}
