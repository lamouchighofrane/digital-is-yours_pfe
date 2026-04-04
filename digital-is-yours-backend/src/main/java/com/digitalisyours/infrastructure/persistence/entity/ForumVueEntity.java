package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_vues",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vue_question_user",
                        columnNames = {"user_id", "question_id"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumVueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionForumEntity question;

    @Column(name = "date_vue", nullable = false)
    private LocalDateTime dateVue;

    @PrePersist
    public void prePersist() {
        if (dateVue == null) dateVue = LocalDateTime.now();
    }
}