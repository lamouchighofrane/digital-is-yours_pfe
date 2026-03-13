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

    @Column(name = "genere_par_ia", nullable = false)
    private Boolean genereParIA;

    @Column(name = "explication", columnDefinition = "TEXT")
    private String explication;

    @Column(name = "ordre")
    private Integer ordre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private QuizEntity quiz;

    // ← LAZY au lieu de EAGER : c'est la correction clé
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @OrderBy("ordre ASC")
    private List<OptionQuestionEntity> options = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.genereParIA == null) this.genereParIA = false;
    }
}
