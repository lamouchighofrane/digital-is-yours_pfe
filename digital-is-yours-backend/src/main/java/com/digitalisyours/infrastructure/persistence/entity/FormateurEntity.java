package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "formateurs")
@PrimaryKeyJoinColumn(name = "user_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FormateurEntity extends UserEntity {
    /**
     * Correspond à +bio : Text du diagramme Formateur.
     */
    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * Correspond à +competences : String du diagramme Formateur.
     * Stocké en JSON : ["Java", "Spring Boot", "Angular"]
     */
    @Column(columnDefinition = "TEXT")
    private String competences;

    /**
     * Correspond à +reseauxSociaux : JSON du diagramme Formateur.
     * Format JSON : {"linkedin":"...","twitter":"...","portfolio":"...","github":"..."}
     * Un seul champ, comme défini dans le diagramme.
     */
    @Column(name = "reseaux_sociaux", columnDefinition = "TEXT")
    private String reseauxSociaux;

    /**
     * Champ prévu par le client (hors diagramme actuel, sera ajouté).
     */
    @Column(length = 100)
    private String specialite;

    /**
     * Champ prévu par le client (hors diagramme actuel, sera ajouté).
     */
    private Integer anneesExperience;
}
