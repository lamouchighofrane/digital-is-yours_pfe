package com.digitalisyours.domain.port.out;
import com.digitalisyours.domain.model.Certificat;

import java.util.List;
import java.util.Optional;

public interface CertificatRepositoryPort {

    Certificat save(Certificat certificat);

    Optional<Certificat> findById(Long id);

    Optional<Certificat> findByApprenantIdAndFormationId(Long apprenantId, Long formationId);

    List<Certificat> findByApprenantId(Long apprenantId);

    boolean existsByApprenantIdAndFormationId(Long apprenantId, Long formationId);

    long countByApprenantId(Long apprenantId);

    Optional<Certificat> findByNumeroCertificat(String numeroCertificat);

    void updateUrlPDF(Long id, String urlPDF);

    /**
     * Retrouve l'email de l'apprenant depuis son ID.
     * Nécessaire car ProfilApprenantUseCase.getProfil() prend un email, pas un ID.
     */
    Optional<String> findApprenantEmailById(Long apprenantId);
}