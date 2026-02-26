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
public class Categorie {
    private Long id;
    private String nom;
    private String description;
    private String icone;
    private String couleur;
    private String imageCouverture;
    private String metaDescription;
    private Integer ordreAffichage;
    private Boolean visibleCatalogue;
    private LocalDateTime dateCreation;
}
