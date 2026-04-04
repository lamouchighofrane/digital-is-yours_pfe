package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.port.out.AdminCertificatRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CertificatEntity;
import com.digitalisyours.infrastructure.persistence.repository.CertificatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AdminCertificatRepositoryAdapter implements AdminCertificatRepositoryPort {

    private final CertificatJpaRepository certificatJpaRepository;

    @Override
    public Page<Certificat> findAllWithFilters(
            String formation,
            String apprenant,
            LocalDate dateDebut,
            LocalDate dateFin,
            String search,
            Pageable pageable) {

        LocalDateTime debut = dateDebut != null ? dateDebut.atStartOfDay()              : null;
        LocalDateTime fin   = dateFin   != null ? dateFin.atTime(23, 59, 59)            : null;

        return certificatJpaRepository
                .findAllWithFilters(formation, apprenant, search, debut, fin, pageable)
                .map(this::toDomain);
    }

    @Override
    public long countThisMonth() {
        LocalDateTime debut = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return certificatJpaRepository.countFromDate(debut);
    }

    @Override
    public long countFormationsActives() {
        return certificatJpaRepository.countFormationsAvecCertificats();
    }

    @Override
    public double getTauxReussite() {
        return certificatJpaRepository.getTauxReussiteGlobal();
    }

    @Override
    public long countTotal() {
        return certificatJpaRepository.count();
    }

    // ── Mapping Entity → Domain ──────────────────────────────
    private Certificat toDomain(CertificatEntity e) {
        return Certificat.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .noteFinal(e.getNoteFinal())
                .dateCreation(e.getDateCreation())
                .contextu(e.getContextu())
                .urlPDF(e.getUrlPDF())
                .estEnvoye(e.getEstEnvoye())
                .partageLinkedIn(e.getPartageLinkedIn())
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