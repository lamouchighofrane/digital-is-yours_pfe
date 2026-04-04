package com.digitalisyours.infrastructure.persistence.repository;
import com.digitalisyours.infrastructure.persistence.entity.CertificatEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CertificatJpaRepository extends JpaRepository<CertificatEntity, Long> {

    Optional<CertificatEntity> findByApprenantIdAndFormationId(Long apprenantId, Long formationId);

    List<CertificatEntity> findByApprenantIdOrderByDateCreationDesc(Long apprenantId);

    boolean existsByApprenantIdAndFormationId(Long apprenantId, Long formationId);

    long countByApprenantId(Long apprenantId);

    Optional<CertificatEntity> findByNumeroCertificat(String numeroCertificat);

    @Modifying
    @Transactional
    @Query("UPDATE CertificatEntity c SET c.urlPDF = :urlPDF WHERE c.id = :id")
    void updateUrlPDF(@Param("id") Long id, @Param("urlPDF") String urlPDF);

    // ── US-050 : Marquer envoyé par email ──
    @Modifying
    @Transactional
    @Query("UPDATE CertificatEntity c SET c.estEnvoye = :estEnvoye WHERE c.id = :id")
    void updateEstEnvoye(@Param("id") Long id, @Param("estEnvoye") boolean estEnvoye);

    // ── US-059 : Marquer partagé sur LinkedIn ──
    @Modifying
    @Transactional
    @Query("UPDATE CertificatEntity c SET c.partageLinkedIn = true WHERE c.id = :id")
    void updatePartageLinkedIn(@Param("id") Long id);
    // ── US-051 : Admin — liste avec filtres dynamiques ──────────────────────────
    @Query("SELECT c FROM CertificatEntity c WHERE " +
            "(:formation IS NULL OR LOWER(c.formationTitre) LIKE LOWER(CONCAT('%',:formation,'%'))) AND " +
            "(:apprenant IS NULL OR LOWER(CONCAT(c.apprenantPrenom,' ',c.apprenantNom)) LIKE LOWER(CONCAT('%',:apprenant,'%'))) AND " +
            "(:search    IS NULL OR LOWER(c.numeroCertificat) LIKE LOWER(CONCAT('%',:search,'%'))) AND " +
            "(:debut     IS NULL OR c.dateCreation >= :debut) AND " +
            "(:fin       IS NULL OR c.dateCreation <= :fin) " +
            "ORDER BY c.dateCreation DESC")
    Page<CertificatEntity> findAllWithFilters(
            @Param("formation") String formation,
            @Param("apprenant") String apprenant,
            @Param("search")    String search,
            @Param("debut") LocalDateTime debut,
            @Param("fin")       LocalDateTime fin,
            Pageable pageable);

    @Query("SELECT COUNT(c) FROM CertificatEntity c WHERE c.dateCreation >= :debut")
    long countFromDate(@Param("debut") LocalDateTime debut);

    @Query("SELECT COUNT(DISTINCT c.formationId) FROM CertificatEntity c")
    long countFormationsAvecCertificats();

    @Query("SELECT CASE WHEN COUNT(c) = 0 THEN 0.0 " +
            "ELSE (SUM(CASE WHEN c.noteFinal >= c.notePassage THEN 1.0 ELSE 0.0 END) / COUNT(c)) * 100 " +
            "END FROM CertificatEntity c WHERE c.notePassage IS NOT NULL")
    double getTauxReussiteGlobal();
}