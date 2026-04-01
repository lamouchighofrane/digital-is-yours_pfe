package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions_forum",
        indexes = {
                @Index(name = "idx_qf_auteur",    columnList = "auteur_id"),
                @Index(name = "idx_qf_formation", columnList = "formation_id"),
                @Index(name = "idx_qf_statut",    columnList = "statut"),
                @Index(name = "idx_qf_date",      columnList = "date_creation")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"reponses","likes"})
@ToString(exclude = {"reponses","likes"})
public class QuestionForumEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false)
    private UserEntity auteur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_id")
    private FormationEntity formation;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "NON_REPONDU";

    @Column(name = "nombre_vues", nullable = false)
    @Builder.Default
    private int nombreVues = 0;

    @Column(name = "tags", length = 500)
    private String tags; // JSON array stocké en string

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dateCreation ASC")
    @Builder.Default
    private List<ReponsesForumEntity> reponses = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ForumLikeEntity> likes = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = LocalDateTime.now();
        if (statut == null)       statut       = "NON_REPONDU";
        if (nombreVues < 0)       nombreVues   = 0;
    }
}