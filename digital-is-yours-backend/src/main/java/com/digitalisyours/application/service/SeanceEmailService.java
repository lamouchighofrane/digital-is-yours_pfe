package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.SeanceEnLigne;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeanceEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void envoyerInvitation(String emailApprenant, String prenomApprenant,
                                  SeanceEnLigne seance) {
        try {
            String dateFormatee = seance.getDateSeance()
                    .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH));
            String duree = formatDuree(seance.getDureeMinutes());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(emailApprenant);
            helper.setSubject("📹 Séance en ligne — " + seance.getFormationTitre()
                    + " — " + seance.getDateSeance()
                    .format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)));
            helper.setText(buildHtml(prenomApprenant, seance, dateFormatee, duree), true);
            mailSender.send(message);
            log.info("Email séance envoyé à {}", emailApprenant);
        } catch (Exception e) {
            log.error("Erreur envoi email séance : {}", e.getMessage());
        }
    }

    private String formatDuree(int min) {
        int h = min / 60, m = min % 60;
        if (h > 0 && m > 0) return h + "h" + m + "min";
        if (h > 0) return h + "h";
        return m + "min";
    }

    private String buildHtml(String prenom, SeanceEnLigne seance,
                             String dateFormatee, String duree) {
        return """
        <!DOCTYPE html>
        <html><head><meta charset="UTF-8">
        <style>
          body{font-family:Arial,sans-serif;background:#F5F1EB;margin:0;padding:0}
          .wrap{max-width:600px;margin:32px auto;padding:0 16px}
          .card{background:#FFF;border-radius:20px;overflow:hidden;border:1px solid #E8E0D5;box-shadow:0 8px 32px rgba(26,22,18,.1)}
          .header{background:linear-gradient(135deg,#0F1E50,#2C5F61);padding:48px 40px 40px;text-align:center}
          .icon-circle{width:80px;height:80px;border-radius:50%%;background:rgba(255,255,255,.15);margin:0 auto 20px;font-size:38px;line-height:80px;text-align:center}
          .header-title{font-size:26px;font-weight:700;color:#FFF;margin:0 0 8px}
          .header-sub{font-size:13px;color:rgba(255,255,255,.6);margin:0}
          .gold-bar{height:3px;background:linear-gradient(90deg,transparent,#B48C32,#f0c84a,#B48C32,transparent)}
          .body{padding:40px}
          .greeting{font-size:22px;font-weight:700;color:#1A1612;margin:0 0 10px}
          .intro{font-size:14px;color:#6B5F52;line-height:1.75;margin:0 0 28px}
          .info-card{background:#F5F1EB;border-radius:14px;padding:0;margin-bottom:24px;overflow:hidden;border:1px solid #E8E0D5}
          .ic-top{height:4px;background:linear-gradient(90deg,#4A7C7E,#2C5F61)}
          .ic-body{padding:22px 24px}
          .ic-titre{font-size:18px;font-weight:700;color:#1A1612;margin:0 0 16px}
          .row{display:table;width:100%%;margin-bottom:10px}
          .row-icon{display:table-cell;width:28px;vertical-align:middle;font-size:16px}
          .row-text{display:table-cell;vertical-align:middle;font-size:13px;color:#6B5F52}
          .row-text b{color:#1A1612}
          .cta-wrap{text-align:center;margin-bottom:28px}
          .cta-btn{display:inline-block;background:linear-gradient(135deg,#4A7C7E,#2C5F61);color:#FFF;font-size:15px;font-weight:700;padding:16px 40px;border-radius:12px;text-decoration:none;box-shadow:0 6px 20px rgba(74,124,126,.35)}
          .link-zone{background:rgba(74,124,126,.07);border:1px solid rgba(74,124,126,.2);border-radius:10px;padding:12px 16px;margin-bottom:28px;word-break:break-all}
          .link-label{font-size:11px;font-weight:700;color:#4A7C7E;text-transform:uppercase;letter-spacing:.06em;margin:0 0 4px}
          .link-url{font-size:12px;color:#1A1612;font-family:monospace}
          .features{display:table;width:100%%;border-collapse:separate;border-spacing:8px}
          .feat{display:table-cell;background:#FAFAF7;border:1px solid #E8E0D5;border-radius:10px;padding:12px;text-align:center;width:25%%}
          .feat-icon{font-size:22px;display:block;margin-bottom:5px}
          .feat-label{font-size:11px;color:#6B5F52;font-weight:600}
          .footer{background:#F5F3EF;padding:20px 40px;text-align:center;border-top:1px solid #EDE8DF}
          .footer-logo{font-size:14px;font-weight:700;color:#1A1612;margin:0 0 4px}
          .footer-text{font-size:11px;color:#9B8B6E;margin:0}
        </style>
        </head><body>
        <div class="wrap"><div class="card">
          <div class="header">
            <div class="icon-circle">📹</div>
            <h1 class="header-title">Séance en ligne planifiée</h1>
            <p class="header-sub">Digital Is Yours — Académie en ligne</p>
          </div>
          <div class="gold-bar"></div>
          <div class="body">
            <h2 class="greeting">Bonjour %s !</h2>
            <p class="intro">
              Votre formateur <strong>%s %s</strong> a planifié une nouvelle séance en ligne
              pour la formation <strong>%s</strong>.<br>
              Rejoignez la session en cliquant sur le bouton ci-dessous.
            </p>
            <div class="info-card">
              <div class="ic-top"></div>
              <div class="ic-body">
                <p class="ic-titre">%s</p>
                <div class="row"><div class="row-icon">📅</div><div class="row-text"><b>Date :</b> %s</div></div>
                <div class="row"><div class="row-icon">⏱</div><div class="row-text"><b>Durée :</b> %s</div></div>
                <div class="row"><div class="row-icon">📚</div><div class="row-text"><b>Formation :</b> %s</div></div>
                %s
              </div>
            </div>
            <div class="cta-wrap">
              <a href="%s" class="cta-btn">🎥 Rejoindre la séance</a>
            </div>
            <div class="link-zone">
              <p class="link-label">Lien de la séance</p>
              <p class="link-url">%s</p>
            </div>
            <table class="features">
              <tr>
                <td class="feat"><span class="feat-icon">🎥</span><span class="feat-label">Vidéo HD</span></td>
                <td class="feat"><span class="feat-icon">💬</span><span class="feat-label">Chat</span></td>
                <td class="feat"><span class="feat-icon">🖥</span><span class="feat-label">Partage écran</span></td>
                <td class="feat"><span class="feat-icon">⏺</span><span class="feat-label">Enregistrement</span></td>
              </tr>
            </table>
          </div>
          <div class="footer">
            <p class="footer-logo">Digital Is Yours</p>
            <p class="footer-text">© 2026 Digital Is Yours · Académie en ligne</p>
          </div>
        </div></div>
        </body></html>
        """.formatted(
                prenom,
                seance.getFormateurPrenom(), seance.getFormateurNom(),
                seance.getFormationTitre(),
                seance.getTitre(),
                dateFormatee,
                duree,
                seance.getFormationTitre(),
                seance.getDescription() != null && !seance.getDescription().isBlank()
                        ? "<div class=\"row\"><div class=\"row-icon\">📝</div><div class=\"row-text\"><b>Note :</b> " + seance.getDescription() + "</div></div>"
                        : "",
                seance.getLienJitsi(),
                seance.getLienJitsi()
        );
    }
}