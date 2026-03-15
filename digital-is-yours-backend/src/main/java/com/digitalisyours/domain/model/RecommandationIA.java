package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommandationIA {
    private Long      formationId;
    private String    titre;
    private String    niveau;
    private String    description;
    private String    imageCouverture;
    private String    categorie;
    private Integer   dureeEstimee;
    private Float     noteMoyenne;
    private Integer   nombreInscrits;

    // Scoring IA
    private int    scoreCompatibilite;   // 0-100
    private String raison;               // Explication personnalisée
    private String pointsForts;
}
