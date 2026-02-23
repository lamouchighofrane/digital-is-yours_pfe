package com.digitalisyours.infrastructure.persistence.repository;


import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);

    @Modifying
    @Transactional
    @Query("UPDATE UserEntity u SET u.motDePasse = :password WHERE u.email = :email")
    void updatePassword(String email, String password);

    @Modifying @Transactional
    @Query("UPDATE UserEntity u SET u.emailVerifie = true WHERE u.email = :email")
    void markEmailVerified(String email);

    @Modifying @Transactional
    @Query("UPDATE UserEntity u SET u.derniereConnexion = :date WHERE u.email = :email")
    void updateLastLogin(String email, LocalDateTime date);

    List<UserEntity> findAllByRoleNot(Role role);
    List<UserEntity> findAllByRole(Role role);

    long countByRole(Role role);
    long countByEmailVerifieFalse();

    // ⚠️ NOUVEAU — nécessaire pour les stats "désactivés"
    long countByActiveFalse();
}
