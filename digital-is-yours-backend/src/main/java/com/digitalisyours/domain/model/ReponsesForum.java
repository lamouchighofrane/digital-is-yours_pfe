package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReponsesForum {
    private Long id;
    private String contenu;
    private Long auteurId;
    private String auteurPrenom;
    private String auteurNom;
    private String auteurPhoto;
    private String auteurRole;        // APPRENANT | FORMATEUR
    private boolean estSolution;
    private int nombreLikes;
    private boolean likeParMoi;
    private LocalDateTime dateCreation;
    private Long questionId;
}