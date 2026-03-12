package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepositoryPort {
    List<Notification> findByEmailOrderByDateDesc(String email);
    long countNonLuesByEmail(String email);
    Optional<Notification> findById(Long id);
    Notification save(Notification notification);
    void marquerToutesLues(String email);
}
