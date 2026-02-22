package com.digitalisyours.infrastructure.email;

import com.digitalisyours.domain.port.out.EmailSenderPort;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSenderAdapter implements EmailSenderPort {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendOtpVerification(String to, String prenom, String otpCode) {
        String subject = "Digital Is Yours - Code de vérification";
        String html = buildOtpVerificationEmail(prenom, otpCode);
        sendHtml(to, subject, html);
    }

    @Override
    public void sendPasswordReset(String to, String prenom, String otpCode) {
        String subject = "Digital Is Yours - Réinitialisation de mot de passe";
        String html = buildPasswordResetEmail(prenom, otpCode);
        sendHtml(to, subject, html);
    }

    @Override
    public void sendWelcome(String to, String prenom, String role) {
        String subject = "Bienvenue sur Digital Is Yours !";
        String html = buildWelcomeEmail(prenom, role);
        sendHtml(to, subject, html);
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email envoyé à : {}", to);
        } catch (Exception e) {
            log.error("Erreur envoi email à {} : {}", to, e.getMessage());
        }
    }

    private String buildOtpVerificationEmail(String prenom, String code) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              body{font-family:'DM Sans',Arial,sans-serif;background:#FAFAF7;margin:0;padding:0}
              .container{max-width:520px;margin:40px auto;background:white;border-radius:20px;
                border:1px solid #EDE8DF;overflow:hidden}
              .header{background:#1E1A16;padding:32px;text-align:center}
              .body{padding:40px}
              .otp-box{background:#f9f0f0;border:2px solid #f0d9d9;border-radius:16px;
                padding:28px;text-align:center;margin:24px 0}
              .otp-code{font-size:42px;font-weight:900;color:#8B3A3A;letter-spacing:12px;
                font-family:monospace}
              .footer{background:#F5F3EF;padding:20px;text-align:center;
                font-size:12px;color:#9E9082}
            </style></head><body>
            <div class="container">
              <div class="header">
                <h1 style="color:white;margin:0;font-size:24px">Digital Is Yours</h1>
                <p style="color:rgba(255,255,255,0.5);margin:8px 0 0;font-size:13px">
                  Plateforme E-Learning
                </p>
              </div>
              <div class="body">
                <h2 style="color:#1E1A16;margin:0 0 16px">Bonjour %s,</h2>
                <p style="color:#6B5F52;line-height:1.6">
                  Voici votre code de vérification pour activer votre compte
                  <strong>Digital Is Yours</strong> :
                </p>
                <div class="otp-box">
                  <div class="otp-code">%s</div>
                  <p style="color:#9E9082;font-size:13px;margin:12px 0 0">
                    ⏱ Ce code expire dans <strong>5 minutes</strong>
                  </p>
                </div>
                <p style="color:#9E9082;font-size:13px">
                  Si vous n'avez pas demandé ce code, ignorez cet email.
                </p>
              </div>
              <div class="footer">
                © 2026 Digital Is Yours · Tous droits réservés
              </div>
            </div>
            </body></html>
            """.formatted(prenom, code);
    }

    private String buildPasswordResetEmail(String prenom, String code) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              body{font-family:'DM Sans',Arial,sans-serif;background:#FAFAF7;margin:0;padding:0}
              .container{max-width:520px;margin:40px auto;background:white;border-radius:20px;
                border:1px solid #EDE8DF;overflow:hidden}
              .header{background:#1E1A16;padding:32px;text-align:center}
              .body{padding:40px}
              .otp-box{background:#edf4f4;border:2px solid #c5e0e1;border-radius:16px;
                padding:28px;text-align:center;margin:24px 0}
              .otp-code{font-size:42px;font-weight:900;color:#4A7C7E;letter-spacing:12px;
                font-family:monospace}
              .footer{background:#F5F3EF;padding:20px;text-align:center;
                font-size:12px;color:#9E9082}
            </style></head><body>
            <div class="container">
              <div class="header">
                <h1 style="color:white;margin:0;font-size:24px">Digital Is Yours</h1>
                <p style="color:rgba(255,255,255,0.5);margin:8px 0 0;font-size:13px">
                  Réinitialisation de mot de passe
                </p>
              </div>
              <div class="body">
                <h2 style="color:#1E1A16;margin:0 0 16px">Bonjour %s,</h2>
                <p style="color:#6B5F52;line-height:1.6">
                  Vous avez demandé à réinitialiser votre mot de passe.
                  Voici votre code :
                </p>
                <div class="otp-box">
                  <div class="otp-code">%s</div>
                  <p style="color:#9E9082;font-size:13px;margin:12px 0 0">
                    ⏱ Ce code expire dans <strong>5 minutes</strong>
                  </p>
                </div>
                <p style="color:#c0392b;font-size:13px;font-weight:600">
                  🔒 Si vous n'avez pas demandé cette réinitialisation, contactez-nous immédiatement.
                </p>
              </div>
              <div class="footer">
                © 2026 Digital Is Yours · Tous droits réservés
              </div>
            </div>
            </body></html>
            """.formatted(prenom, code);
    }

    private String buildWelcomeEmail(String prenom, String role) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              body{font-family:'DM Sans',Arial,sans-serif;background:#FAFAF7;margin:0;padding:0}
              .container{max-width:520px;margin:40px auto;background:white;border-radius:20px;
                border:1px solid #EDE8DF;overflow:hidden}
              .header{background:linear-gradient(135deg,#8B3A3A,#a84848);padding:40px;text-align:center}
              .body{padding:40px;text-align:center}
              .btn{display:inline-block;background:#8B3A3A;color:white;padding:14px 32px;
                border-radius:50px;text-decoration:none;font-weight:600;margin:24px 0}
              .footer{background:#F5F3EF;padding:20px;text-align:center;
                font-size:12px;color:#9E9082}
            </style></head><body>
            <div class="container">
              <div class="header">
                <div style="font-size:48px;margin-bottom:12px">🎉</div>
                <h1 style="color:white;margin:0;font-size:26px">Bienvenue !</h1>
              </div>
              <div class="body">
                <h2 style="color:#1E1A16">Bonjour %s,</h2>
                <p style="color:#6B5F52;line-height:1.6">
                  Votre compte <strong>%s</strong> sur Digital Is Yours est maintenant activé.
                  Vous pouvez commencer votre parcours d'apprentissage !
                </p>
                <a href="http://localhost:4200/login" class="btn">
                  Accéder à mon espace →
                </a>
              </div>
              <div class="footer">© 2026 Digital Is Yours · Tous droits réservés</div>
            </div>
            </body></html>
            """.formatted(prenom, role);
    }
}
