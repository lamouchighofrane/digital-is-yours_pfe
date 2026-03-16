package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Inscription;
import com.digitalisyours.domain.port.out.InscriptionRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.ApprenantEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.InscriptionEntity;
import com.digitalisyours.infrastructure.persistence.repository.ApprenantJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InscriptionRepositoryAdapter implements InscriptionRepositoryPort {
    private final InscriptionJpaRepository inscriptionJpaRepository;
    private final ApprenantJpaRepository apprenantJpaRepository;
    private final FormationJpaRepository formationJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Inscription> findByApprenantEmail(String email) {
        return inscriptionJpaRepository.findByApprenantEmail(email)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inscription> findByApprenantEmailAndFormationId(String email, Long formationId) {
        return inscriptionJpaRepository
                .findByApprenantEmailAndFormationId(email, formationId)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Inscription save(Inscription inscription) {
        // L'email apprenant est stocké dans le domaine via apprenantEmail (voir port)
        ApprenantEntity apprenant = apprenantJpaRepository
                .findById(inscription.getApprenantId())
                .orElseThrow(() -> new RuntimeException("Apprenant non trouvé"));

        FormationEntity formation = formationJpaRepository
                .findById(inscription.getFormationId())
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        // Chercher l'entité existante (par id ou apprenant+formation)
        InscriptionEntity entity;
        if (inscription.getId() != null) {
            entity = inscriptionJpaRepository.findById(inscription.getId())
                    .orElse(new InscriptionEntity());
        } else {
            entity = inscriptionJpaRepository
                    .findByApprenantEmailAndFormationId(
                            apprenant.getEmail(), formation.getId())
                    .orElse(new InscriptionEntity());
        }

        entity.setApprenant(apprenant);
        entity.setFormation(formation);
        entity.setDateInscription(inscription.getDateInscription());
        entity.setProgression(inscription.getProgression() != null ? inscription.getProgression() : 0f);
        entity.setCoursTotal(inscription.getCoursTotal() != null ? inscription.getCoursTotal() : 0);
        entity.setCoursTermines(inscription.getCoursTermines() != null ? inscription.getCoursTermines() : 0);
        entity.setDernierActivite(inscription.getDernierActivite());
        entity.setStatutPaiement(inscription.getStatutPaiement());
        entity.setMethodePaiement(inscription.getMethodePaiement());
        entity.setReferencePaiement(inscription.getReferencePaiement());
        entity.setMontantPaye(inscription.getMontantPaye());
        entity.setDatePaiement(inscription.getDatePaiement());

        return toDomain(inscriptionJpaRepository.save(entity));
    }

    @Override
    public boolean existsByApprenantEmailAndFormationIdAndStatutPaiement(
            String email, Long formationId, String statut) {
        return inscriptionJpaRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(email, formationId, statut);
    }

    // ── Mapping ───────────────────────────────────────────────────────

    private Inscription toDomain(InscriptionEntity e) {
        Inscription i = Inscription.builder()
                .id(e.getId())
                .dateInscription(e.getDateInscription())
                .progression(e.getProgression())
                .coursTotal(e.getCoursTotal())
                .coursTermines(e.getCoursTermines())
                .dernierActivite(e.getDernierActivite())
                .statutPaiement(e.getStatutPaiement())
                .methodePaiement(e.getMethodePaiement())
                .referencePaiement(e.getReferencePaiement())
                .montantPaye(e.getMontantPaye())
                .datePaiement(e.getDatePaiement())
                .build();

        try {
            if (e.getApprenant() != null) i.setApprenantId(e.getApprenant().getId());
        } catch (Exception ex) { /* lazy proxy */ }

        try {
            if (e.getFormation() != null) {
                i.setFormationId(e.getFormation().getId());
                i.setFormationTitre(e.getFormation().getTitre());
                i.setFormationImage(e.getFormation().getImageCouverture());
                i.setFormationNiveau(e.getFormation().getNiveau());
                i.setFormationPrix(e.getFormation().getPrix());
                i.setFormationDescription(e.getFormation().getDescription()); // ← AJOUTER
            }
        } catch (Exception ex) { /* lazy proxy */ }

        return i;
    }
}
