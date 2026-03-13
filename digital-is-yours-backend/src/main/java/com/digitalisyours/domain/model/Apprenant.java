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
public class Apprenant {
    private Long id;
    private String prenom;
    private String nom;
    private String email;
    private String telephone;
    private String photo;
    private String motDePasse;
    private Role role;
    private boolean active;
    private LocalDateTime dateInscription;
    private LocalDateTime derniereConnexion;

    // Champs spécifiques apprenant (diagramme de classes)
    private String bio;
    private String niveauActuel;                     // DEBUTANT / INTERMEDIAIRE / AVANCE
    private List<String> domainesInteret;            // ["Marketing","Design","Dev"]
    private List<String> disponibilites;             // ["LUN","MAR","MER"]
    private String objectifsApprentissage;
    private Integer disponibilitesHeuresParSemaine;
}
