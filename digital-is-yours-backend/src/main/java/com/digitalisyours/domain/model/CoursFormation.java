package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursFormation {
    private Long    id;
    private String  titre;
    private String  description;
    private String  objectifs;
    private Integer dureeEstimee;
    private Integer ordre;
    private String  statut;
    private String  videoType;

    /**
     * URL exposée à l'apprenant :
     * - YouTube → URL publique
     * - LOCAL   → null  (l'URL brute n'est jamais exposée ici)
     */
    private String  videoUrl;

    /** true si une vidéo est attachée au cours (peu importe le type) */
    private boolean hasVideo;
}
