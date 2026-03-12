package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.out.AdminRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.FormateurEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.NotificationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminRepositoryAdapter implements AdminRepositoryPort {
    private final UserJpaRepository userJpaRepository;
    private final NotificationJpaRepository notificationJpaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<User> findAllNonAdmin() {
        return userJpaRepository.findAllByRoleNot(Role.ADMIN)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity;

        if (user.getId() != null) {
            // ── UPDATE : charger l'entité existante (obligatoire avec InheritanceType.JOINED)
            entity = userJpaRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + user.getId()));

            entity.setPrenom(user.getPrenom());
            entity.setNom(user.getNom());
            entity.setEmail(user.getEmail());
            entity.setTelephone(user.getTelephone());
            entity.setRole(user.getRole());
            entity.setEmailVerifie(user.isEmailVerifie());
            entity.setActive(user.isActive());
            entity.setDateInscription(user.getDateInscription());
            entity.setDerniereConnexion(user.getDerniereConnexion());

            // Ne mettre à jour le mot de passe que s'il est fourni (non encodé != null)
            if (user.getMotDePasse() != null && !user.getMotDePasse().isBlank()) {
                entity.setMotDePasse(user.getMotDePasse()); // déjà encodé par AdminService
            }

        } else {
            // ── CREATE : construire la bonne sous-entité selon le rôle
            if (user.getRole() == Role.FORMATEUR) {
                entity = FormateurEntity.builder()
                        .prenom(user.getPrenom())
                        .nom(user.getNom())
                        .email(user.getEmail())
                        .telephone(user.getTelephone())
                        .motDePasse(user.getMotDePasse()) // déjà encodé par AdminService
                        .role(Role.FORMATEUR)
                        .emailVerifie(true)
                        .active(true)
                        .dateInscription(LocalDateTime.now())
                        .build();
            } else {
                // APPRENANT ou autre rôle non-admin
                entity = UserEntity.builder()
                        .prenom(user.getPrenom())
                        .nom(user.getNom())
                        .email(user.getEmail())
                        .telephone(user.getTelephone())
                        .motDePasse(user.getMotDePasse()) // déjà encodé par AdminService
                        .role(user.getRole() != null ? user.getRole() : Role.APPRENANT)
                        .emailVerifie(true)
                        .active(true)
                        .dateInscription(LocalDateTime.now())
                        .build();
            }
        }

        return toDomain(userJpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        // Supprimer d'abord les notifications liées à cet utilisateur (FK constraint)
        notificationJpaRepository.deleteByUserId(id);
        // Puis supprimer l'utilisateur
        userJpaRepository.deleteById(id);
    }

    @Override
    public long countByRole(String role) {
        return userJpaRepository.countByRole(Role.valueOf(role));
    }

    @Override
    public long countEmailNonVerifie() {
        return userJpaRepository.countByEmailVerifieFalse();
    }

    @Override
    public long countDesactives() {
        return userJpaRepository.countByActiveFalse();
    }

    // ── Mapping ──────────────────────────────────────────────

    private User toDomain(UserEntity e) {
        return User.builder()
                .id(e.getId())
                .prenom(e.getPrenom())
                .nom(e.getNom())
                .email(e.getEmail())
                .telephone(e.getTelephone())
                .motDePasse(e.getMotDePasse())
                .role(e.getRole())
                .emailVerifie(e.isEmailVerifie())
                .active(e.isActive())
                .dateInscription(e.getDateInscription())
                .derniereConnexion(e.getDerniereConnexion())
                .build();
    }
}
