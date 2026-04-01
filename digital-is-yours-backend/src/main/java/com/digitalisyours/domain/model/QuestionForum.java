package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionForum {
    private Long id;
    private String titre;
    private String contenu;
    private Long auteurId;
    private String auteurPrenom;
    private String auteurNom;
    private String auteurPhoto;
    private Long formationId;
    private String formationTitre;
    private String statut;            // NON_REPONDU | REPONDU | RESOLU
    private int nombreReponses;
    private int nombreVues;
    private int nombreLikes;
    private boolean likeParMoi;
    private LocalDateTime dateCreation;
    private List<String> tags;
    private List<ReponsesForum> reponses;
}