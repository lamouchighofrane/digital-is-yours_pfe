package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.InscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;


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

    @Modifying
    @Transactional
    @Query("UPDATE InscriptionEntity i SET " +
            "i.coursTermines = :coursTermines, " +
            "i.coursTotal = :coursTotal " +
            "WHERE i.apprenant.id = :apprenantId " +
            "AND i.formation.id = :formationId " +
            "AND i.statutPaiement = 'PAYE'")
    void updateCoursProgression(
            @Param("apprenantId") Long apprenantId,
            @Param("formationId") Long formationId,
            @Param("coursTermines") int coursTermines,
            @Param("coursTotal")    int coursTotal
    );
    // Mise à jour par apprenantId (utilisé après quiz final)
    @Modifying
    @Transactional
    @Query("UPDATE InscriptionEntity i SET i.statutApprenant = :statut " +
            "WHERE i.apprenant.id = :apprenantId " +
            "AND i.formation.id = :formationId " +
            "AND i.statutPaiement = 'PAYE'")
    void updateStatutApprenant(
            @Param("apprenantId") Long apprenantId,
            @Param("formationId") Long formationId,
            @Param("statut") String statut);

    // Mise à jour par email (utilisé pendant la progression)
// Ne jamais écraser CERTIFIE
    @Modifying
    @Transactional
    @Query("UPDATE InscriptionEntity i SET i.statutApprenant = :statut " +
            "WHERE i.apprenant.email = :email " +
            "AND i.formation.id = :formationId " +
            "AND i.statutPaiement = 'PAYE' " +
            "AND i.statutApprenant != 'CERTIFIE'")
    void updateStatutApprenantByEmail(
            @Param("email") String email,
            @Param("formationId") Long formationId,
            @Param("statut") String statut);

    // Trouver par apprenantId + formationId
    @Query("SELECT i FROM InscriptionEntity i " +
            "WHERE i.apprenant.id = :apprenantId " +
            "AND i.formation.id = :formationId")
    Optional<InscriptionEntity> findByApprenantIdAndFormationId(
            @Param("apprenantId") Long apprenantId,
            @Param("formationId") Long formationId);

}