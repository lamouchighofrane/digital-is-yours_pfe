package com.digitalisyours.infrastructure.persistence.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "competences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String nom;

    @Column(length = 200)
    private String categorie;

    @JsonIgnore
    @ManyToMany(mappedBy = "competences", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<FormationEntity> formations = new HashSet<>();
}
