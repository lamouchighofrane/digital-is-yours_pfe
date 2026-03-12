package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Cours;

import com.digitalisyours.domain.port.in.CoursFormateurUseCase;

import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/formateur/formations/{formationId}/cours")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CoursFormateurController {
    private final CoursFormateurUseCase coursUseCase;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getCours(
            @PathVariable Long formationId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(coursUseCase.getCours(formationId, email));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createCours(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Cours cours = coursUseCase.createCours(formationId, email, payload);
            log.info("Formateur {} a créé le cours '{}'", email, cours.getTitre());
            return ResponseEntity.ok(cours);
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{coursId}")
    public ResponseEntity<?> updateCours(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(coursUseCase.updateCours(formationId, coursId, email, payload));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{coursId}/toggle-statut")
    public ResponseEntity<?> toggleStatut(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Cours cours = coursUseCase.toggleStatut(formationId, coursId, email);
            return ResponseEntity.ok(Map.of("success", true, "statut", cours.getStatut()));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
    }

    @DeleteMapping("/{coursId}")
    public ResponseEntity<?> deleteCours(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            coursUseCase.deleteCours(formationId, coursId, email);
            log.info("Formateur {} a supprimé le cours id={}", email, coursId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Cours supprimé"));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
    }

    @PatchMapping("/reordonner")
    public ResponseEntity<?> reordonner(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ordres = (List<Map<String, Object>>) payload.get("ordres");
            if (ordres == null)
                return ResponseEntity.badRequest().body(Map.of("message", "Données manquantes"));
            coursUseCase.reordonner(formationId, email, ordres);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
    }

    @PostMapping("/{coursId}/video/youtube")
    public ResponseEntity<?> ajouterVideoYoutube(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Cours cours = coursUseCase.ajouterVideoYoutube(formationId, coursId, email, body.get("url"));
            log.info("Formateur {} a ajouté une vidéo YouTube au cours {}", email, coursId);
            return ResponseEntity.ok(cours);
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{coursId}/video/upload")
    public ResponseEntity<?> uploadVideoLocale(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestParam("fichier") MultipartFile fichier,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Cours cours = coursUseCase.uploadVideoLocale(
                    formationId, coursId, email,
                    fichier.getOriginalFilename(),
                    fichier.getContentType(),
                    fichier.getSize(),
                    fichier.getBytes()
            );
            log.info("Formateur {} a uploadé une vidéo pour le cours {}", email, coursId);
            return ResponseEntity.ok(cours);
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lecture fichier"));
        }
    }

    @DeleteMapping("/{coursId}/video")
    public ResponseEntity<?> supprimerVideo(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            coursUseCase.supprimerVideo(formationId, coursId, email);
            log.info("Formateur {} a supprimé la vidéo du cours {}", email, coursId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Vidéo supprimée"));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
    }

    private ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(403).body(Map.of("message", message));
    }
}
