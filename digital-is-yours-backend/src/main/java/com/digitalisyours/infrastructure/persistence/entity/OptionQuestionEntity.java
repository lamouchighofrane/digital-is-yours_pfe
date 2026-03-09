package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "options_question")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionQuestionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "texte", columnDefinition = "TEXT", nullable = false)
    private String texte;

    @Column(name = "est_correcte", nullable = false)
    private Boolean estCorrecte;

    /** A, B, C ou D (char selon diagramme) */
    @Column(name = "ordre", length = 2)
    private String ordre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;
}
