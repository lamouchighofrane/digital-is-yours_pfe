package com.digitalisyours.infrastructure.persistence.adapter;


import com.digitalisyours.domain.model.Notification;
import com.digitalisyours.domain.port.out.NotificationRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.NotificationEntity;
import com.digitalisyours.infrastructure.persistence.repository.NotificationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepositoryPort {
    private final NotificationJpaRepository notificationJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Override
    public List<Notification> findByEmailOrderByDateDesc(String email) {
        return userJpaRepository.findByEmail(email)
                .map(user -> notificationJpaRepository
                        .findByUserOrderByDateCreationDesc(user)
                        .stream().map(this::toDomain).collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Override
    public long countNonLuesByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .map(notificationJpaRepository::countByUserAndLuFalse)
                .orElse(0L);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return notificationJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = notificationJpaRepository.findById(notification.getId())
                .orElseThrow(() -> new RuntimeException("Notification non trouvée"));
        entity.setLu(notification.isLu());
        return toDomain(notificationJpaRepository.save(entity));
    }

    @Override
    public void marquerToutesLues(String email) {
        userJpaRepository.findByEmail(email)
                .ifPresent(notificationJpaRepository::marquerToutesLues);
    }

    private Notification toDomain(NotificationEntity e) {
        return Notification.builder()
                .id(e.getId())
                .userId(e.getUser().getId())
                .type(e.getType())
                .titre(e.getTitre())
                .message(e.getMessage())
                .formationId(e.getFormationId())
                .formationTitre(e.getFormationTitre())
                .lu(e.isLu())
                .dateCreation(e.getDateCreation())
                .build();
    }
}
