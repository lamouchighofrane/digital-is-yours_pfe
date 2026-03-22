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
    private String apprenantEmail;   // utilisé pour la persistence
    private Long   quizId;
    private Long   formationId;

    private Float   score;                 // score en %
    private Integer nombreBonnesReponses;
    private Integer nombreQuestions;
    private Integer tempsPasse;            // en secondes
    private Boolean reussi;
    private Float   notePassage;
    private Integer tentativeNumero;
    private Integer tentativesRestantes;

    private LocalDateTime datePassage;

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