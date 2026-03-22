package com.digitalisyours.domain.port.in;
import com.digitalisyours.domain.model.Certificat;

import java.util.List;

public interface CertificatUseCase {

    /**
     * Génère automatiquement un certificat après réussite au quiz final.
     * Appelé depuis QuizFinalApprenantService si reussi = true.
     */
    Certificat genererCertificat(Long apprenantId, Long formationId, Long quizId, Float noteFinal);

    /**
     * Récupère tous les certificats d'un apprenant.
     */
    List<Certificat> getMesCertificats(Long apprenantId);

    /**
     * Récupère un certificat par son ID (vérification que l'apprenant en est le propriétaire).
     */
    Certificat getCertificatById(Long certificatId, Long apprenantId);

    /**
     * Télécharge le PDF du certificat (retourne les bytes).
     */
    byte[] downloadCertificatPDF(Long certificatId, Long apprenantId);
}