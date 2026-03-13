package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Categorie;
import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.port.in.CategorieUseCase;
import com.digitalisyours.domain.port.in.FormationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PublicFormationController {
    private final FormationUseCase formationUseCase;
    private final CategorieUseCase categorieUseCase;

    @GetMapping("/formations")
    public ResponseEntity<List<Formation>> getFormationsPubliees() {
        List<Formation> publiees = formationUseCase.getAllFormations()
                .stream()
                .filter(f -> "PUBLIE".equals(f.getStatut()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(publiees);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Categorie>> getCategoriesVisibles() {
        List<Categorie> visibles = categorieUseCase.getAllCategories()
                .stream()
                .filter(c -> Boolean.TRUE.equals(c.getVisibleCatalogue()))
                .sorted(java.util.Comparator.comparing(
                        c -> c.getOrdreAffichage() != null ? c.getOrdreAffichage() : 0
                ))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(visibles);
    }
}
