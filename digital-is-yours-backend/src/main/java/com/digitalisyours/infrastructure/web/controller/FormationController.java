package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.persistence.entity.CategorieEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.CategorieJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/formations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class FormationController {
    private final FormationJpaRepository formationRepository;
    private final CategorieJpaRepository categorieRepository;
    private final UserJpaRepository userRepository;

    // ══ STATS ════════════════════════════════════════════════════
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(Map.of(
                "total",      formationRepository.countAll(),
                "publiees",   formationRepository.countPubliees(),
                "brouillons", formationRepository.countBrouillons()
        ));
    }

    // ══ LISTER TOUS LES FORMATEURS ACTIFS ════════════════════════
    @GetMapping("/formateurs")
    public ResponseEntity<List<Map<String, Object>>> getAllFormateurs() {
        List<UserEntity> formateurs = userRepository.findByRoleAndActiveTrue(Role.FORMATEUR);
        return ResponseEntity.ok(
                formateurs.stream().map(this::formateurToMap).collect(Collectors.toList())
        );
    }

    // ══ LISTER TOUTES LES FORMATIONS ══════════════════════════════
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllFormations() {
        List<FormationEntity> formations = formationRepository.findAllWithCategorie();
        return ResponseEntity.ok(
                formations.stream().map(this::toResponse).collect(Collectors.toList())
        );
    }

    // ══ RÉCUPÉRER UNE FORMATION ═══════════════════════════════════
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return formationRepository.findById(id)
                .map(f -> ResponseEntity.ok(toResponse(f)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ══ CRÉER ════════════════════════════════════════════════════
    @PostMapping
    public ResponseEntity<?> createFormation(@RequestBody Map<String, Object> payload) {
        log.info("Création d'une nouvelle formation");

        String titre = (String) payload.get("titre");
        if (titre == null || titre.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Le titre est obligatoire"));
        }

        CategorieEntity categorie = resoudreCategorie(payload);
        UserEntity formateur = resoudreFormateur(payload);

        FormationEntity formation = FormationEntity.builder()
                .titre(titre)
                .description((String) payload.get("description"))
                .objectifsApprentissage((String) payload.get("objectifsApprentissage"))
                .prerequis((String) payload.get("prerequis"))
                .pourQui((String) payload.get("pourQui"))
                .imageCouverture((String) payload.get("imageCouverture"))
                .dureeEstimee(payload.get("dureeEstimee") != null
                        ? Integer.valueOf(payload.get("dureeEstimee").toString()) : 1)
                .niveau((String) payload.get("niveau"))
                .statut(payload.get("statut") != null ? (String) payload.get("statut") : "BROUILLON")
                .categorie(categorie)
                .formateur(formateur)
                .dateCreation(LocalDateTime.now())
                .nombreInscrits(0)
                .nombreCertifies(0)
                .build();

        if ("PUBLIE".equals(formation.getStatut())) {
            formation.setDatePublication(LocalDateTime.now());
        }

        formationRepository.save(formation);
        log.info("Formation créée : {}", titre);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Formation créée avec succès",
                "id", formation.getId()
        ));
    }

    // ══ MODIFIER ═════════════════════════════════════════════════
    @PutMapping("/{id}")
    public ResponseEntity<?> updateFormation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        FormationEntity formation = formationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        String titre = (String) payload.get("titre");
        if (titre == null || titre.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Le titre est obligatoire"));
        }

        boolean wasNotPublie = !"PUBLIE".equals(formation.getStatut());
        String newStatut = payload.get("statut") != null
                ? (String) payload.get("statut") : formation.getStatut();

        formation.setTitre(titre);
        formation.setDescription((String) payload.get("description"));
        formation.setObjectifsApprentissage((String) payload.get("objectifsApprentissage"));
        formation.setPrerequis((String) payload.get("prerequis"));
        formation.setPourQui((String) payload.get("pourQui"));
        formation.setImageCouverture((String) payload.get("imageCouverture"));
        formation.setDureeEstimee(payload.get("dureeEstimee") != null
                ? Integer.valueOf(payload.get("dureeEstimee").toString()) : formation.getDureeEstimee());
        formation.setNiveau((String) payload.get("niveau"));
        formation.setStatut(newStatut);
        formation.setCategorie(resoudreCategorie(payload));
        formation.setFormateur(resoudreFormateur(payload));

        if ("PUBLIE".equals(newStatut) && wasNotPublie) {
            formation.setDatePublication(LocalDateTime.now());
        }

        formationRepository.save(formation);
        log.info("Formation mise à jour : {}", titre);

        return ResponseEntity.ok(Map.of("success", true, "message", "Formation mise à jour avec succès"));
    }

    // ══ AFFECTER FORMATEUR (endpoint dédié) ══════════════════════
    @PatchMapping("/{id}/affecter-formateur")
    public ResponseEntity<?> affecterFormateur(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        FormationEntity formation = formationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        Object formateurIdObj = payload.get("formateurId");

        if (formateurIdObj == null) {
            // Retirer le formateur
            formation.setFormateur(null);
            formationRepository.save(formation);
            log.info("Formateur retiré de la formation: {}", formation.getTitre());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Formateur retiré avec succès"
            ));
        }

        Long formateurId = Long.valueOf(formateurIdObj.toString());
        UserEntity formateur = userRepository.findById(formateurId)
                .orElseThrow(() -> new RuntimeException("Formateur non trouvé"));

        if (formateur.getRole() != Role.FORMATEUR) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "L'utilisateur sélectionné n'est pas un formateur"
            ));
        }

        formation.setFormateur(formateur);
        formationRepository.save(formation);

        log.info("Formateur {} {} affecté à la formation: {}",
                formateur.getPrenom(), formateur.getNom(), formation.getTitre());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Formateur affecté avec succès",
                "formateur", formateurToMap(formateur)
        ));
    }

    // ══ TOGGLE STATUT ════════════════════════════════════════════
    @PatchMapping("/{id}/toggle-statut")
    public ResponseEntity<?> toggleStatut(@PathVariable Long id) {
        FormationEntity formation = formationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        String newStatut = "PUBLIE".equals(formation.getStatut()) ? "BROUILLON" : "PUBLIE";
        formation.setStatut(newStatut);

        if ("PUBLIE".equals(newStatut) && formation.getDatePublication() == null) {
            formation.setDatePublication(LocalDateTime.now());
        }

        formationRepository.save(formation);
        log.info("Formation {} → {}", formation.getTitre(), newStatut);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "statut",  newStatut,
                "message", "PUBLIE".equals(newStatut) ? "Formation publiée" : "Formation mise en brouillon"
        ));
    }

    // ══ SUPPRIMER ════════════════════════════════════════════════
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFormation(@PathVariable Long id) {
        if (!formationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        formationRepository.deleteById(id);
        log.info("Formation supprimée ID: {}", id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Formation supprimée avec succès"));
    }

    // ══ HELPERS ══════════════════════════════════════════════════
    private CategorieEntity resoudreCategorie(Map<String, Object> payload) {
        Object catIdObj = payload.get("categorieId");
        if (catIdObj == null) return null;
        try {
            Long catId = Long.valueOf(catIdObj.toString());
            return categorieRepository.findById(catId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UserEntity resoudreFormateur(Map<String, Object> payload) {
        Object formateurIdObj = payload.get("formateurId");
        if (formateurIdObj == null) return null;
        try {
            Long formateurId = Long.valueOf(formateurIdObj.toString());
            return userRepository.findById(formateurId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> toResponse(FormationEntity f) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",                    f.getId());
        map.put("titre",                 f.getTitre());
        map.put("description",           f.getDescription());
        map.put("objectifsApprentissage",f.getObjectifsApprentissage());
        map.put("prerequis",             f.getPrerequis());
        map.put("pourQui",               f.getPourQui());
        map.put("imageCouverture",       f.getImageCouverture());
        map.put("dureeEstimee",          f.getDureeEstimee());
        map.put("niveau",                f.getNiveau());
        map.put("statut",                f.getStatut());
        map.put("dateCreation",          f.getDateCreation());
        map.put("datePublication",       f.getDatePublication());
        map.put("nombreInscrits",        f.getNombreInscrits());
        map.put("nombreCertifies",       f.getNombreCertifies());
        map.put("noteMoyenne",           f.getNoteMoyenne());
        map.put("tauxReussite",          f.getTauxReussite());

        if (f.getCategorie() != null) {
            map.put("categorieId",       f.getCategorie().getId());
            map.put("categorieNom",      f.getCategorie().getNom());
            map.put("categorieCouleur",  f.getCategorie().getCouleur());
        }

        // ★★★ AJOUT : Infos formateur ★★★
        if (f.getFormateur() != null) {
            map.put("formateurId",      f.getFormateur().getId());
            map.put("formateurNom",     f.getFormateur().getNom());
            map.put("formateurPrenom",  f.getFormateur().getPrenom());
            map.put("formateurEmail",   f.getFormateur().getEmail());
        }

        return map;
    }

    private Map<String, Object> formateurToMap(UserEntity u) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",      u.getId());
        map.put("prenom",  u.getPrenom());
        map.put("nom",     u.getNom());
        map.put("email",   u.getEmail());
        map.put("active",  u.isActive());
        return map;
    }
}
