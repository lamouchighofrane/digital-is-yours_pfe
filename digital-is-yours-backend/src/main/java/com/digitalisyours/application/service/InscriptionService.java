package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.model.Inscription;
import com.digitalisyours.domain.port.in.InscriptionUseCase;
import com.digitalisyours.domain.port.out.FormationRepositoryPort;
import com.digitalisyours.domain.port.out.InscriptionRepositoryPort;
import com.digitalisyours.domain.port.out.ProfilApprenantRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InscriptionService implements InscriptionUseCase {
    private final InscriptionRepositoryPort     inscriptionRepository;
    private final FormationRepositoryPort       formationRepository;
    private final ProfilApprenantRepositoryPort apprenantRepository;

    private static final List<String> CARTES_REFUSEES = List.of(
            "4000000000000002",
            "4000000000009995",
            "0000000000000000"
    );

    @Override
    public List<Inscription> getMesInscriptions(String email) {
        return inscriptionRepository.findByApprenantEmail(email)
                .stream()
                .filter(i -> "PAYE".equals(i.getStatutPaiement()))
                .toList();
    }

    @Override
    public boolean estInscrit(String email, Long formationId) {
        return inscriptionRepository.existsByApprenantEmailAndFormationIdAndStatutPaiement(
                email, formationId, "PAYE");
    }

    @Override
    public Inscription initierPaiement(String email, Long formationId) {
        Formation formation = formationRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        if (!"PUBLIE".equals(formation.getStatut()))
            throw new RuntimeException("Cette formation n'est pas disponible");

        if (estInscrit(email, formationId))
            throw new RuntimeException("Vous êtes déjà inscrit à cette formation");

        Long apprenantId = apprenantRepository.findUserIdByEmail(email)
                .orElseThrow(() -> new RuntimeException("Apprenant non trouvé"));

        return inscriptionRepository.findByApprenantEmailAndFormationId(email, formationId)
                .map(i -> {
                    if ("EN_ATTENTE".equals(i.getStatutPaiement())) {
                        i.setApprenantId(apprenantId);
                        i.setMontantPaye(formation.getPrix());
                        return inscriptionRepository.save(i);
                    }
                    if ("ECHEC".equals(i.getStatutPaiement())) {
                        i.setStatutPaiement("EN_ATTENTE");
                        i.setApprenantId(apprenantId);
                        i.setMontantPaye(formation.getPrix());
                        i.setDateInscription(LocalDateTime.now());
                        i.setReferencePaiement(null);
                        i.setDatePaiement(null);
                        return inscriptionRepository.save(i);
                    }
                    return i;
                })
                .orElseGet(() -> {
                    Inscription nouvelle = Inscription.builder()
                            .apprenantId(apprenantId)
                            .formationId(formationId)
                            .formationTitre(formation.getTitre())
                            .formationImage(formation.getImageCouverture())
                            .formationNiveau(formation.getNiveau())
                            .formationPrix(formation.getPrix())
                            .dateInscription(LocalDateTime.now())
                            .progression(0f)
                            .coursTotal(0)
                            .coursTermines(0)
                            .statutPaiement("EN_ATTENTE")
                            .montantPaye(formation.getPrix())
                            .build();
                    return inscriptionRepository.save(nouvelle);
                });
    }

    /**
     * Confirmation après retour depuis Stripe Checkout
     */
    @Override
    public Inscription confirmerDepuisStripe(String email, Long formationId, String stripeSessionId) {
        Long apprenantId = apprenantRepository.findUserIdByEmail(email)
                .orElseThrow(() -> new RuntimeException("Apprenant non trouvé"));

        Formation formation = formationRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        // Chercher inscription existante ou en créer une
        Inscription inscription = inscriptionRepository
                .findByApprenantEmailAndFormationId(email, formationId)
                .orElseGet(() -> Inscription.builder()
                        .apprenantId(apprenantId)
                        .formationId(formationId)
                        .formationTitre(formation.getTitre())
                        .formationImage(formation.getImageCouverture())
                        .formationNiveau(formation.getNiveau())
                        .formationPrix(formation.getPrix())
                        .dateInscription(LocalDateTime.now())
                        .progression(0f)
                        .coursTotal(0)
                        .coursTermines(0)
                        .montantPaye(formation.getPrix())
                        .build());

        if ("PAYE".equals(inscription.getStatutPaiement()))
            return inscription; // déjà payé (double appel)

        inscription.setApprenantId(apprenantId);
        inscription.setStatutPaiement("PAYE");
        inscription.setMethodePaiement("STRIPE");
        inscription.setReferencePaiement(
                stripeSessionId != null
                        ? "STR-" + stripeSessionId.substring(stripeSessionId.length() - 8).toUpperCase()
                        : "STR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        inscription.setDatePaiement(LocalDateTime.now());

        return inscriptionRepository.save(inscription);
    }

    @Override
    public Inscription confirmerPaiement(String email, Long formationId,
                                         Map<String, Object> payloadCarte) {
        Long apprenantId = apprenantRepository.findUserIdByEmail(email)
                .orElseThrow(() -> new RuntimeException("Apprenant non trouvé"));

        Inscription inscription = inscriptionRepository
                .findByApprenantEmailAndFormationId(email, formationId)
                .orElseThrow(() -> new RuntimeException(
                        "Aucune inscription en attente"));

        if ("PAYE".equals(inscription.getStatutPaiement()))
            throw new RuntimeException("Vous êtes déjà inscrit à cette formation");

        inscription.setApprenantId(apprenantId);

        String numeroCarte = nettoyer((String) payloadCarte.get("numeroCarte"));
        String nomCarte    = (String) payloadCarte.get("nomCarte");
        String expiration  = (String) payloadCarte.get("expiration");
        String cvv         = (String) payloadCarte.get("cvv");

        if (numeroCarte == null || numeroCarte.length() < 16)
            throw new RuntimeException("Numéro de carte invalide");
        if (nomCarte == null || nomCarte.isBlank())
            throw new RuntimeException("Nom du titulaire requis");
        if (expiration == null || !expiration.matches("\\d{2}/\\d{2}"))
            throw new RuntimeException("Date d'expiration invalide");
        if (cvv == null || cvv.length() < 3)
            throw new RuntimeException("CVV invalide");

        if (CARTES_REFUSEES.contains(numeroCarte)) {
            inscription.setStatutPaiement("ECHEC");
            inscriptionRepository.save(inscription);
            throw new RuntimeException("Paiement refusé.");
        }

        inscription.setStatutPaiement("PAYE");
        inscription.setMethodePaiement("CARTE");
        inscription.setReferencePaiement(
                "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        inscription.setDatePaiement(LocalDateTime.now());

        return inscriptionRepository.save(inscription);
    }

    private String nettoyer(String s) {
        return s == null ? null : s.replaceAll("[\\s\\-]", "");
    }
}
