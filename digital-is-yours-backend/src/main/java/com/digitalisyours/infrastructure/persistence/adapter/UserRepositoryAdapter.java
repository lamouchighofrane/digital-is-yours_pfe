package com.digitalisyours.infrastructure.persistence.adapter;
import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.out.UserRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {
    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public void updatePassword(String email, String newPassword) {
        jpaRepository.updatePassword(email, newPassword);
    }

    @Override
    public void markEmailVerified(String email) {
        jpaRepository.markEmailVerified(email);
    }

    @Override
    public void updateLastLogin(String email) {
        jpaRepository.updateLastLogin(email, LocalDateTime.now());
    }

    private UserEntity toEntity(User user) {
        return UserEntity.builder()
                .id(user.getId())
                .prenom(user.getPrenom())
                .nom(user.getNom())
                .email(user.getEmail())
                .telephone(user.getTelephone())
                .motDePasse(user.getMotDePasse())
                .role(user.getRole())
                .emailVerifie(user.isEmailVerifie())
                .dateInscription(user.getDateInscription())
                .derniereConnexion(user.getDerniereConnexion())
                .build();
    }

    private User toDomain(UserEntity entity) {
        return User.builder()
                .id(entity.getId())
                .prenom(entity.getPrenom())
                .nom(entity.getNom())
                .email(entity.getEmail())
                .telephone(entity.getTelephone())
                .motDePasse(entity.getMotDePasse())
                .role(entity.getRole())
                .emailVerifie(entity.isEmailVerifie())
                .dateInscription(entity.getDateInscription())
                .derniereConnexion(entity.getDerniereConnexion())
                .build();
    }
}
