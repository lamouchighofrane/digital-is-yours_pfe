package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.SeanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SeanceJpaRepository extends JpaRepository<SeanceEntity, Long> {

    @Query("SELECT s FROM SeanceEntity s LEFT JOIN FETCH s.formation LEFT JOIN FETCH s.formateur " +
            "WHERE s.formateur.id = :formateurId ORDER BY s.dateSeance ASC")
    List<SeanceEntity> findByFormateurId(@Param("formateurId") Long formateurId);

    @Query("SELECT s FROM SeanceEntity s LEFT JOIN FETCH s.formation LEFT JOIN FETCH s.formateur " +
            "WHERE s.formation.id = :formationId ORDER BY s.dateSeance ASC")
    List<SeanceEntity> findByFormationId(@Param("formationId") Long formationId);

    // Séances pour un apprenant = séances des formations où il est inscrit et a payé
    @Query("SELECT DISTINCT s FROM SeanceEntity s " +
            "LEFT JOIN FETCH s.formation f " +
            "LEFT JOIN FETCH s.formateur " +
            "WHERE f.id IN (" +
            "  SELECT i.formation.id FROM InscriptionEntity i " +
            "  WHERE i.apprenant.email = :email AND i.statutPaiement = 'PAYE'" +
            ") AND s.statut != 'ANNULEE' " +
            "ORDER BY s.dateSeance ASC")
    List<SeanceEntity> findSeancesForApprenant(@Param("email") String email);
}