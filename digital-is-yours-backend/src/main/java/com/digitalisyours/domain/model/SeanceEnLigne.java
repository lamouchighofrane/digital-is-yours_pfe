package com.digitalisyours.domain.model;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeanceEnLigne {
    private Long id;
    private Long formationId;
    private String formationTitre;
    private Long formateurId;
    private String formateurNom;
    private String formateurPrenom;

    private String titre;
    private LocalDateTime dateSeance;
    private Integer dureeMinutes;
    private String description;
    private String lienJitsi;       // URL complète : https://meet.jit.si/DIY-xxx
    private String roomName;        // Nom de la salle Jitsi seul
    private String statut;          // PLANIFIEE | EN_COURS | TERMINEE | ANNULEE

    private Boolean notifEnvoyee;
    private LocalDateTime dateCreation;
}