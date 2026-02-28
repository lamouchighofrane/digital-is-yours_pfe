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
public class Formation {
    private Long id;
    private String titre;
    private String description;
    private String objectifsApprentissage;
    private String prerequis;
    private String pourQui;
    private String imageCouverture;
    private Integer dureeEstimee;
    private String niveau;
    private String statut;

    // Catégorie
    private Long categorieId;
    private String categorieNom;

    // ★★★ AJOUT : Formateur ★★★
    private Long formateurId;
    private String formateurNom;
    private String formateurPrenom;
    private String formateurEmail;

    // Stats
    private LocalDateTime dateCreation;
    private LocalDateTime datePublication;
    private Integer nombreInscrits;
    private Integer nombreCertifies;
    private Float noteMoyenne;
    private Float tauxReussite;

}
