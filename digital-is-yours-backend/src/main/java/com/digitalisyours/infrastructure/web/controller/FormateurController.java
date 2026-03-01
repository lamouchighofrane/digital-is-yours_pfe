package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/formateur")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class FormateurController {
    private final FormationJpaRepository formationRepository;
    private final UserJpaRepository userRepository;
    private final JwtUtil jwtUtil;

    // ══ MES FORMATIONS (uniquement PUBLIÉES) ══════════════════
    @GetMapping("/mes-formations")
    public ResponseEntity<?> getMesFormations(HttpServletRequest request) {
        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        }

        List<FormationEntity> formations = formationRepository.findAllWithCategorie()
                .stream()
                .filter(f -> f.getFormateur() != null
                        && f.getFormateur().getId().equals(formateur.getId())
                        && "PUBLIE".equals(f.getStatut()))   // ← uniquement les publiées
                .collect(Collectors.toList());

        List<Map<String, Object>> result = formations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        log.info("Formateur {} {} a {} formations publiées affectées",
                formateur.getPrenom(), formateur.getNom(), result.size());

        return ResponseEntity.ok(result);
    }

    // ══ STATS (basées sur formations publiées uniquement) ═════
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        }

        List<FormationEntity> formations = formationRepository.findAllWithCategorie()
                .stream()
                .filter(f -> f.getFormateur() != null
                        && f.getFormateur().getId().equals(formateur.getId())
                        && "PUBLIE".equals(f.getStatut()))   // ← uniquement les publiées
                .collect(Collectors.toList());

        int totalApprenants = formations.stream()
                .mapToInt(f -> f.getNombreInscrits() != null ? f.getNombreInscrits() : 0)
                .sum();

        int totalCertifies = formations.stream()
                .mapToInt(f -> f.getNombreCertifies() != null ? f.getNombreCertifies() : 0)
                .sum();

        int tauxReussite = totalApprenants > 0
                ? (int) Math.round((double) totalCertifies / totalApprenants * 100)
                : 0;

        double noteMoyenne = formations.stream()
                .filter(f -> f.getNoteMoyenne() != null && f.getNoteMoyenne() > 0)
                .mapToDouble(FormationEntity::getNoteMoyenne)
                .average()
                .orElse(0.0);

        return ResponseEntity.ok(Map.of(
                "totalApprenants",  totalApprenants,
                "tauxReussite",     tauxReussite,
                "nouveauxInscrits", 0,
                "noteMoyenne",      Math.round(noteMoyenne * 10.0) / 10.0
        ));
    }

    // ══ ACTIVITÉS RÉCENTES (placeholder) ═════════════════════
    @GetMapping("/activites-recentes")
    public ResponseEntity<?> getActivitesRecentes(HttpServletRequest request) {
        return ResponseEntity.ok(Collections.emptyList());
    }

    // ══ ALERTES (placeholder) ════════════════════════════════
    @GetMapping("/alertes")
    public ResponseEntity<?> getAlertes(HttpServletRequest request) {
        return ResponseEntity.ok(Collections.emptyList());
    }

    // ══ HELPER : extraire le formateur depuis le JWT ══════════
    private UserEntity getFormateurFromRequest(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            if (!jwtUtil.isValid(token)) return null;
            String email = jwtUtil.extractEmail(token);
            return userRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            log.warn("Erreur extraction formateur depuis JWT: {}", e.getMessage());
            return null;
        }
    }

    // ══ HELPER : sérialiser FormationEntity ══════════════════
    private Map<String, Object> toResponse(FormationEntity f) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",              f.getId());
        map.put("titre",           f.getTitre());
        map.put("description",     f.getDescription());
        map.put("imageCouverture", f.getImageCouverture());
        map.put("dureeEstimee",    f.getDureeEstimee());
        map.put("niveau",          f.getNiveau());
        map.put("statut",          f.getStatut());
        map.put("dateCreation",    f.getDateCreation());
        map.put("nombreInscrits",  f.getNombreInscrits() != null ? f.getNombreInscrits() : 0);
        map.put("nombreCertifies", f.getNombreCertifies() != null ? f.getNombreCertifies() : 0);
        map.put("noteMoyenne",     f.getNoteMoyenne());
        map.put("tauxReussite",    f.getTauxReussite() != null ? (int) Math.round(f.getTauxReussite()) : 0);

        if (f.getCategorie() != null) {
            map.put("categorieId",      f.getCategorie().getId());
            map.put("categorieNom",     f.getCategorie().getNom());
            map.put("categorieCouleur", f.getCategorie().getCouleur());
        }

        return map;
    }
}
