package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.SeanceEnLigne;
import com.digitalisyours.domain.port.in.SeanceUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/formateur/seances")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class SeanceController {

    private final SeanceUseCase seanceUseCase;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> creerSeance(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            SeanceEnLigne seance = seanceUseCase.creerSeance(email, payload);
            log.info("Séance créée : {} pour formation {}", seance.getTitre(), seance.getFormationId());
            return ResponseEntity.ok(toMap(seance));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getMesSeances(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        List<Map<String, Object>> result = seanceUseCase.getMesSeances(email)
                .stream().map(this::toMap).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimerSeance(
            @PathVariable Long id,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            seanceUseCase.supprimerSeance(id, email);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/annuler")
    public ResponseEntity<?> annulerSeance(
            @PathVariable Long id,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            SeanceEnLigne s = seanceUseCase.annulerSeance(id, email);
            return ResponseEntity.ok(toMap(s));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(SeanceEnLigne s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("titre", s.getTitre());
        m.put("formationId", s.getFormationId());
        m.put("formationTitre", s.getFormationTitre() != null ? s.getFormationTitre() : "");
        m.put("formateurNom", s.getFormateurNom() != null ? s.getFormateurNom() : "");
        m.put("formateurPrenom", s.getFormateurPrenom() != null ? s.getFormateurPrenom() : "");
        m.put("dateSeance", s.getDateSeance() != null ? s.getDateSeance().toString() : "");
        m.put("dureeMinutes", s.getDureeMinutes());
        m.put("description", s.getDescription() != null ? s.getDescription() : "");
        m.put("lienJitsi", s.getLienJitsi());
        m.put("roomName", s.getRoomName());
        m.put("statut", s.getStatut());
        m.put("notifEnvoyee", s.getNotifEnvoyee());
        m.put("dateCreation", s.getDateCreation() != null ? s.getDateCreation().toString() : "");
        return m;
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