package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.NotificationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long> {
    // Toutes les notifs d'un user, du plus récent au plus ancien
    List<NotificationEntity> findByUserOrderByDateCreationDesc(UserEntity user);

    // Notifs non lues d'un user
    List<NotificationEntity> findByUserAndLuFalseOrderByDateCreationDesc(UserEntity user);

    // Comptage non lues
    long countByUserAndLuFalse(UserEntity user);

    // Marquer toutes les notifs d'un user comme lues
    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.lu = true WHERE n.user = :user")
    void marquerToutesLues(@Param("user") UserEntity user);
}
