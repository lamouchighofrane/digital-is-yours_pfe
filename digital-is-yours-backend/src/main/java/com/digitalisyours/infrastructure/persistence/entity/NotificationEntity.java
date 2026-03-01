package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Destinataire de la notification
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // Type : FORMATION_AFFECTEE, FORMATION_RETIREE, etc.
    @Column(nullable = false, length = 50)
    private String type;

    // Titre court affiché dans la cloche
    @Column(nullable = false, length = 255)
    private String titre;

    // Message détaillé
    @Column(columnDefinition = "TEXT")
    private String message;

    // Lien vers la ressource concernée (optionnel)
    @Column(name = "formation_id")
    private Long formationId;

    @Column(name = "formation_titre", length = 255)
    private String formationTitre;

    @Column(name = "lu", nullable = false)
    private boolean lu = false;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        if (this.dateCreation == null) {
            this.dateCreation = LocalDateTime.now();
        }
        this.lu = false;
    }
}
