package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reponses_forum",
        indexes = {
                @Index(name = "idx_rf_question", columnList = "question_id"),
                @Index(name = "idx_rf_auteur",   columnList = "auteur_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"likes", "documents", "reactions"})
@ToString(exclude = {"likes", "documents", "reactions"})
public class ReponsesForumEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false)
    private UserEntity auteur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionForumEntity question;

    @Column(name = "est_solution", nullable = false)
    @Builder.Default
    private boolean estSolution = false;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @OneToMany(mappedBy = "reponse", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ForumLikeEntity> likes = new ArrayList<>();

    // ── NOUVEAU : documents joints ─────────────────────────────────
    @OneToMany(mappedBy = "reponse", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ReponseDocumentEntity> documents = new ArrayList<>();

    // ── NOUVEAU : réactions emoji ──────────────────────────────────
    @OneToMany(mappedBy = "reponse", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ReponseReactionEntity> reactions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = LocalDateTime.now();
    }
}