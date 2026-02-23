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
public class User {
    private Long id;
    private String prenom;
    private String nom;
    private String email;
    private String telephone;
    private String motDePasse;
    private Role role;
    private boolean emailVerifie;
    private boolean active;
    private LocalDateTime dateInscription;
    private LocalDateTime derniereConnexion;
}
