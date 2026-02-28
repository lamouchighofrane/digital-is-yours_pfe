package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.infrastructure.persistence.entity.CompetenceEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.repository.CompetenceJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/competences")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CompetenceController {
    private final CompetenceJpaRepository competenceRepository;
    private final FormationJpaRepository formationRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(
                competenceRepository.findAllByOrderByNomAsc()
                        .stream().map(this::toMap).collect(Collectors.toList())
        );
    }

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(competenceRepository.findAllCategories());
    }

    @GetMapping("/formation/{formationId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getByFormation(@PathVariable Long formationId) {
        return ResponseEntity.ok(
                competenceRepository.findByFormationId(formationId)
                        .stream().map(this::toMap).collect(Collectors.toList())
        );
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, Object> payload) {
        String nom = (String) payload.get("nom");
        if (nom == null || nom.isBlank())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Le nom est obligatoire"));
        if (competenceRepository.existsByNom(nom.trim()))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cette compétence existe déjà"));

        CompetenceEntity c = CompetenceEntity.builder()
                .nom(nom.trim())
                .categorie((String) payload.get("categorie"))
                .build();
        competenceRepository.save(c);
        return ResponseEntity.ok(Map.of("success", true, "message", "Compétence créée", "data", toMap(c)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        CompetenceEntity c = competenceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compétence non trouvée"));
        String nom = (String) payload.get("nom");
        if (nom == null || nom.isBlank())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Le nom est obligatoire"));
        if (!c.getNom().equals(nom.trim()) && competenceRepository.existsByNom(nom.trim()))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cette compétence existe déjà"));

        c.setNom(nom.trim());
        c.setCategorie((String) payload.get("categorie"));
        competenceRepository.save(c);
        return ResponseEntity.ok(Map.of("success", true, "message", "Compétence mise à jour"));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!competenceRepository.existsById(id)) return ResponseEntity.notFound().build();
        competenceRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Compétence supprimée"));
    }

    @PutMapping("/formation/{formationId}")
    @Transactional
    public ResponseEntity<?> associer(@PathVariable Long formationId,
                                      @RequestBody Map<String, Object> payload) {
        FormationEntity formation = formationRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) payload.get("competenceIds");

        Set<CompetenceEntity> competences = new HashSet<>();
        if (ids != null) {
            competences = ids.stream()
                    .map(i -> competenceRepository.findById(Long.valueOf(i))
                            .orElseThrow(() -> new RuntimeException("Compétence introuvable : " + i)))
                    .collect(Collectors.toSet());
        }
        formation.setCompetences(competences);
        formationRepository.save(formation);
        log.info("Formation {} : {} compétences associées", formation.getTitre(), competences.size());
        return ResponseEntity.ok(Map.of("success", true,
                "message", competences.size() + " compétence(s) associée(s) avec succès"));
    }

    private Map<String, Object> toMap(CompetenceEntity c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        c.getId());
        m.put("nom",       c.getNom());
        m.put("categorie", c.getCategorie() != null ? c.getCategorie() : "");
        return m;
    }
}
