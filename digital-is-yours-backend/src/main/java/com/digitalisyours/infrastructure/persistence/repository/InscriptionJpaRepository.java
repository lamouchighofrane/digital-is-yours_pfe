package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.InscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscriptionJpaRepository extends JpaRepository<InscriptionEntity, Long> {
    @Query("SELECT i FROM InscriptionEntity i " +
            "LEFT JOIN FETCH i.formation f " +
            "WHERE i.apprenant.email = :email")
    List<InscriptionEntity> findByApprenantEmail(@Param("email") String email);

    @Query("SELECT i FROM InscriptionEntity i " +
            "WHERE i.apprenant.email = :email " +
            "AND i.formation.id = :formationId")
    Optional<InscriptionEntity> findByApprenantEmailAndFormationId(
            @Param("email") String email,
            @Param("formationId") Long formationId);

    @Query("SELECT COUNT(i) > 0 FROM InscriptionEntity i " +
            "WHERE i.apprenant.email = :email " +
            "AND i.formation.id = :formationId " +
            "AND i.statutPaiement = :statut")
    boolean existsByApprenantEmailAndFormationIdAndStatutPaiement(
            @Param("email") String email,
            @Param("formationId") Long formationId,
            @Param("statut") String statut);
}
