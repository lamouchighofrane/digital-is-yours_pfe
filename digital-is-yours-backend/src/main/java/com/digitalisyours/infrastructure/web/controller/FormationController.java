package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.in.FormationUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/admin/formations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class FormationController {
    private final FormationUseCase formationUseCase;

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(formationUseCase.getStats());
    }

    @GetMapping("/formateurs")
    public ResponseEntity<List<User>> getAllFormateurs() {
        return ResponseEntity.ok(formationUseCase.getAllFormateurs());
    }

    @GetMapping
    public ResponseEntity<List<Formation>> getAllFormations() {
        return ResponseEntity.ok(formationUseCase.getAllFormations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(formationUseCase.getFormationById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createFormation(@RequestBody Map<String, Object> payload) {
        try {
            Formation formation = fromPayload(payload);
            Formation created = formationUseCase.createFormation(formation);
            log.info("Formation créée : {}", created.getTitre());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Formation créée avec succès",
                    "id", created.getId()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFormation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        try {
            Formation formation = fromPayload(payload);
            formationUseCase.updateFormation(id, formation);
            log.info("Formation mise à jour ID: {}", id);
            return ResponseEntity.ok(Map.of(
                    "success", true, "message", "Formation mise à jour avec succès"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/affecter-formateur")
    public ResponseEntity<?> affecterFormateur(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        try {
            Object formateurIdObj = payload.get("formateurId");
            if (formateurIdObj == null) {
                formationUseCase.retirerFormateur(id);
                log.info("Formateur retiré de la formation ID: {}", id);
                return ResponseEntity.ok(Map.of(
                        "success", true, "message", "Formateur retiré avec succès"));
            }
            Long formateurId = Long.valueOf(formateurIdObj.toString());
            Formation updated = formationUseCase.affecterFormateur(id, formateurId);
            log.info("Formateur {} affecté à la formation ID: {}", formateurId, id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Formateur affecté avec succès",
                    "formateurId",     updated.getFormateurId(),
                    "formateurNom",    updated.getFormateurNom(),
                    "formateurPrenom", updated.getFormateurPrenom(),
                    "formateurEmail",  updated.getFormateurEmail()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/toggle-statut")
    public ResponseEntity<?> toggleStatut(@PathVariable Long id) {
        try {
            Formation updated = formationUseCase.toggleStatut(id);
            log.info("Formation {} → {}", updated.getTitre(), updated.getStatut());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "statut",  updated.getStatut(),
                    "message", "PUBLIE".equals(updated.getStatut())
                            ? "Formation publiée" : "Formation mise en brouillon"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFormation(@PathVariable Long id) {
        try {
            formationUseCase.deleteFormation(id);
            log.info("Formation supprimée ID: {}", id);
            return ResponseEntity.ok(Map.of(
                    "success", true, "message", "Formation supprimée avec succès"));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helper ────────────────────────────────────────────────────
    private Formation fromPayload(Map<String, Object> payload) {
        return Formation.builder()
                .titre((String) payload.get("titre"))
                .description((String) payload.get("description"))
                .objectifsApprentissage((String) payload.get("objectifsApprentissage"))
                .prerequis((String) payload.get("prerequis"))
                .pourQui((String) payload.get("pourQui"))
                .imageCouverture((String) payload.get("imageCouverture"))
                .dureeEstimee(payload.get("dureeEstimee") != null
                        ? Integer.valueOf(payload.get("dureeEstimee").toString()) : null)
                .niveau((String) payload.get("niveau"))
                .statut((String) payload.get("statut"))
                .categorieId(payload.get("categorieId") != null
                        ? Long.valueOf(payload.get("categorieId").toString()) : null)
                .formateurId(payload.get("formateurId") != null
                        ? Long.valueOf(payload.get("formateurId").toString()) : null)
                .build();
    }
}
