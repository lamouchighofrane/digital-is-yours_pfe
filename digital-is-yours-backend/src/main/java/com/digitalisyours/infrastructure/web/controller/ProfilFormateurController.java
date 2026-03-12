package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Formateur;

import com.digitalisyours.domain.port.in.ProfilFormateurUseCase;

import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@RequestMapping("/api/formateur/profil")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class ProfilFormateurController {
    private final ProfilFormateurUseCase profilUseCase;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getProfil(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(profilUseCase.getProfil(email));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> updateProfil(
            HttpServletRequest request,
            @RequestBody Map<String, Object> payload) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Formateur updated = profilUseCase.updateProfil(email, payload);
            log.info("Profil formateur mis à jour : {}", email);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil mis à jour avec succès",
                    "profil",  updated
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/mot-de-passe")
    public ResponseEntity<?> changerMotDePasse(
            HttpServletRequest request,
            @RequestBody Map<String, Object> payload) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            profilUseCase.changerMotDePasse(
                    email,
                    (String) payload.get("ancienMotDePasse"),
                    (String) payload.get("nouveauMotDePasse"),
                    (String) payload.get("confirmMotDePasse")
            );
            return ResponseEntity.ok(Map.of("success", true, "message", "Mot de passe modifié avec succès"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

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
}
