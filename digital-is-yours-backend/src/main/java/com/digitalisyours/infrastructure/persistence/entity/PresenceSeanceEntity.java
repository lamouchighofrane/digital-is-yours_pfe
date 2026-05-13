package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "presence_seance",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"seance_id", "apprenant_email"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceSeanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seance_id", nullable = false)
    private Long seanceId;

    @Column(name = "apprenant_email", nullable = false)
    private String apprenantEmail;

    @Column(name = "apprenant_nom")
    private String apprenantNom;

    @Column(name = "apprenant_prenom")
    private String apprenantPrenom;

    @Column(name = "date_rejoindre")
    private LocalDateTime dateRejoindre;

    @Column(name = "date_quitter")
    private LocalDateTime dateQuitter;

    @Column(name = "present", nullable = false)
    private boolean present = false;

    @Column(name = "duree_minutes")
    private Integer dureeMinutes;
}