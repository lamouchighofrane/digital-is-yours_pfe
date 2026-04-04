package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.port.in.SessionCalendrierUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/apprenant/calendrier")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class SessionCalendrierController {
    private final SessionCalendrierUseCase sessionUseCase;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getMesSessions(HttpServletRequest req) {
        String email = extractEmail(req);
        if (email == null) return unauthorized();
        return ResponseEntity.ok(sessionUseCase.getMesSessions(email));
    }

    @PostMapping
    public ResponseEntity<?> ajouterSession(
            HttpServletRequest req,
            @RequestBody Map<String, Object> payload) {
        String email = extractEmail(req);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(
                    sessionUseCase.ajouterSession(email, payload));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> modifierSession(
            HttpServletRequest req,
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        String email = extractEmail(req);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(
                    sessionUseCase.modifierSession(email, id, payload));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimerSession(
            HttpServletRequest req,
            @PathVariable Long id) {
        String email = extractEmail(req);
        if (email == null) return unauthorized();
        try {
            sessionUseCase.supprimerSession(email, id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    private String extractEmail(HttpServletRequest req) {
        try {
            String auth = req.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token)
                    ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401)
                .body(Map.of("message", "Non autorisé"));
    }
}