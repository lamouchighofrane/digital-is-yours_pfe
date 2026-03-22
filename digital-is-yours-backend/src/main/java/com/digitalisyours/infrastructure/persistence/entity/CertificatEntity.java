package com.digitalisyours.infrastructure.persistence.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "certificats",
        indexes = {
                @Index(name = "idx_cert_apprenant", columnList = "apprenant_id"),
                @Index(name = "idx_cert_formation", columnList = "formation_id"),
                @Index(name = "idx_cert_numero", columnList = "numero_certificat", unique = true)
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CertificatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "note_final", nullable = false)
    private Float noteFinal;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "contextu", columnDefinition = "TEXT")
    private String contextu;

    @Column(name = "url_pdf")
    private String urlPDF;

    @Column(name = "est_envoye", nullable = false)
    private Boolean estEnvoye = false;

    @Column(name = "numero_certificat", unique = true, nullable = false)
    private String numeroCertificat;

    // FK apprenant
    @Column(name = "apprenant_id", nullable = false)
    private Long apprenantId;

    @Column(name = "apprenant_email", nullable = false)
    private String apprenantEmail;

    @Column(name = "apprenant_prenom")
    private String apprenantPrenom;

    @Column(name = "apprenant_nom")
    private String apprenantNom;

    // FK formation
    @Column(name = "formation_id", nullable = false)
    private Long formationId;

    @Column(name = "formation_titre")
    private String formationTitre;

    @Column(name = "formation_niveau")
    private String formationNiveau;

    @Column(name = "formation_duree")
    private Integer formationDuree;

    // FK quiz
    @Column(name = "quiz_id")
    private Long quizId;

    @Column(name = "note_passage")
    private Float notePassage;
}
