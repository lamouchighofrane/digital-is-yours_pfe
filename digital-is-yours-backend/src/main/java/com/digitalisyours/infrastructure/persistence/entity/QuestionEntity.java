package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "texte", columnDefinition = "TEXT", nullable = false)
    private String texte;

    /** true = question générée par IA (mapGenerateParIA du diagramme) */
    @Column(name = "genere_par_ia", nullable = false)
    private Boolean genereParIA;

    /** Explication affichée après correction */
    @Column(name = "explication", columnDefinition = "TEXT")
    private String explication;

    /** Ordre d'affichage dans le quiz */
    @Column(name = "ordre")
    private Integer ordre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private QuizEntity quiz;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    @OrderBy("ordre ASC")
    private List<OptionQuestionEntity> options = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.genereParIA == null) this.genereParIA = false;
    }
}
