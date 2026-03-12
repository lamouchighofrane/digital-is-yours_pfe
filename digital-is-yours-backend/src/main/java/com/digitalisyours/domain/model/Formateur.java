package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Formateur {
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

    // Champs spécifiques formateur
    private String bio;
    private String specialite;
    private Integer anneesExperience;
    private List<String> competences;
    private Map<String, String> reseauxSociaux;
}
