package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "progression_cours",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_apprenant_cours",
                columnNames = {"apprenant_id", "cours_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressionCoursEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apprenant_id", nullable = false)
    private ApprenantEntity apprenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cours_id", nullable = false)
    private CoursEntity cours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_id", nullable = false)
    private FormationEntity formation;

    /** Statut : A_FAIRE | EN_COURS | TERMINE */
    @Column(name = "statut", length = 20, nullable = false)
    @Builder.Default
    private String statut = "A_FAIRE";

    /** true quand l'apprenant a cliqué "Marquer comme vu" */
    @Column(name = "video_vue", nullable = false)
    @Builder.Default
    private boolean videoVue = false;

    /** true quand l'apprenant a ouvert le document */
    @Column(name = "document_ouvert", nullable = false)
    @Builder.Default
    private boolean documentOuvert = false;

    /** true quand l'apprenant a soumis le mini-quiz */
    @Column(name = "quiz_passe", nullable = false)
    @Builder.Default
    private boolean quizPasse = false;

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    @PrePersist
    public void prePersist() {
        if (this.dateDebut == null) this.dateDebut = LocalDateTime.now();
        if (this.statut    == null) this.statut    = "A_FAIRE";
    }

    /** Recalcule le statut en fonction des 3 conditions */
    public void recalculerStatut() {
        if (this.videoVue && this.documentOuvert && this.quizPasse) {
            this.statut  = "TERMINE";
            if (this.dateFin == null) this.dateFin = LocalDateTime.now();
        } else if (this.videoVue || this.documentOuvert || this.quizPasse) {
            this.statut = "EN_COURS";
        } else {
            this.statut = "A_FAIRE";
        }
    }
}
