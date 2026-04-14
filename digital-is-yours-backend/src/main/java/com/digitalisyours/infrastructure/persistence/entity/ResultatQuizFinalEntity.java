package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "resultats_quiz_final",
        indexes = {
                @Index(name = "idx_rqf_apprenant_quiz",  columnList = "apprenant_email, quiz_id"),
                @Index(name = "idx_rqf_formation",        columnList = "formation_id"),
                @Index(name = "idx_rqf_suspect",          columnList = "suspect_fraude")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultatQuizFinalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email de l'apprenant (dénormalisé pour performance) */
    @Column(name = "apprenant_email", nullable = false, length = 255)
    private String apprenantEmail;

    /** ID du quiz passé */
    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    /** ID de la formation */
    @Column(name = "formation_id", nullable = false)
    private Long formationId;

    // ── Score ────────────────────────────────────────────────────

    /**
     * Score brut AVANT application du malus anti-fraude (0-100).
     * Identique à score si aucune infraction.
     */
    @Column(name = "score_brut", nullable = false)
    private Float scoreBrut;

    /**
     * Malus appliqué en points (0, 10 ou 20).
     * 0 infraction → 0 | 1-2 infractions → 10 | 3+ → 20
     */
    @Column(name = "penalite_appliquee", nullable = false)
    @Builder.Default
    private Integer penaliteAppliquee = 0;

    /**
     * Score FINAL après malus (= scoreBrut - penaliteAppliquee).
     * C'est ce score qui est affiché à l'apprenant et utilisé
     * pour déterminer réussi/échoué.
     */
    @Column(name = "score", nullable = false)
    private Float score;

    /** Nombre total de bonnes réponses */
    @Column(name = "nombre_bonnes_reponses")
    private Integer nombreBonnesReponses;

    /** Nombre total de questions */
    @Column(name = "nombre_questions")
    private Integer nombreQuestions;

    /** Temps passé en secondes */
    @Column(name = "temps_passe")
    private Integer tempsPasse;

    /** Vrai si score (après malus) >= notePassage */
    @Column(name = "reussi", nullable = false)
    private boolean reussi;

    /** Note de passage au moment du passage */
    @Column(name = "note_passage")
    private Float notePassage;

    /** Numéro de la tentative (1, 2, 3…) */
    @Column(name = "tentative_numero")
    private Integer tentativeNumero;

    /** Date et heure de passage */
    @Column(name = "date_passage", nullable = false)
    private LocalDateTime datePassage;

    // ── Anti-fraude ──────────────────────────────────────────────

    /**
     * Nombre total d'infractions détectées pendant le quiz.
     * 0 = aucune infraction détectée.
     */
    @Column(name = "nb_infractions", nullable = false)
    @Builder.Default
    private Integer nbInfractions = 0;

    /**
     * true si au moins 1 infraction détectée.
     * Sert de filtre rapide pour le dashboard admin.
     */
    @Column(name = "suspect_fraude", nullable = false)
    @Builder.Default
    private Boolean suspectFraude = false;

    /**
     * Liste horodatée de chaque infraction au format JSON.
     * Ex : [{"type":"onglet_quitte","message":"Vous avez quitté...","horodatage":"..."}]
     * Stocké en TEXT pour compatibilité MySQL.
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