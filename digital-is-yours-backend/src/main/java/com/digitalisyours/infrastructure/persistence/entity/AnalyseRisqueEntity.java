package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "analyses_risque",
        indexes = {
                @Index(name = "idx_ar_apprenant", columnList = "apprenant_id"),
                @Index(name = "idx_ar_formation", columnList = "formation_id")
        })
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalyseRisqueEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apprenant_id", nullable = false)
    private Long apprenantId;

    @Column(name = "apprenant_email", nullable = false, length = 255)
    private String apprenantEmail;

    @Column(name = "formation_id", nullable = false)
    private Long formationId;

    /** FAIBLE | MOYEN | ELEVE */
    @Column(name = "niveau_risque", length = 20, nullable = false)
    private String niveauRisque;

    @Column(name = "score_risque")
    private Float scoreRisque;

    @Column(name = "jours_inactivite")
    private Integer joursInactivite;

    @Column(name = "progression")
    private Float progression;

    @Column(name = "score_moyen_quiz")
    private Float scoreMoyenQuiz;

    @Column(name = "nb_quiz_passes")
    private Integer nbQuizPasses;

    @Column(name = "nb_videos_vues")
    private Integer nbVideosVues;

    @Column(name = "nb_documents_ouverts")
    private Integer nbDocumentsOuverts;

    @Column(name = "explication", columnDefinition = "TEXT")
    private String explication;

    @Column(name = "recommandation_ia", columnDefinition = "TEXT")
    private String recommandationIA;

    @Column(name = "notification_envoyee")
    private boolean notificationEnvoyee;

    @Column(name = "email_envoye")
    private boolean emailEnvoye;

    @Column(name = "date_analyse", nullable = false)
    private LocalDateTime dateAnalyse;

    @Column(name = "formation_titre", length = 500)
    private String formationTitre;

    @PrePersist
    public void prePersist() {
        if (this.dateAnalyse == null) this.dateAnalyse = LocalDateTime.now();
    }
}