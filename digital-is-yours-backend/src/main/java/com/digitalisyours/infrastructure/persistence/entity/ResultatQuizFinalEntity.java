package com.digitalisyours.infrastructure.persistence.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA — table resultats_quiz_final
 * Stocke chaque tentative de l'apprenant sur le quiz final.
 * Correspond à la classe ResultatQuiz du diagramme de classes.
 */
@Entity
@Table(name = "resultats_quiz_final",
        indexes = {
                @Index(name = "idx_rqf_apprenant_quiz",  columnList = "apprenant_email, quiz_id"),
                @Index(name = "idx_rqf_formation",        columnList = "formation_id")
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

    /** Score en pourcentage (0-100) */
    @Column(name = "score", nullable = false)
    private Float score;

    /** Nombre de bonnes réponses */
    @Column(name = "nombre_bonnes_reponses")
    private Integer nombreBonnesReponses;

    /** Nombre total de questions */
    @Column(name = "nombre_questions")
    private Integer nombreQuestions;

    /** Temps passé en secondes */
    @Column(name = "temps_passe")
    private Integer tempsPasse;

    /** Vrai si score >= notePassage */
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

    @PrePersist
    public void prePersist() {
        if (this.datePassage == null) this.datePassage = LocalDateTime.now();
    }
}
