package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Competence;
import com.digitalisyours.domain.port.in.CompetenceUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
@RequestMapping("/api/admin/competences")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CompetenceController {
    private final CompetenceUseCase competenceUseCase;

    @GetMapping
    public ResponseEntity<List<Competence>> getAll() {
        return ResponseEntity.ok(competenceUseCase.getAllCompetences());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(competenceUseCase.getAllCategories());
    }

    @GetMapping("/formation/{formationId}")
    public ResponseEntity<List<Competence>> getByFormation(@PathVariable Long formationId) {
        return ResponseEntity.ok(competenceUseCase.getCompetencesByFormation(formationId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> payload) {
        try {
            Competence competence = Competence.builder()
                    .nom((String) payload.get("nom"))
                    .categorie((String) payload.get("categorie"))
                    .build();
            Competence created = competenceUseCase.createCompetence(competence);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Compétence créée",
                    "data", Map.of("id", created.getId(), "nom", created.getNom(),
                            "categorie", created.getCategorie() != null ? created.getCategorie() : "")
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        try {
            Competence competence = Competence.builder()
                    .nom((String) payload.get("nom"))
                    .categorie((String) payload.get("categorie"))
                    .build();
            competenceUseCase.updateCompetence(id, competence);
            return ResponseEntity.ok(Map.of("success", true, "message", "Compétence mise à jour"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            competenceUseCase.deleteCompetence(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Compétence supprimée"));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/formation/{formationId}")
    public ResponseEntity<?> associer(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> ids = (List<Integer>) payload.get("competenceIds");
            List<Long> competenceIds = ids != null
                    ? ids.stream().map(Long::valueOf).toList()
                    : List.of();
            competenceUseCase.associerCompetences(formationId, competenceIds);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", competenceIds.size() + " compétence(s) associée(s) avec succès"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }
}
