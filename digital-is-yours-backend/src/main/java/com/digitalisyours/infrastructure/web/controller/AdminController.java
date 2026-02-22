package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class AdminController {
    private final UserJpaRepository userRepository;

    // ── Statistiques générales ───────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long apprenants  = userRepository.countByRole(Role.APPRENANT);
        long formateurs  = userRepository.countByRole(Role.FORMATEUR);
        long totalUsers  = apprenants + formateurs;
        long nonVerifies = userRepository.countByEmailVerifieFalse();

        return ResponseEntity.ok(Map.of(
                "totalUsers",  totalUsers,
                "apprenants",  apprenants,
                "formateurs",  formateurs,
                "nonVerifies", nonVerifies
        ));
    }

    // ── Liste tous les utilisateurs ──────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<List<UserEntity>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAllByRoleNot(Role.ADMIN));
    }

    // ── Activer / Désactiver un utilisateur ──────────────────────
    @PatchMapping("/users/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setActive(!user.isActive());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message", user.isActive() ? "Compte activé" : "Compte désactivé",
                "active", user.isActive()
        ));
    }

    // ── Supprimer un utilisateur ─────────────────────────────────
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé"));
    }

    // ── Approuver un formateur ───────────────────────────────────
    @PatchMapping("/users/{id}/approve-formateur")
    public ResponseEntity<?> approveFormateur(@PathVariable Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setEmailVerifie(true);
        user.setActive(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Formateur approuvé"));
    }

    // ── Refuser / supprimer un formateur ─────────────────────────
    @DeleteMapping("/users/{id}/reject-formateur")
    public ResponseEntity<?> rejectFormateur(@PathVariable Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Formateur refusé et supprimé"));
    }
}
