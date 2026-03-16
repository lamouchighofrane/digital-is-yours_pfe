package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions_calendrier")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCalendrierEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apprenant_id", nullable = false)
    private ApprenantEntity apprenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_id")
    private FormationEntity formation;

    @Column(name = "titre_personnalise", length = 255, nullable = false)
    private String titrePersonnalise;

    @Column(name = "date_session", nullable = false)
    private LocalDateTime dateSession;

    @Column(name = "duree_minutes")
    private Integer dureeMinutes;

    @Column(name = "type_session", length = 20)
    private String typeSession;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "rappel_24h")
    private boolean rappel24h = true;

    @Column(name = "rappel_envoye")
    private boolean rappelEnvoye = false;

    @Column(name = "is_terminee")
    private boolean isTerminee = false;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = LocalDateTime.now();
    }
}
