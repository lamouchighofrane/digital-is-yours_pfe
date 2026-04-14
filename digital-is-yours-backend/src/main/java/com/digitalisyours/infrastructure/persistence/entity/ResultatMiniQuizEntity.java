package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "resultats_mini_quiz",
        indexes = {
                @Index(name = "idx_rq_apprenant_quiz",   columnList = "apprenant_email, quiz_id"),
                @Index(name = "idx_rq_formation",         columnList = "formation_id"),
                @Index(name = "idx_rq_cours",             columnList = "cours_id"),
                @Index(name = "idx_rq_suspect",           columnList = "suspect_fraude")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultatMiniQuizEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apprenant_email", nullable = false, length = 255)
    private String apprenantEmail;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "cours_id")
    private Long coursId;

    @Column(name = "formation_id")
    private Long formationId;

    // ── Score ────────────────────────────────────────────────────

    /**
     * Score brut AVANT application du malus (0-100).
     */
    @Column(name = "score_brut", nullable = false)
    private Float scoreBrut;

    /**
     * Malus appliqué en points (0, 10 ou 20).
     */
    @Column(name = "penalite_appliquee", nullable = false)
    @Builder.Default
    private Integer penaliteAppliquee = 0;

    /**
     * Score FINAL après malus, affiché à l'apprenant.
     */
    @Column(name = "score", nullable = false)
    private Float score;

    @Column(name = "nombre_bonnes_reponses")
    private Integer nombreBonnesReponses;

    @Column(name = "nombre_questions")
    private Integer nombreQuestions;

    @Column(name = "temps_passe")
    private Integer tempsPasse;

    @Column(name = "reussi", nullable = false)
    private boolean reussi;

    @Column(name = "note_passage")
    private Float notePassage;

    @Column(name = "tentative_numero")
    private Integer tentativeNumero;

    @Column(name = "date_passage", nullable = false)
    private LocalDateTime datePassage;

    // ── Anti-fraude ──────────────────────────────────────────────

    /**
     * Nombre total d'infractions détectées pendant le mini-quiz.
     */
    @Column(name = "nb_infractions", nullable = false)
    @Builder.Default
    private Integer nbInfractions = 0;

    /**
     * true si au moins 1 infraction détectée.
     */
    @Column(name = "suspect_fraude", nullable = false)
    @Builder.Default
    private Boolean suspectFraude = false;

    /**
     * Liste horodatée JSON de chaque infraction.
     */
    @Column(name = "detail_infractions", columnDefinition = "TEXT")
    private String detailInfractions;

    @PrePersist
    public void prePersist() {
        if (this.datePassage == null)       this.datePassage = LocalDateTime.now();
        if (this.penaliteAppliquee == null)  this.penaliteAppliquee = 0;
        if (this.nbInfractions == null)      this.nbInfractions = 0;
        if (this.suspectFraude == null)      this.suspectFraude = false;
        if (this.scoreBrut == null)          this.scoreBrut = this.score;
    }
}