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


@RestController
@RequestMapping("/api/formateur/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class NotificationController {
    private final NotificationUseCase notificationUseCase;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getMesNotifications(
            @RequestHeader("Authorization") String authHeader) {
        String email = extractEmail(authHeader);
        if (email == null) return unauthorized();
        List<Notification> notifs = notificationUseCase.getMesNotifications(email);
        return ResponseEntity.ok(notifs);
    }

    @GetMapping("/count")
    public ResponseEntity<?> getNonLuesCount(
            @RequestHeader("Authorization") String authHeader) {
        String email = extractEmail(authHeader);
        if (email == null) return unauthorized();
        return ResponseEntity.ok(Map.of("count", notificationUseCase.getNonLuesCount(email)));
    }

    @PatchMapping("/{id}/lire")
    public ResponseEntity<?> marquerCommentLue(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        String email = extractEmail(authHeader);
        if (email == null) return unauthorized();
        try {
            // Vérifier que la notif appartient à cet utilisateur
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
        return ResponseEntity.ok(Map.of("success", true, "message", "Toutes les notifications marquées comme lues"));
    }

    private String extractEmail(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
    }
}
