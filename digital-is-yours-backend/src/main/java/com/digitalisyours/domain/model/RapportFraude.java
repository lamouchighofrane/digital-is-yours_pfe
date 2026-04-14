package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rapport de fraude généré par le service Angular
 * pendant le quiz et envoyé avec la soumission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportFraude {

    /** Nombre total d'infractions cumulées pendant le quiz */
    private int nombreInfractions;

    /** Détail horodaté de chaque infraction */
    private List<Infraction> infractions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Infraction {

        /**
         * Type d'infraction :
         * "onglet_quitte" | "copie" | "raccourci" | "plein_ecran"
         */
        private String type;

        /**
         * Message exact affiché à l'apprenant au moment de l'infraction.
         * Ex : "Vous avez quitté l'onglet du quiz. (2ème fois)"
         */
        private String message;

        /** Horodatage ISO-8601 de l'infraction */
        private String horodatage;
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Calcule le malus à appliquer sur le score.
     * 0 infraction  → 0 point de malus
     * 1-2 infractions → 10 points de malus
     * 3+  infractions → 20 points de malus
     */
    public int calculerMalus() {
        if (nombreInfractions == 0) return 0;
        if (nombreInfractions <= 2) return 10;
        return 20;
    }

    /**
     * Indique si l'apprenant est suspect (au moins 1 infraction).
     */
    public boolean estSuspect() {
        return nombreInfractions > 0;
    }
}