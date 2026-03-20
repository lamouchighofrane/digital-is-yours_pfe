package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursFormation {
    private Long    id;
    private String  titre;
    private String  description;
    private String  objectifs;
    private Integer dureeEstimee;
    private Integer ordre;
    private String  statut;
    private String  videoType;
    private String  videoUrl;
    private boolean hasVideo;
    private boolean hasQuiz;

    /** true si les 3 conditions sont remplies (video + doc + quiz) */
    private boolean estTermine;

    /** true si la vidéo a été vue */
    private boolean videoVue;

    /** true si le document a été ouvert */
    private boolean documentOuvert;

    /** true si le mini-quiz a été passé */
    private boolean quizPasse;

    /** Statut : A_FAIRE | EN_COURS | TERMINE */
    private String  statutProgression;
}
