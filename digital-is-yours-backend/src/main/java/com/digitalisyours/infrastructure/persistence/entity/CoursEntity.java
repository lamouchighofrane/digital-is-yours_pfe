package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cours")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duree_estimee")
    private Integer dureeEstimee; // en minutes

    @Column(name = "ordre")
    private Integer ordre;

    @Column(name = "objectifs", columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "statut", length = 20, nullable = false)
    private String statut; // BROUILLON | PUBLIE

    // ── Vidéo — attributs directs selon diagramme de classes ──────────────
    /**
     * videoType : Enum (Local, YouTube)
     * Stocké en VARCHAR. Valeurs : "LOCAL", "YOUTUBE", null (pas de vidéo).
     */
    @Column(name = "video_type", length = 20)
    private String videoType;

    /**
     * videoUrl : String
     * Si LOCAL  → nom du fichier stocké sur le serveur (ex: "abc123.mp4")
     * Si YOUTUBE → URL complète YouTube (ex: "https://www.youtube.com/watch?v=XXXXXXXXXXX")
     */
    @Column(name = "video_url", length = 1000)
    private String videoUrl;
    // ──────────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_id", nullable = false)
    private FormationEntity formation;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @PrePersist
    public void prePersist() {
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
        if (this.statut == null)       this.statut = "BROUILLON";
        if (this.ordre == null)        this.ordre = 0;
        if (this.dureeEstimee == null) this.dureeEstimee = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.dateModification = LocalDateTime.now();
    }
}
