package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Notification;
import com.digitalisyours.domain.port.in.NotificationUseCase;
import com.digitalisyours.domain.port.out.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationUseCase {
    private final NotificationRepositoryPort notificationRepository;

    @Override
    public List<Notification> getMesNotifications(String email) {
        return notificationRepository.findByEmailOrderByDateDesc(email);
    }

    @Override
    public long getNonLuesCount(String email) {
        return notificationRepository.countNonLuesByEmail(email);
    }

    @Override
    public void marquerCommentLue(Long notifId, String email) {
        Notification notif = notificationRepository.findById(notifId)
                .orElseThrow(() -> new RuntimeException("Notification non trouvée"));
        // Vérifier que la notif appartient bien à cet utilisateur
        // (géré dans le controller via le check du userId)
        notif.setLu(true);
        notificationRepository.save(notif);
    }

    @Override
    public void marquerToutesLues(String email) {
        notificationRepository.marquerToutesLues(email);
    }
}
