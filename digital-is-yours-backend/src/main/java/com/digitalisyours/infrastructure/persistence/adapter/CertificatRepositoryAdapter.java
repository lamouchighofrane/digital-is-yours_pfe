package com.digitalisyours.infrastructure.persistence.adapter;
import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.port.out.CertificatRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CertificatEntity;
import com.digitalisyours.infrastructure.persistence.repository.ApprenantJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.CertificatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CertificatRepositoryAdapter implements CertificatRepositoryPort {

    private final CertificatJpaRepository certificatJpaRepository;
    private final ApprenantJpaRepository  apprenantJpaRepository;

    // ══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public Certificat save(Certificat certificat) {
        return toDomain(certificatJpaRepository.save(toEntity(certificat)));
    }

    @Override
    public Optional<Certificat> findById(Long id) {
        return certificatJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Certificat> findByApprenantIdAndFormationId(Long apprenantId, Long formationId) {
        return certificatJpaRepository
                .findByApprenantIdAndFormationId(apprenantId, formationId)
                .map(this::toDomain);
    }

    @Override
    public List<Certificat> findByApprenantId(Long apprenantId) {
        return certificatJpaRepository
                .findByApprenantIdOrderByDateCreationDesc(apprenantId)
                .stream().map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByApprenantIdAndFormationId(Long apprenantId, Long formationId) {
        return certificatJpaRepository.existsByApprenantIdAndFormationId(apprenantId, formationId);
    }

    @Override
    public long countByApprenantId(Long apprenantId) {
        return certificatJpaRepository.countByApprenantId(apprenantId);
    }

    @Override
    public Optional<Certificat> findByNumeroCertificat(String numeroCertificat) {
        return certificatJpaRepository.findByNumeroCertificat(numeroCertificat).map(this::toDomain);
    }

    @Override
    @Transactional
    public void updateUrlPDF(Long id, String urlPDF) {
        certificatJpaRepository.updateUrlPDF(id, urlPDF);
    }

    /**
     * Retrouve l'email de l'apprenant via ApprenantJpaRepository.
     * ApprenantEntity hérite de UserEntity qui contient le champ email.
     */
    @Override
    public Optional<String> findApprenantEmailById(Long apprenantId) {
        return apprenantJpaRepository.findById(apprenantId)
                .map(a -> a.getEmail());   // getEmail() est dans UserEntity (parent)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Mapping
    // ══════════════════════════════════════════════════════════════════════════

    private CertificatEntity toEntity(Certificat c) {
        CertificatEntity e = new CertificatEntity();
        e.setId(c.getId());
        e.setTitre(c.getTitre());
        e.setNoteFinal(c.getNoteFinal());
        e.setDateCreation(c.getDateCreation());
        e.setContextu(c.getContextu());
        e.setUrlPDF(c.getUrlPDF());
        e.setEstEnvoye(c.getEstEnvoye() != null ? c.getEstEnvoye() : false);
        e.setNumeroCertificat(c.getNumeroCertificat());
        e.setApprenantId(c.getApprenantId());
        e.setApprenantEmail(c.getApprenantEmail());
        e.setApprenantPrenom(c.getApprenantPrenom());
        e.setApprenantNom(c.getApprenantNom());
        e.setFormationId(c.getFormationId());
        e.setFormationTitre(c.getFormationTitre());
        e.setFormationNiveau(c.getFormationNiveau());
        e.setFormationDuree(c.getFormationDuree());
        e.setQuizId(c.getQuizId());
        e.setNotePassage(c.getNotePassage());
        return e;
    }

    private Certificat toDomain(CertificatEntity e) {
        return Certificat.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .noteFinal(e.getNoteFinal())
                .dateCreation(e.getDateCreation())
                .contextu(e.getContextu())
                .urlPDF(e.getUrlPDF())
                .estEnvoye(e.getEstEnvoye())
                .numeroCertificat(e.getNumeroCertificat())
                .apprenantId(e.getApprenantId())
                .apprenantEmail(e.getApprenantEmail())
                .apprenantPrenom(e.getApprenantPrenom())
                .apprenantNom(e.getApprenantNom())
                .formationId(e.getFormationId())
                .formationTitre(e.getFormationTitre())
                .formationNiveau(e.getFormationNiveau())
                .formationDuree(e.getFormationDuree())
                .quizId(e.getQuizId())
                .notePassage(e.getNotePassage())
                .build();
    }
}