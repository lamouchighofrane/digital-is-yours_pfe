package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.infrastructure.persistence.entity.NotificationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.NotificationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/formateur/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class NotificationController {
    private final NotificationJpaRepository notifRepository;
    private final UserJpaRepository userRepository;
    private final JwtUtil jwtUtil;

    // ══ GET toutes les notifications du formateur ══════════════
    @GetMapping
    public ResponseEntity<?> getMesNotifications(
            @RequestHeader("Authorization") String authHeader) {

        UserEntity formateur = getFormateurFromRequest(authHeader);
        if (formateur == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        }

        List<NotificationEntity> notifs =
                notifRepository.findByUserOrderByDateCreationDesc(formateur);

        return ResponseEntity.ok(
                notifs.stream().map(this::toResponse).collect(Collectors.toList())
        );
    }

    // ══ GET nombre de notifications non lues ══════════════════
    @GetMapping("/count")
    public ResponseEntity<?> getNonLuesCount(
            @RequestHeader("Authorization") String authHeader) {

        UserEntity formateur = getFormateurFromRequest(authHeader);
        if (formateur == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        }

        long count = notifRepository.countByUserAndLuFalse(formateur);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ══ PATCH marquer une notification comme lue ══════════════
    @PatchMapping("/{id}/lire")
    public ResponseEntity<?> marquerCommentLue(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        UserEntity formateur = getFormateurFromRequest(authHeader);
        if (formateur == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        }

        NotificationEntity notif = notifRepository.findById(id).orElse(null);
        if (notif == null || !notif.getUser().getId().equals(formateur.getId())) {
            return ResponseEntity.notFound().build();
        }

        notif.setLu(true);
        notifRepository.save(notif);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ══ PATCH marquer TOUTES comme lues ═══════════════════════
    @PatchMapping("/tout-lire")
    public ResponseEntity<?> marquerToutesLues(
            @RequestHeader("Authorization") String authHeader) {

        UserEntity formateur = getFormateurFromRequest(authHeader);
        if (formateur == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        }

        notifRepository.marquerToutesLues(formateur);
        return ResponseEntity.ok(Map.of("success", true, "message", "Toutes les notifications marquées comme lues"));
    }

    // ══ HELPER : extraire le formateur depuis le JWT ═══════════
    private UserEntity getFormateurFromRequest(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            if (!jwtUtil.isValid(token)) return null;
            String email = jwtUtil.extractEmail(token);
            return userRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            log.warn("Erreur extraction formateur: {}", e.getMessage());
            return null;
        }
    }

    // ══ HELPER : sérialiser une notification ══════════════════
    private Map<String, Object> toResponse(NotificationEntity n) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",             n.getId());
        map.put("type",           n.getType());
        map.put("titre",          n.getTitre());
        map.put("message",        n.getMessage());
        map.put("formationId",    n.getFormationId());
        map.put("formationTitre", n.getFormationTitre());
        map.put("lu",             n.isLu());
        map.put("dateCreation",   n.getDateCreation());
        return map;
    }
}
