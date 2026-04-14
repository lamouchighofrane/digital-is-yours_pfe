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
public class SoumissionQuizFinal {

    /** Email de l'apprenant extrait du JWT */
    private String email;

    /** ID de la formation (pour vérifier inscription + récupérer le quiz) */
    private Long formationId;

    /**
     * Réponses soumises : questionId → optionId choisie
     * Si une question n'a pas de réponse, elle ne figure pas dans la map
     */
    private Map<Long, Long> reponses;

    /** Temps passé en secondes */
    private Integer tempsPasse;

    /**
     * Rapport de fraude généré par le service Angular.
     * Peut être null si l'apprenant n'a commis aucune infraction
     * ou si le frontend n'envoie pas de rapport.
     */
    private RapportFraude rapportFraude;
}