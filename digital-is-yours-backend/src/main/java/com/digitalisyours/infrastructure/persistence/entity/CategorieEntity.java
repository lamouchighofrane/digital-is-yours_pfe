package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorieEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;


    @Column(length = 20)
    private String couleur;  // Hex color

    @Column(name = "image_couverture", columnDefinition = "LONGTEXT")
    private String imageCouverture;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;  // SEO

    @Column(name = "ordre_affichage")
    private Integer ordreAffichage;

    @Column(name = "visible_catalogue", nullable = false)
    private Boolean visibleCatalogue;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        if (this.dateCreation == null) {
            this.dateCreation = LocalDateTime.now();
        }
        if (this.visibleCatalogue == null) {
            this.visibleCatalogue = true;
        }
        if (this.ordreAffichage == null) {
            this.ordreAffichage = 0;
        }
    }
}
