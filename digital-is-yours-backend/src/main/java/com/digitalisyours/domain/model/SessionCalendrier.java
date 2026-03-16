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
public class SessionCalendrier {
    private Long id;
    private Long apprenantId;
    private Long formationId;
    private String formationTitre;
    private String titrePersonnalise;
    private LocalDateTime dateSession;
    private Integer dureeMinutes;
    private String typeSession; // COURS, QUIZ, EVENEMENT
    private String notes;
    private boolean rappel24h;
    private boolean rappelEnvoye;
    private boolean isTerminee;
    private LocalDateTime dateCreation;
}
