package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Titre affiché dans l'interface */
    @Column(nullable = false, length = 255)
    private String titre;

    /** Nom original du fichier (ex: "cours-seo.pdf") */
    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    /** Nom UUID stocké sur le serveur (ex: "a3f2...pdf") */
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** Type MIME (application/pdf, etc.) */
    @Column(name = "type_fichier", length = 100)
    private String typeFichier;

    /** Taille en octets */
    @Column(name = "taille")
    private Long taille;

    @Column(name = "date_ajout", nullable = false)
    private LocalDateTime dateAjout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cours_id", nullable = false)
    private CoursEntity cours;

    @PrePersist
    public void prePersist() {
        if (this.dateAjout == null) this.dateAjout = LocalDateTime.now();
    }
}
