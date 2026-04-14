package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoumissionMiniQuiz {

    /** Email de l'apprenant extrait du JWT */
    private String email;

    /** ID de la formation (pour vérifier inscription) */
    private Long formationId;

    /** ID du cours dont on passe le quiz */
    private Long coursId;

    /**
     * Réponses soumises : questionId → optionId choisie
     * Si une question n'a pas de réponse, elle ne figure pas dans la map
     */
    private Map<Long, Long> reponses;

    /** Temps passé en secondes */
    private Integer tempsPasse;

    /**
     * Rapport de fraude généré par le service Angular.
     * Peut être null si aucune infraction ou si non envoyé.
     */
    private RapportFraude rapportFraude;
}