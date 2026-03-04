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
public class Cours {
    private Long id;
    private String titre;
    private String description;
    private Integer dureeEstimee;     // en minutes
    private Integer ordre;
    private String objectifs;
    private String statut;            // BROUILLON | PUBLIE

    // ── Vidéo — attributs directs selon diagramme ──
    private String videoType;         // LOCAL | YOUTUBE  (null si aucune vidéo)
    private String videoUrl;          // URL YouTube ou nom du fichier local

    private Long formationId;
    private String formationTitre;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
}
