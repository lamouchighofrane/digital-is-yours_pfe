package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.SessionCalendrierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionCalendrierJpaRepository extends JpaRepository<SessionCalendrierEntity, Long> {
    @Query("SELECT s FROM SessionCalendrierEntity s " +
            "WHERE s.apprenant.email = :email " +
            "ORDER BY s.dateSession ASC")
    List<SessionCalendrierEntity> findByApprenantEmail(@Param("email") String email);

    @Query("SELECT s FROM SessionCalendrierEntity s " +
            "WHERE s.rappel24h = true " +
            "AND s.rappelEnvoye = false " +
            "AND s.isTerminee = false " +
            "AND s.dateSession BETWEEN :from AND :to")
    List<SessionCalendrierEntity> findRappelsAEnvoyer(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
