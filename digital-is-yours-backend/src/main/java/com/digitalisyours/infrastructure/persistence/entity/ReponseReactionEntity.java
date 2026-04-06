package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reponse_reactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reaction_user_reponse_emoji",
                columnNames = {"user_id", "reponse_id", "emoji"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReponseReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reponse_id", nullable = false)
    private ReponsesForumEntity reponse;

    // "👍", "❤️", "🙏"
    @Column(nullable = false, length = 10)
    private String emoji;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) dateCreation = LocalDateTime.now();
    }
}