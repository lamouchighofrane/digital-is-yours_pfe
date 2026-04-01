package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_likes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_like_question",
                        columnNames = {"user_id","question_id"}),
                @UniqueConstraint(name = "uk_like_reponse",
                        columnNames = {"user_id","reponse_id"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumLikeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private QuestionForumEntity question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reponse_id")
    private ReponsesForumEntity reponse;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = LocalDateTime.now();
    }
}