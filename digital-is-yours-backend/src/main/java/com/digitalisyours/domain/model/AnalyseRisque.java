package com.digitalisyours.domain.model;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalyseRisque {
    private Long id;
    private Long apprenantId;
    private String apprenantEmail;
    private String apprenantPrenom;
    private String apprenantNom;
    private Long formationId;
    private String formationTitre;
    private String niveauRisque;      // FAIBLE | MOYEN | ELEVE
    private Float scoreRisque;        // 0-100
    private Integer joursInactivite;
    private Float progression;
    private Float scoreMoyenQuiz;
    private Integer nbQuizPasses;
    private Integer nbVideosVues;
    private Integer nbDocumentsOuverts;
    private String explication;
    private String recommandationIA;
    private boolean notificationEnvoyee;
    private boolean emailEnvoye;
    private LocalDateTime dateAnalyse;

}