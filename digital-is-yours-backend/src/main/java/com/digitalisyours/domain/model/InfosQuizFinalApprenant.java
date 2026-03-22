package com.digitalisyours.domain.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Informations du quiz final retournées à l'apprenant :
 * métadonnées + dernier résultat éventuel.
 * Les bonnes réponses (estCorrecte) ne sont jamais exposées ici.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfosQuizFinalApprenant {

    private boolean existe;

    // ── Paramètres du quiz ───────────────────────────────
    private Long    quizId;
    private Float   notePassage;
    private Integer nombreTentatives;
    private Integer tentativesUtilisees;
    private Integer tentativesRestantes;
    private Integer dureeMinutes;
    private Integer nbQuestions;
    private boolean peutPasser;

    // ── Quiz avec questions (sans estCorrecte) ───────────
    private Quiz quiz;

    // ── Dernier résultat éventuel ────────────────────────
    private DernierResultat dernierResultat;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DernierResultat {
        private Float         score;
        private Boolean       reussi;
        private Integer       nombreBonnesReponses;
        private Integer       nombreQuestions;
        private LocalDateTime datePassage;
        private Integer       tentativeNumero;
    }
}