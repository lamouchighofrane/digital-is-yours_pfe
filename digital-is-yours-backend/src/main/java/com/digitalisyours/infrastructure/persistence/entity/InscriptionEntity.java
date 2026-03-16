package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_apprenant_formation",
                columnNames = {"apprenant_id", "formation_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apprenant_id", nullable = false)
    private ApprenantEntity apprenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_id", nullable = false)
    private FormationEntity formation;

    @Column(name = "date_inscription", nullable = false)
    private LocalDateTime dateInscription;

    @Column(nullable = false)
    @Builder.Default
    private Float progression = 0f;

    @Column(name = "cours_total")
    @Builder.Default
    private Integer coursTotal = 0;

    @Column(name = "cours_termines")
    @Builder.Default
    private Integer coursTermines = 0;

    @Column(name = "dernier_activite")
    private LocalDateTime dernierActivite;

    // ── Paiement ──
    @Column(name = "statut_paiement", length = 20, nullable = false)
    @Builder.Default
    private String statutPaiement = "EN_ATTENTE";

    @Column(name = "methode_paiement", length = 20)
    private String methodePaiement;

    @Column(name = "reference_paiement", length = 100)
    private String referencePaiement;

    @Column(name = "montant_paye")
    private Double montantPaye;

    @Column(name = "date_paiement")
    private LocalDateTime datePaiement;

    @PrePersist
    public void prePersist() {
        if (dateInscription == null) dateInscription = LocalDateTime.now();
        if (statutPaiement  == null) statutPaiement  = "EN_ATTENTE";
    }
}
