package com.digitalisyours.infrastructure.persistence.repository;


import com.digitalisyours.infrastructure.persistence.entity.PresenceSeanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresenceSeanceJpaRepository
        extends JpaRepository<PresenceSeanceEntity, Long> {

    Optional<PresenceSeanceEntity> findBySeanceIdAndApprenantEmail(
            Long seanceId, String apprenantEmail);

    List<PresenceSeanceEntity> findBySeanceId(Long seanceId);
}