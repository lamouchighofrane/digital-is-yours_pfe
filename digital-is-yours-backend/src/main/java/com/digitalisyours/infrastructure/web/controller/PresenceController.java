package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.PresenceSeance;
import com.digitalisyours.domain.port.in.PresenceUseCase;

import com.digitalisyours.infrastructure.web.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceUseCase presenceUseCase;
    private final JwtUtil jwtUtil;

    // ── Apprenant rejoint ──────────────────────────────
    @PostMapping("/apprenant/seances/{seanceId}/rejoindre")
    public ResponseEntity<?> rejoindre(
            @PathVariable Long seanceId,
            @RequestHeader("Authorization") String authHeader) {

        String email = extraireEmail(authHeader);
        if (email == null) return ResponseEntity.status(401).build();

        presenceUseCase.apprenantRejoindre(seanceId, email);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Apprenant quitte ───────────────────────────────
    @PostMapping("/apprenant/seances/{seanceId}/quitter")
    public ResponseEntity<?> quitter(
            @PathVariable Long seanceId,
            @RequestHeader("Authorization") String authHeader) {

        String email = extraireEmail(authHeader);
        if (email == null) return ResponseEntity.status(401).build();

        presenceUseCase.apprenantQuitter(seanceId, email);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Formateur consulte les présences ──────────────
    @GetMapping("/formateur/seances/{seanceId}/presences")
    public ResponseEntity<List<PresenceSeance>> getPresences(
            @PathVariable Long seanceId) {

        List<PresenceSeance> presences =
                presenceUseCase.getPresences(seanceId);
        return ResponseEntity.ok(presences);
    }

    // ── Utilitaire JWT ────────────────────────────────
    private String extraireEmail(String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            return jwtUtil.extractEmail(token);
        } catch (Exception e) {
            return null;
        }
    }
}