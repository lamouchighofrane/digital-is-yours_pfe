package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "seances_en_ligne")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_id", nullable = false)
    private FormationEntity formation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formateur_id", nullable = false)
    private UserEntity formateur;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(name = "date_seance", nullable = false)
    private LocalDateTime dateSeance;

    @Column(name = "duree_minutes")
    @Builder.Default
    private Integer dureeMinutes = 60;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "lien_jitsi", length = 500)
    private String lienJitsi;

    @Column(name = "room_name", length = 255)
    private String roomName;

    @Column(length = 20)
    @Builder.Default
    private String statut = "PLANIFIEE";

    @Column(name = "notif_envoyee")
    @Builder.Default
    private Boolean notifEnvoyee = false;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = LocalDateTime.now();
        if (statut == null) statut = "PLANIFIEE";
        if (notifEnvoyee == null) notifEnvoyee = false;
        if (dureeMinutes == null) dureeMinutes = 60;
    }
}