package com.digitalisyours.infrastructure.persistence.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "formations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "objectifs_apprentissage", columnDefinition = "TEXT")
    private String objectifsApprentissage;

    @Column(columnDefinition = "TEXT")
    private String prerequis;

    @Column(name = "pour_qui", columnDefinition = "TEXT")
    private String pourQui;

    @Column(name = "image_couverture", columnDefinition = "LONGTEXT")
    private String imageCouverture;

    @Column(name = "duree_estimee")
    private Integer dureeEstimee;

    @Column(length = 50)
    private String niveau;

    @Column(length = 20, nullable = false)
    private String statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private CategorieEntity categorie;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "formation_competences",
            joinColumns = @JoinColumn(name = "formation_id"),
            inverseJoinColumns = @JoinColumn(name = "competence_id")
    )
    @Builder.Default
    private Set<CompetenceEntity> competences = new HashSet<>();

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_publication")
    private LocalDateTime datePublication;

    @Column(name = "nombre_inscrits")
    private Integer nombreInscrits;

    @Column(name = "nombre_certifies")
    private Integer nombreCertifies;

    @Column(name = "note_moyenne")
    private Float noteMoyenne;

    @Column(name = "taux_reussite")
    private Float tauxReussite;

    @PrePersist
    public void prePersist() {
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
        if (this.statut == null) this.statut = "BROUILLON";
        if (this.nombreInscrits == null) this.nombreInscrits = 0;
        if (this.nombreCertifies == null) this.nombreCertifies = 0;
        if (this.dureeEstimee == null) this.dureeEstimee = 1;
    }
}
