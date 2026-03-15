package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Notification;
import com.digitalisyours.domain.port.in.NotificationUseCase;

import com.digitalisyours.infrastructure.web.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping({"/api/formateur/notifications", "/api/apprenant/notifications"})
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class NotificationController {
    private final NotificationUseCase notificationUseCase;
    private final JwtUtil jwtUtil;

    // Types de notifications réservés aux formateurs
    private static final List<String> TYPES_FORMATEUR = List.of(
            "FORMATION_AFFECTEE", "FORMATION_RETIREE", "FORMATEUR"
    );

    @GetMapping
    public ResponseEntity<?> getMesNotifications(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Request-URI", required = false) String requestUri) {
        String email = extractEmail(authHeader);
        if (email == null) return unauthorized();

        List<Notification> notifs = notificationUseCase.getMesNotifications(email);

        // ✅ Si appel depuis /api/apprenant → filtrer les notifs de type formateur
        String role = extractRole(authHeader);
        if ("APPRENANT".equals(role)) {
            notifs = notifs.stream()
                    .filter(n -> !TYPES_FORMATEUR.contains(n.getType()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(notifs);
    }

    @GetMapping("/count")
    public ResponseEntity<?> getNonLuesCount(
            @RequestHeader("Authorization") String authHeader) {
        String email = extractEmail(authHeader);
        if (email == null) return unauthorized();

        // ✅ Pour un apprenant, compter uniquement ses notifications apprenant
        String role = extractRole(authHeader);
        if ("APPRENANT".equals(role)) {
            List<Notification> notifs = notificationUseCase.getMesNotifications(email);
            long count = notifs.stream()
                    .filter(n -> !TYPES_FORMATEUR.contains(n.getType()))
                    .filter(n -> !n.isLu())
                    .count();
            return ResponseEntity.ok(Map.of("count", count));
        }

        return ResponseEntity.ok(Map.of("count", notificationUseCase.getNonLuesCount(email)));
    }

    @PatchMapping("/{id}/lire")
    public ResponseEntity<?> marquerCommentLue(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        String email = extractEmail(authHeader);
        if (email == null) return unauthorized();
        try {
            List<Notification> notifs = notificationUseCase.getMesNotifications(email);
            boolean appartient = notifs.stream().anyMatch(n -> n.getId().equals(id));
            if (!appartient) return ResponseEntity.notFound().build();
            notificationUseCase.marquerCommentLue(id, email);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/tout-lire")
    public ResponseEntity<?> marquerToutesLues(
            @RequestHeader("Authorization") String authHeader) {
        String email = extractEmail(authHeader);
        if (email == null) return unauthorized();
        notificationUseCase.marquerToutesLues(email);
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Toutes les notifications marquées comme lues"));
    }

    private String extractEmail(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private String extractRole(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            if (!jwtUtil.isValid(token)) return null;
            // Extraire le rôle depuis le token JWT
            return jwtUtil.extractRole(token);
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
    }
}
