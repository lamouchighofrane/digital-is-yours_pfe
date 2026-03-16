package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inscription {
    private Long id;

    // Apprenant
    private Long apprenantId;

    // Formation
    private Long formationId;
    private String formationTitre;
    private String formationImage;
    private String formationNiveau;
    private Double formationPrix;

    // Progression
    private LocalDateTime dateInscription;
    private Float progression;
    private Integer coursTotal;
    private Integer coursTermines;
    private LocalDateTime dernierActivite;

    // Paiement
    private String statutPaiement;   // EN_ATTENTE | PAYE | ECHEC
    private String methodePaiement;  // CARTE
    private String referencePaiement;
    private Double montantPaye;
    private LocalDateTime datePaiement;
}
