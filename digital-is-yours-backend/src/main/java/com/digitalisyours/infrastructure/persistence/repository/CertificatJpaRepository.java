package com.digitalisyours.infrastructure.persistence.repository;
import com.digitalisyours.infrastructure.persistence.entity.CertificatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificatJpaRepository extends JpaRepository<CertificatEntity, Long> {

    Optional<CertificatEntity> findByApprenantIdAndFormationId(Long apprenantId, Long formationId);

    List<CertificatEntity> findByApprenantIdOrderByDateCreationDesc(Long apprenantId);

    boolean existsByApprenantIdAndFormationId(Long apprenantId, Long formationId);

    long countByApprenantId(Long apprenantId);

    Optional<CertificatEntity> findByNumeroCertificat(String numeroCertificat);

    @Modifying
    @Query("UPDATE CertificatEntity c SET c.urlPDF = :urlPDF WHERE c.id = :id")
    void updateUrlPDF(@Param("id") Long id, @Param("urlPDF") String urlPDF);

    // ── US-050 : Marquer le certificat comme envoyé par email ──
    @Modifying
    @Query("UPDATE CertificatEntity c SET c.estEnvoye = :estEnvoye WHERE c.id = :id")
    void updateEstEnvoye(@Param("id") Long id, @Param("estEnvoye") boolean estEnvoye);
}