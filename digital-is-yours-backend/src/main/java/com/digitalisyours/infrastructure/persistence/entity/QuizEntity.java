package com.digitalisyours.infrastructure.persistence.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", length = 20, nullable = false)
    private String type;

    @Column(name = "note_passage", nullable = false)
    private Float notePassage;

    @Column(name = "nombre_tentatives", nullable = false)
    private Integer nombreTentatives;

    @Column(name = "genere_par_ia", nullable = false)
    private Boolean genereParIA;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "niveau_difficulte", length = 20)
    private String niveauDifficulte;

    @Column(name = "inclure_definitions", nullable = false)
    @Builder.Default
    private Boolean inclureDefinitions = true;

    @Column(name = "inclure_cas_pratiques", nullable = false)
    @Builder.Default
    private Boolean inclureCasPratiques = true;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cours_id", nullable = false, unique = true)
    private CoursEntity cours;

    // ← LAZY : évite le MultipleBagFetchException
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuestionEntity> questions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.dateCreation == null)     this.dateCreation = LocalDateTime.now();
        if (this.type == null)             this.type = "MiniQuiz";
        if (this.notePassage == null)      this.notePassage = 70.0f;
        if (this.nombreTentatives == null) this.nombreTentatives = 3;
        if (this.genereParIA == null)      this.genereParIA = false;
        if (this.inclureDefinitions == null)   this.inclureDefinitions = true;
        if (this.inclureCasPratiques == null)  this.inclureCasPratiques = true;
    }
}
