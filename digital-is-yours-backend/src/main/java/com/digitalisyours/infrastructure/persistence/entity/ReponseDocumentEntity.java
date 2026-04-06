package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reponse_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReponseDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reponse_id", nullable = false)
    private ReponsesForumEntity reponse;

    @Column(nullable = false, length = 255)
    private String nomFichier;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String typeFichier;

    private Long taille;

    @Column(name = "date_ajout", nullable = false)
    private LocalDateTime dateAjout;

    @PrePersist
    public void prePersist() {
        if (dateAjout == null) dateAjout = LocalDateTime.now();
    }
}