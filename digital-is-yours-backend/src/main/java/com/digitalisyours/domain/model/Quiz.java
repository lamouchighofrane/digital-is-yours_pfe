package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quiz {
    private Long id;
    private String type;                  // "MiniQuiz" | "QuizFinal"
    private Float notePassage;
    private Integer nombreTentatives;
    private Boolean genereParIA;
    private String niveauDifficulte;      // "FACILE" | "MOYEN" | "DIFFICILE"
    private Boolean inclureDefinitions;
    private Boolean inclureCasPratiques;
    private LocalDateTime dateCreation;

    // ── Cours associé (MiniQuiz) ────────────────────────────
    private Long coursId;
    private String coursTitre;
    private String coursDescription;
    private String coursObjectifs;
    private String videoType;
    private String videoUrl;

    // ── Formation associée (QuizFinal) ──────────────────────
    private Long formationId;
    private String formationTitre;

    // ── Chronomètre (QuizFinal) ─────────────────────────────
    private Integer dureeMinutes;         // Durée totale du quiz en minutes

    @Builder.Default
    private List<Question> questions = new ArrayList<>();
}
