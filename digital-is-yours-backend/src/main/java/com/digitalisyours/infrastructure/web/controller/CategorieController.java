package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.infrastructure.persistence.entity.CategorieEntity;
import com.digitalisyours.infrastructure.persistence.repository.CategorieJpaRepository;
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
    private final CategorieJpaRepository categorieRepository;

    // ══════════════════════════════════════════════════════════
    // LISTER TOUTES LES CATÉGORIES
    // ══════════════════════════════════════════════════════════
    @GetMapping
    public ResponseEntity<List<CategorieEntity>> getAllCategories() {
        log.info("Récupération de toutes les catégories");
        List<CategorieEntity> categories = categorieRepository.findAllByOrderByOrdreAffichageAsc();
        return ResponseEntity.ok(categories);
    }

    // ══════════════════════════════════════════════════════════
    // RÉCUPÉRER UNE CATÉGORIE PAR ID
    // ══════════════════════════════════════════════════════════
    @GetMapping("/{id}")
    public ResponseEntity<?> getCategorieById(@PathVariable Long id) {
        log.info("Récupération de la catégorie ID: {}", id);

        return categorieRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════════
    // CRÉER UNE CATÉGORIE
    // ══════════════════════════════════════════════════════════
    @PostMapping
    public ResponseEntity<?> createCategorie(@RequestBody Map<String, Object> payload) {
        log.info("Création d'une nouvelle catégorie");

        String nom = (String) payload.get("nom");

        // Validation : nom unique
        if (categorieRepository.existsByNom(nom)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Une catégorie avec ce nom existe déjà"
            ));
        }

        CategorieEntity categorie = CategorieEntity.builder()
                .nom(nom)
                .description((String) payload.get("description"))
                .couleur((String) payload.get("couleur"))
                .imageCouverture((String) payload.get("imageCouverture"))
                .metaDescription((String) payload.get("metaDescription"))
                .ordreAffichage(payload.get("ordreAffichage") != null ?
                        Integer.valueOf(payload.get("ordreAffichage").toString()) : 0)
                .visibleCatalogue(payload.get("visibleCatalogue") != null ?
                        Boolean.valueOf(payload.get("visibleCatalogue").toString()) : true)
                .dateCreation(LocalDateTime.now())
                .build();

        categorieRepository.save(categorie);

        log.info("Catégorie créée avec succès: {}", nom);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Catégorie créée avec succès",
                "id", categorie.getId()
        ));
    }

    // ══════════════════════════════════════════════════════════
    // MODIFIER UNE CATÉGORIE
    // ══════════════════════════════════════════════════════════
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategorie(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        log.info("Modification de la catégorie ID: {}", id);

        CategorieEntity categorie = categorieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée"));

        String nouveauNom = (String) payload.get("nom");

        // Validation : nom unique (sauf si c'est le même)
        if (!categorie.getNom().equals(nouveauNom) && categorieRepository.existsByNom(nouveauNom)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Une catégorie avec ce nom existe déjà"
            ));
        }

        categorie.setNom(nouveauNom);
        categorie.setDescription((String) payload.get("description"));
        categorie.setCouleur((String) payload.get("couleur"));
        categorie.setImageCouverture((String) payload.get("imageCouverture"));
        categorie.setMetaDescription((String) payload.get("metaDescription"));
        categorie.setOrdreAffichage(payload.get("ordreAffichage") != null ?
                Integer.valueOf(payload.get("ordreAffichage").toString()) : 0);
        categorie.setVisibleCatalogue(payload.get("visibleCatalogue") != null ?
                Boolean.valueOf(payload.get("visibleCatalogue").toString()) : true);

        categorieRepository.save(categorie);

        log.info("Catégorie mise à jour avec succès: {}", nouveauNom);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Catégorie mise à jour avec succès"
        ));
    }

    // ══════════════════════════════════════════════════════════
    // SUPPRIMER UNE CATÉGORIE
    // ══════════════════════════════════════════════════════════
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategorie(@PathVariable Long id) {
        log.info("Suppression de la catégorie ID: {}", id);

        if (!categorieRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // TODO: Vérifier si des formations utilisent cette catégorie
        // Si oui, empêcher la suppression ou proposer une réaffectation

        categorieRepository.deleteById(id);

        log.info("Catégorie supprimée avec succès ID: {}", id);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Catégorie supprimée avec succès"
        ));
    }
}
