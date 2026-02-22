package com.digitalisyours.infrastructure.web.service;


import com.digitalisyours.domain.model.EmailVerificationToken;
import com.digitalisyours.infrastructure.persistence.EmailVerificationTokenRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final EmailVerificationTokenRepository tokenRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    // Lien de verification email
    public void sendVerificationLink(String toEmail) {
        try {
            tokenRepository.deleteByEmail(toEmail);

            EmailVerificationToken token = new EmailVerificationToken(toEmail);
            tokenRepository.save(token);

            String verificationUrl = "http://localhost:8080/api/auth/verify-email?token=" + token.getToken();
            String htmlContent = buildVerificationEmailHtml(verificationUrl);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Digital Is Yours");
            helper.setTo(toEmail);
            helper.setSubject("Confirmez votre compte Digital Is Yours");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Lien de verification envoye a: {}", toEmail);

        } catch (Exception e) {
            log.error("Erreur envoi lien verification: {}", e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de verification");
        }
    }

    // Code reset mot de passe
    public void sendPasswordResetCode(String toEmail, String code) {
        try {
            String htmlContent = buildPasswordResetEmailHtml(code);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Digital Is Yours");
            helper.setTo(toEmail);
            helper.setSubject("Code de reinitialisation - Digital Is Yours");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Code reset envoye a: {}", toEmail);

        } catch (Exception e) {
            log.error("Erreur envoi code reset: {}", e.getMessage());
            throw new RuntimeException("Impossible d'envoyer le code");
        }
    }

    private String buildVerificationEmailHtml(String verificationUrl) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;padding:0;background:#F5F1EB;font-family:Arial,sans-serif\">"
                + "<div style=\"max-width:560px;margin:40px auto;background:#fff;border-radius:16px;"
                + "overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)\">"
                + "<div style=\"background:#1E1A16;padding:40px 32px;text-align:center\">"
                + "<p style=\"font-size:40px;margin:0 0 12px\">&#9993;</p>"
                + "<h1 style=\"color:#F5F1EB;font-size:22px;margin:0;font-weight:600\">Confirmez votre email</h1>"
                + "<p style=\"color:rgba(245,241,235,0.5);font-size:13px;margin:8px 0 0\">Digital Is Yours</p>"
                + "</div>"
                + "<div style=\"padding:40px 32px\">"
                + "<p style=\"color:#3D362E;font-size:15px;line-height:1.6;margin:0 0 24px\">"
                + "Merci de vous etre inscrit sur <strong>Digital Is Yours</strong> !<br>"
                + "Cliquez sur le bouton ci-dessous pour activer votre compte."
                + "</p>"
                + "<div style=\"text-align:center;margin:32px 0\">"
                + "<a href=\"" + verificationUrl + "\" "
                + "style=\"display:inline-block;padding:16px 40px;background:#8B3A3A;"
                + "color:#fff;text-decoration:none;border-radius:12px;font-weight:600;font-size:15px\">"
                + "Activer mon compte"
                + "</a>"
                + "</div>"
                + "<div style=\"background:#F5F1EB;border-radius:8px;padding:16px;margin:24px 0\">"
                + "<p style=\"color:#9E9082;font-size:11px;margin:0 0 6px\">Ou copiez ce lien :</p>"
                + "<p style=\"color:#8B3A3A;font-size:11px;margin:0;word-break:break-all\">" + verificationUrl + "</p>"
                + "</div>"
                + "<p style=\"color:#C8BEB2;font-size:12px;margin:24px 0 0\">"
                + "Ce lien expire dans <strong>24 heures</strong>.<br>"
                + "Si vous n'avez pas cree de compte, ignorez cet email."
                + "</p>"
                + "</div>"
                + "<div style=\"padding:20px 32px;border-top:1px solid #EDE8DF;text-align:center\">"
                + "<p style=\"color:#C8BEB2;font-size:11px;margin:0\">2026 Digital Is Yours</p>"
                + "</div>"
                + "</div></body></html>";
    }

    private String buildPasswordResetEmailHtml(String code) {
        StringBuilder digitBoxes = new StringBuilder();
        for (char c : code.toCharArray()) {
            digitBoxes.append("<div style=\"width:48px;height:56px;background:#F5F1EB;border:2px solid #EDE8DF;"
                    + "border-radius:10px;display:inline-block;text-align:center;line-height:56px;"
                    + "font-size:24px;font-weight:700;color:#1E1A16;margin:0 4px\">"
                    + c
                    + "</div>");
        }

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;padding:0;background:#F5F1EB;font-family:Arial,sans-serif\">"
                + "<div style=\"max-width:560px;margin:40px auto;background:#fff;border-radius:16px;"
                + "overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)\">"
                + "<div style=\"background:#1E1A16;padding:40px 32px;text-align:center\">"
                + "<p style=\"font-size:40px;margin:0 0 12px\">&#128272;</p>"
                + "<h1 style=\"color:#F5F1EB;font-size:22px;margin:0;font-weight:600\">Reinitialisation du mot de passe</h1>"
                + "<p style=\"color:rgba(245,241,235,0.5);font-size:13px;margin:8px 0 0\">Digital Is Yours</p>"
                + "</div>"
                + "<div style=\"padding:40px 32px;text-align:center\">"
                + "<p style=\"color:#3D362E;font-size:15px;margin:0 0 8px;text-align:left\">Voici votre code :</p>"
                + "<p style=\"color:#9E9082;font-size:13px;margin:0 0 28px;text-align:left\">Ce code expire dans 5 minutes.</p>"
                + "<div style=\"background:#F5F1EB;border-radius:12px;padding:28px 20px;margin:0 0 24px\">"
                + digitBoxes.toString()
                + "</div>"
                + "<p style=\"color:#C8BEB2;font-size:12px;margin:0;text-align:left\">"
                + "Si vous n'avez pas demande de reinitialisation, ignorez cet email."
                + "</p>"
                + "</div>"
                + "<div style=\"padding:20px 32px;border-top:1px solid #EDE8DF;text-align:center\">"
                + "<p style=\"color:#C8BEB2;font-size:11px;margin:0\">2026 Digital Is Yours</p>"
                + "</div>"
                + "</div></body></html>";
    }
}
