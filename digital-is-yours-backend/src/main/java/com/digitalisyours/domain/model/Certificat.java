package com.digitalisyours.domain.model;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder
public class Certificat {
    private Long id;
    private String titre;
    private Float noteFinal;
    private LocalDateTime dateCreation;
    private String contextu;
    private String urlPDF;
    private Boolean estEnvoye;
    private Boolean partageLinkedIn;   // ← US-059
    private String numeroCertificat;

    // Apprenant
    private Long apprenantId;
    private String apprenantEmail;
    private String apprenantPrenom;
    private String apprenantNom;

    // Formation
    private Long formationId;
    private String formationTitre;
    private String formationNiveau;
    private Integer formationDuree;

    // Quiz
    private Long quizId;
    private Float notePassage;

    public String getNomCompletApprenant() {
        return (apprenantPrenom != null ? apprenantPrenom : "")
                + " "
                + (apprenantNom != null ? apprenantNom : "");
    }
}