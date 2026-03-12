package com.digitalisyours.infrastructure.web.controller;



import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.in.AdminUseCase;

import com.digitalisyours.infrastructure.web.dto.request.CreateUserRequest;
import com.digitalisyours.infrastructure.web.dto.request.UpdateUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class AdminController {
    private final AdminUseCase adminUseCase;

    // ─────────────────────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(adminUseCase.getStats());
    }

    // ─────────────────────────────────────────────────────────────
    // USERS — CRUD
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminUseCase.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminUseCase.getUserById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Crée un utilisateur et retourne l'objet complet (avec id)
     * pour que le frontend puisse l'ajouter immédiatement à la liste
     * sans attendre un rechargement — fix Bug 4 race condition.
     */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            User user = User.builder()
                    .prenom(request.getPrenom())
                    .nom(request.getNom())
                    .email(request.getEmail())
                    .telephone(request.getTelephone())
                    .role(request.getRole())
                    .build();

            User created = adminUseCase.createUser(user, request.getPassword());
            log.info("Admin a créé l'utilisateur: {}", request.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Utilisateur créé avec succès");
            response.put("success", true);
            response.put("id", created.getId());
            response.put("prenom", created.getPrenom());
            response.put("nom", created.getNom());
            response.put("email", created.getEmail());
            response.put("telephone", created.getTelephone());
            response.put("role", created.getRole());
            response.put("active", created.isActive());
            response.put("emailVerifie", created.isEmailVerifie());
            response.put("dateInscription", created.getDateInscription());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(), "success", false));
        }
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        try {
            User user = User.builder()
                    .prenom(request.getPrenom())
                    .nom(request.getNom())
                    .email(request.getEmail())
                    .telephone(request.getTelephone())
                    .role(request.getRole())
                    .build();
            adminUseCase.updateUser(id, user, request.getPassword());
            log.info("Admin a modifié l'utilisateur id: {}", id);
            return ResponseEntity.ok(Map.of(
                    "message", "Utilisateur modifié avec succès", "success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(), "success", false));
        }
    }

    @PatchMapping("/users/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        try {
            adminUseCase.toggleActive(id);
            User user = adminUseCase.getUserById(id);
            log.info("Admin a {} le compte id: {}",
                    user.isActive() ? "activé" : "désactivé", id);
            return ResponseEntity.ok(Map.of(
                    "message", user.isActive()
                            ? "Compte activé avec succès"
                            : "Compte désactivé — connexion bloquée",
                    "active", user.isActive(),
                    "success", true
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(), "success", false));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        adminUseCase.deleteUser(id);
        log.info("Admin a supprimé l'utilisateur ID: {}", id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé", "success", true));
    }

    // ─────────────────────────────────────────────────────────────
    // FORMATEURS — APPROBATION
    // ─────────────────────────────────────────────────────────────

    @PatchMapping("/users/{id}/approve-formateur")
    public ResponseEntity<?> approveFormateur(@PathVariable Long id) {
        try {
            adminUseCase.approveFormateur(id);
            log.info("Formateur approuvé id: {}", id);
            return ResponseEntity.ok(Map.of("message", "Formateur approuvé", "success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(), "success", false));
        }
    }

    @DeleteMapping("/users/{id}/reject-formateur")
    public ResponseEntity<?> rejectFormateur(@PathVariable Long id) {
        adminUseCase.rejectFormateur(id);
        log.info("Formateur refusé ID: {}", id);
        return ResponseEntity.ok(Map.of(
                "message", "Formateur refusé et supprimé", "success", true));
    }
}
