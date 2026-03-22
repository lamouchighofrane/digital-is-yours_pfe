package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.port.in.CertificatUseCase;
import com.digitalisyours.domain.port.in.ProfilApprenantUseCase;
import com.digitalisyours.domain.port.out.CertificatRepositoryPort;
import com.digitalisyours.infrastructure.pdf.CertificatPdfGenerator;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.QuizJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificatService implements CertificatUseCase {

    private final CertificatRepositoryPort certificatRepository;
    private final CertificatPdfGenerator   pdfGenerator;
    private final ProfilApprenantUseCase   profilUseCase;
    private final FormationJpaRepository   formationJpaRepository;
    private final QuizJpaRepository        quizJpaRepository;

    @Value("${app.certificats.storage-path:./certificats}")
    private String storagePath;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ══════════════════════════════════════════════════════════════════════════
    // Génération automatique
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public Certificat genererCertificat(Long apprenantId, Long formationId,
                                        Long quizId, Float noteFinal) {

        if (certificatRepository.existsByApprenantIdAndFormationId(apprenantId, formationId)) {
            log.info("Certificat déjà existant – apprenant={} formation={}", apprenantId, formationId);
            return certificatRepository
                    .findByApprenantIdAndFormationId(apprenantId, formationId)
                    .orElseThrow();
        }

        String apprenantEmail = certificatRepository
                .findApprenantEmailById(apprenantId)
                .orElseThrow(() -> new RuntimeException("Apprenant introuvable : " + apprenantId));

        Apprenant apprenant = profilUseCase.getProfil(apprenantEmail);

        FormationEntity formation = formationJpaRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation introuvable : " + formationId));

        QuizEntity quiz = (quizId != null)
                ? quizJpaRepository.findById(quizId).orElse(null)
                : null;

        long   seq    = certificatRepository.countByApprenantId(apprenantId) + 1;
        String raw    = formation.getTitre().replaceAll("[^A-Za-z0-9]", "");
        String code   = raw.substring(0, Math.min(3, raw.length())).toUpperCase();
        String annee  = String.valueOf(LocalDateTime.now().getYear());
        String numero = String.format("#%s-%s-%04d", code, annee, seq);

        String contextu = "Ce certificat atteste que "
                + apprenant.getPrenom() + " " + apprenant.getNom()
                + " a demontre les competences requises en "
                + formation.getTitre()
                + ", ayant complete l'integralite du programme avec succes"
                + " sur la plateforme Digital Is Yours.";

        String niveauStr = formation.getNiveau() != null
                ? formation.getNiveau().toString()
                : null;

        Float notePassage = (quiz != null && quiz.getNotePassage() != null)
                ? quiz.getNotePassage()
                : null;

        Integer duree = formation.getDureeEstimee();

        Certificat certificat = Certificat.builder()
                .titre("Certificat de Reussite - " + formation.getTitre())
                .noteFinal(noteFinal)
                .dateCreation(LocalDateTime.now())
                .contextu(contextu)
                .estEnvoye(false)
                .numeroCertificat(numero)
                .apprenantId(apprenantId)
                .apprenantEmail(apprenant.getEmail())
                .apprenantPrenom(apprenant.getPrenom())
                .apprenantNom(apprenant.getNom())
                .formationId(formationId)
                .formationTitre(formation.getTitre())
                .formationNiveau(niveauStr)
                .formationDuree(duree)
                .quizId(quizId)
                .notePassage(notePassage)
                .build();

        Certificat saved = certificatRepository.save(certificat);

        try {
            byte[] pdfBytes = pdfGenerator.generer(saved);
            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            String fileName = "cert_" + saved.getId() + "_" + System.currentTimeMillis() + ".pdf";
            Files.write(dir.resolve(fileName), pdfBytes);

            String urlPDF = baseUrl + "/api/apprenant/certificats/" + saved.getId() + "/download";
            certificatRepository.updateUrlPDF(saved.getId(), urlPDF);
            saved.setUrlPDF(urlPDF);

            log.info("Certificat genere : {} – apprenant={} formation={}", numero, apprenantId, formationId);
        } catch (IOException e) {
            log.error("Erreur generation PDF (non bloquant) : {}", e.getMessage(), e);
        }

        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Consultation
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public List<Certificat> getMesCertificats(Long apprenantId) {
        return certificatRepository.findByApprenantId(apprenantId);
    }

    @Override
    public Certificat getCertificatById(Long certificatId, Long apprenantId) {
        Certificat cert = certificatRepository.findById(certificatId)
                .orElseThrow(() -> new RuntimeException("Certificat introuvable : " + certificatId));
        if (!cert.getApprenantId().equals(apprenantId)) {
            throw new RuntimeException("Acces refuse");
        }
        return cert;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Téléchargement PDF — accès authentifié
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public byte[] downloadCertificatPDF(Long certificatId, Long apprenantId) {
        Certificat cert = getCertificatById(certificatId, apprenantId);
        try {
            return pdfGenerator.generer(cert);
        } catch (IOException e) {
            throw new RuntimeException("Erreur generation PDF : " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Téléchargement PDF — accès PUBLIC (QR Code depuis téléphone)
    // Pas de vérification d'ownership — le certificat est public par nature
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public byte[] downloadCertificatPDFPublic(Long certificatId) {
        Certificat cert = certificatRepository.findById(certificatId)
                .orElseThrow(() -> new RuntimeException("Certificat introuvable : " + certificatId));
        try {
            return pdfGenerator.generer(cert);
        } catch (IOException e) {
            throw new RuntimeException("Erreur generation PDF : " + e.getMessage(), e);
        }
    }
}