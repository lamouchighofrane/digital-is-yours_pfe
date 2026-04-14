package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultatQuizFinal {

    private Long   id;
    private Long   apprenantId;
    private String apprenantEmail;
    private Long   quizId;
    private Long   formationId;

    // ── Score ────────────────────────────────────────────────────

    /** Score brut avant malus anti-fraude */
    private Float   scoreBrut;

    /** Malus appliqué (0, 10 ou 20 points) */
    private Integer penaliteAppliquee;

    /** Score final après malus — affiché à l'apprenant */
    private Float   score;

    private Integer nombreBonnesReponses;
    private Integer nombreQuestions;
    private Integer tempsPasse;
    private Boolean reussi;
    private Float   notePassage;
    private Integer tentativeNumero;
    private Integer tentativesRestantes;
    private LocalDateTime datePassage;

    // ── Anti-fraude ──────────────────────────────────────────────

    /** Nombre total d'infractions détectées */
    private Integer nbInfractions;

    /** true si au moins 1 infraction */
    private Boolean suspectFraude;

    /** JSON des infractions détaillées */
    private String  detailInfractions;

    /** Détail des réponses pour la correction affichée à l'apprenant */
    private List<ReponseDetail> reponses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReponseDetail {
        private Long    questionId;
        private String  questionTexte;
        private Long    optionChoisieId;
        private String  optionChoisieTexte;
        private Boolean estCorrecte;
        private String  explication;
        private String  bonneReponseTexte;
    }
}