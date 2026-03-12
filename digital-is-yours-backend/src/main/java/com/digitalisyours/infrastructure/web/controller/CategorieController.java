package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Categorie;
import com.digitalisyours.domain.port.in.CategorieUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CategorieController {
    private final CategorieUseCase categorieUseCase;

    @GetMapping
    public ResponseEntity<List<Categorie>> getAllCategories() {
        return ResponseEntity.ok(categorieUseCase.getAllCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCategorieById(@PathVariable Long id) {
        return categorieUseCase.getCategorieById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createCategorie(@RequestBody Map<String, Object> payload) {
        try {
            Categorie categorie = fromPayload(null, payload);
            Categorie created = categorieUseCase.createCategorie(categorie);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Catégorie créée avec succès",
                    "id", created.getId()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategorie(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        try {
            Categorie categorie = fromPayload(id, payload);
            categorieUseCase.updateCategorie(id, categorie);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Catégorie mise à jour avec succès"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategorie(@PathVariable Long id) {
        try {
            categorieUseCase.deleteCategorie(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Catégorie supprimée avec succès"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helper ───────────────────────────────────────────────

    private Categorie fromPayload(Long id, Map<String, Object> p) {
        return Categorie.builder()
                .id(id)
                .nom((String) p.get("nom"))
                .description((String) p.get("description"))
                .couleur((String) p.get("couleur"))
                .imageCouverture((String) p.get("imageCouverture"))
                .metaDescription((String) p.get("metaDescription"))
                .ordreAffichage(p.get("ordreAffichage") != null
                        ? Integer.valueOf(p.get("ordreAffichage").toString()) : 0)
                .visibleCatalogue(p.get("visibleCatalogue") != null
                        ? Boolean.valueOf(p.get("visibleCatalogue").toString()) : true)
                .build();
    }
}
