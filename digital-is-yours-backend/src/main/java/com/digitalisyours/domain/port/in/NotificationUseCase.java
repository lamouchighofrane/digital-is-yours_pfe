package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Notification;

import java.util.List;

public interface NotificationUseCase {
    List<Notification> getMesNotifications(String email);
    long getNonLuesCount(String email);
    void marquerCommentLue(Long notifId, String email);
    void marquerToutesLues(String email);
}
