package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.dto.request.CreateUserRequest;
import com.digitalisyours.infrastructure.web.dto.request.UpdateUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    // ══ STATS ════════════════════════════════════════════════════
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long apprenants  = userRepository.countByRole(Role.APPRENANT);
        long formateurs  = userRepository.countByRole(Role.FORMATEUR);
        long totalUsers  = apprenants + formateurs;
        long nonVerifies = userRepository.countByEmailVerifieFalse();
        long desactives  = userRepository.countByActiveFalse();

        return ResponseEntity.ok(Map.of(
                "totalUsers",  totalUsers,
                "apprenants",  apprenants,
                "formateurs",  formateurs,
                "nonVerifies", nonVerifies,
                "desactives",  desactives
        ));
    }

    // ══ LISTE UTILISATEURS ═══════════════════════════════════════
    @GetMapping("/users")
    public ResponseEntity<List<UserEntity>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAllByRoleNot(Role.ADMIN));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé")));
    }

    // ══ CRÉER ════════════════════════════════════════════════════
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Cet email est déjà utilisé", "success", false));
        }

        UserEntity user = UserEntity.builder()
                .prenom(request.getPrenom())
                .nom(request.getNom())
                .email(request.getEmail())
                .telephone(request.getTelephone())
                .motDePasse(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .emailVerifie(true)
                .active(true)
                .build();

        userRepository.save(user);
        log.info("Admin a créé l'utilisateur: {}", request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "Utilisateur créé avec succès", "success", true));
    }

    // ══ MODIFIER ═════════════════════════════════════════════════
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Cet email est déjà utilisé", "success", false));
        }

        user.setPrenom(request.getPrenom());
        user.setNom(request.getNom());
        user.setEmail(request.getEmail());
        user.setTelephone(request.getTelephone());
        user.setRole(request.getRole());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setMotDePasse(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);
        log.info("Admin a modifié l'utilisateur: {}", user.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "Utilisateur modifié avec succès", "success", true));
    }

    // ══ TOGGLE ACTIVER / DÉSACTIVER ══════════════════════════════
    // ⚠️ Ce endpoint change user.active — Spring Security vérifie
    //    ce champ via UserDetails.isEnabled() pour bloquer la connexion
    @PatchMapping("/users/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setActive(!user.isActive());
        userRepository.save(user);
        log.info("Admin a {} le compte: {}", user.isActive() ? "activé" : "désactivé", user.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", user.isActive() ? "Compte activé avec succès" : "Compte désactivé — connexion bloquée",
                "active",  user.isActive(),
                "success", true
        ));
    }

    // ══ SUPPRIMER ════════════════════════════════════════════════
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        log.info("Admin a supprimé l'utilisateur ID: {}", id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé", "success", true));
    }

    // ══ APPROUVER / REFUSER FORMATEUR ════════════════════════════
    @PatchMapping("/users/{id}/approve-formateur")
    public ResponseEntity<?> approveFormateur(@PathVariable Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setEmailVerifie(true);
        user.setActive(true);
        userRepository.save(user);
        log.info("Formateur approuvé: {}", user.getEmail());
        return ResponseEntity.ok(Map.of("message", "Formateur approuvé", "success", true));
    }

    @DeleteMapping("/users/{id}/reject-formateur")
    public ResponseEntity<?> rejectFormateur(@PathVariable Long id) {
        userRepository.deleteById(id);
        log.info("Formateur refusé ID: {}", id);
        return ResponseEntity.ok(Map.of("message", "Formateur refusé et supprimé", "success", true));
    }
}
