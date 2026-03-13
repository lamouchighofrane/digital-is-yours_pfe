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
@Table(name = "apprenants")
@PrimaryKeyJoinColumn(name = "user_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprenantEntity extends UserEntity {
    /**
     * Correspond à +bio : Text du diagramme Apprenant.
     */
    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * Correspond à +niveauActuel : Enum du diagramme Apprenant.
     * Valeurs : DEBUTANT / INTERMEDIAIRE / AVANCE
     */
    @Column(name = "niveau_actuel", length = 20)
    private String niveauActuel;

    /**
     * Correspond à +domainesInteret : String du diagramme Apprenant.
     * Stocké en JSON : ["Marketing", "Design", "Développement"]
     */
    @Column(name = "domaines_interet", columnDefinition = "TEXT")
    private String domainesInteret;

    /**
     * Correspond à +disponibilites : Integer du diagramme Apprenant.
     * Stocké en JSON : ["LUN", "MAR", "MER"]
     */
    @Column(name = "disponibilites", columnDefinition = "TEXT")
    private String disponibilites;

    /**
     * Correspond à +objectifsApprentissage : Text du diagramme Apprenant.
     */
    @Column(name = "objectifs_apprentissage", columnDefinition = "TEXT")
    private String objectifsApprentissage;

    /**
     * Heures disponibles par semaine.
     */
    @Column(name = "disponibilites_heures_par_semaine")
    private Integer disponibilitesHeuresParSemaine;
}
