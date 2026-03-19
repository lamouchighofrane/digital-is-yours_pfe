package com.digitalisyours.infrastructure.scheduler;

import com.digitalisyours.domain.model.SessionCalendrier;
import com.digitalisyours.domain.port.out.SessionCalendrierRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.NotificationEntity;
import com.digitalisyours.infrastructure.persistence.repository.ApprenantJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.NotificationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RappelSessionScheduler {
    private final SessionCalendrierRepositoryPort sessionRepo;
    private final ApprenantJpaRepository apprenantRepo;
    private final JavaMailSender mailSender;
    private final NotificationJpaRepository notificationRepo;
    private final UserJpaRepository userRepo;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Scheduled(fixedRate = 3600000)
    public void envoyerRappels() {
        LocalDateTime from = LocalDateTime.now().plusHours(23);
        LocalDateTime to   = LocalDateTime.now().plusHours(25);

        List<SessionCalendrier> sessions =
                sessionRepo.findRappelsAEnvoyer(from, to);

        for (SessionCalendrier s : sessions) {
            try {
                apprenantRepo.findById(s.getApprenantId())
                        .ifPresent(apprenant -> {
                            try {
                                String html = buildRappelEmail(
                                        apprenant.getPrenom(),
                                        s.getTitrePersonnalise(),
                                        s.getDateSession(),
                                        s.getDureeMinutes(),
                                        s.getTypeSession()
                                );

                                MimeMessage message = mailSender.createMimeMessage();
                                MimeMessageHelper helper = new MimeMessageHelper(
                                        message, true, "UTF-8");
                                helper.setFrom(fromEmail);
                                helper.setTo(apprenant.getEmail());
                                helper.setSubject("Rappel — Session demain : "
                                        + s.getTitrePersonnalise());
                                helper.setText(html, true);
                                mailSender.send(message);
                                log.info("Rappel HTML envoyé à {} pour session {}",
                                        apprenant.getEmail(), s.getId());

                                userRepo.findByEmail(apprenant.getEmail())
                                        .ifPresent(user -> {
                                            NotificationEntity notif =
                                                    NotificationEntity.builder()
                                                            .user(user)
                                                            .type("RAPPEL_SESSION")
                                                            .titre("Rappel session demain")
                                                            .message("Votre session \""
                                                                    + s.getTitrePersonnalise()
                                                                    + "\" est prévue demain à "
                                                                    + s.getDateSession().format(
                                                                    DateTimeFormatter.ofPattern("HH:mm")))
                                                            .lu(false)
                                                            .build();
                                            notificationRepo.save(notif);
                                            log.info("Notification cloche créée pour {}",
                                                    apprenant.getEmail());
                                        });

                            } catch (Exception e) {
                                log.error("Erreur envoi rappel: {}", e.getMessage());
                            }
                        });

                s.setRappelEnvoye(true);
                sessionRepo.save(s);

            } catch (Exception e) {
                log.error("Erreur rappel session {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    private String buildRappelEmail(String prenom, String titre,
                                    LocalDateTime dateSession,
                                    Integer dureeMinutes, String typeSession) {
        String icone = "QUIZ".equals(typeSession) ? "✏️"
                : "EVENEMENT".equals(typeSession) ? "📅" : "📚";

        String couleur = "QUIZ".equals(typeSession) ? "#27ae60"
                : "EVENEMENT".equals(typeSession) ? "#f39c12" : "#4A7C7E";

        String dateFormatee = dateSession.format(
                DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm",
                        java.util.Locale.FRENCH));

        int h = dureeMinutes != null ? dureeMinutes / 60 : 1;
        int m = dureeMinutes != null ? dureeMinutes % 60 : 0;
        String duree = h > 0
                ? (h + "h" + (m > 0 ? m + "min" : ""))
                : (m + "min");

        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#F5F1EB;font-family:Arial,sans-serif">
            <div style="max-width:560px;margin:40px auto;background:#fff;border-radius:16px;
              overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)">
              <div style="background:#1E1A16;padding:40px 32px;text-align:center">
                <p style="font-size:40px;margin:0 0 12px">📅</p>
                <h1 style="color:#F5F1EB;font-size:22px;margin:0;font-weight:600">
                  Rappel de session
                </h1>
                <p style="color:rgba(245,241,235,0.5);font-size:13px;margin:8px 0 0">
                  Digital Is Yours
                </p>
              </div>
              <div style="padding:40px 32px">
                <h2 style="color:#1E1A16;margin:0 0 16px;font-size:20px">
                  Bonjour %s,
                </h2>
                <p style="color:#6B5F52;font-size:15px;line-height:1.6;margin:0 0 24px">
                  Vous avez une session d'apprentissage planifiée <strong>demain</strong>.
                  Voici les détails :
                </p>
                <div style="background:#F5F1EB;border-radius:14px;padding:24px;
                  border-left:4px solid %s;margin:0 0 24px">
                  <div style="font-size:28px;margin-bottom:12px">%s</div>
                  <h3 style="color:#1E1A16;font-size:18px;margin:0 0 16px;
                    font-family:'Cormorant Garamond',Georgia,serif">
                    %s
                  </h3>
                  <table style="width:100%%;border-collapse:collapse">
                    <tr>
                      <td style="padding:6px 0;color:#9E9082;font-size:13px;width:40%%">
                        📆 Date
                      </td>
                      <td style="padding:6px 0;color:#1E1A16;font-size:13px;
                        font-weight:600;text-transform:capitalize">
                        %s
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:6px 0;color:#9E9082;font-size:13px">
                        ⏱ Durée
                      </td>
                      <td style="padding:6px 0;color:#1E1A16;font-size:13px;font-weight:600">
                        %s
                      </td>
                    </tr>
                  </table>
                </div>
                <div style="text-align:center;margin:32px 0">
                  <a href="http://localhost:4200/apprenant/dashboard?section=calendrier"
                     style="display:inline-block;padding:16px 40px;background:#4A7C7E;
                     color:#fff;text-decoration:none;border-radius:12px;
                     font-weight:600;font-size:15px">
                    Voir mon calendrier →
                  </a>
                </div>
                <p style="color:#C8BEB2;font-size:12px;margin:0;text-align:center">
                  Bonne session ! — Digital Is Yours
                </p>
              </div>
              <div style="padding:20px 32px;border-top:1px solid #EDE8DF;text-align:center">
                <p style="color:#C8BEB2;font-size:11px;margin:0">
                  © 2026 Digital Is Yours · Tous droits réservés
                </p>
              </div>
            </div>
            </body></html>
            """.formatted(prenom, couleur, icone, titre, dateFormatee, duree);
    }
}
