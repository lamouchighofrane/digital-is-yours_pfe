package com.digitalisyours.domain.port.in;
import com.digitalisyours.domain.model.Certificat;

import java.util.List;

public interface CertificatUseCase {

    Certificat genererCertificat(Long apprenantId, Long formationId, Long quizId, Float noteFinal);

    List<Certificat> getMesCertificats(Long apprenantId);

    Certificat getCertificatById(Long certificatId, Long apprenantId);

    /** Accès authentifié — vérifie que l'apprenant est propriétaire */
    byte[] downloadCertificatPDF(Long certificatId, Long apprenantId);

    /** Accès public — QR Code scanné depuis téléphone, pas de vérification ownership */
    byte[] downloadCertificatPDFPublic(Long certificatId);
}
