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
public class Document {
    private Long id;
    private String titre;
    private String nomFichier;
    private String url;
    private String typeFichier;
    private Long taille;
    private LocalDateTime dateAjout;
    private Long coursId;
}
