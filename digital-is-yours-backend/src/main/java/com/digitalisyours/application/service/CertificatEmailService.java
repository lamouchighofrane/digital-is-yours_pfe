package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.infrastructure.pdf.CertificatPdfGenerator;
import com.digitalisyours.infrastructure.persistence.repository.CertificatJpaRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificatEmailService {

    private final JavaMailSender          mailSender;
    private final CertificatPdfGenerator  pdfGenerator;
    private final CertificatJpaRepository certificatJpaRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ══════════════════════════════════════════════════════
    // POINT D'ENTRÉE
    // ══════════════════════════════════════════════════════

    @Transactional
    public void envoyerCertificat(Certificat cert) {
        try {
            // 1. Générer le PDF
            byte[] pdfBytes = pdfGenerator.generer(cert);

            // 2. Construire l'email HTML
            String dateStr = cert.getDateCreation() != null
                    ? cert.getDateCreation().format(
                    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
                    : "";

            String html = buildCertificatEmail(
                    cert.getApprenantPrenom(),
                    cert.getFormationTitre(),
                    cert.getNumeroCertificat(),
                    cert.getNoteFinal() != null ? cert.getNoteFinal() : 0f,
                    dateStr
            );

            // 3. Construire le nom du fichier PDF
            String nomFichier = "certificat_"
                    + cert.getNumeroCertificat()
                    .replace("#", "")
                    .replace("-", "_")
                    + ".pdf";

            // 4. Envoyer avec pièce jointe
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(cert.getApprenantEmail());
            helper.setSubject("🎓 Votre certificat Digital Is Yours — "
                    + cert.getFormationTitre());
            helper.setText(html, true);
            helper.addAttachment(nomFichier,
                    new ByteArrayResource(pdfBytes),
                    "application/pdf");

            mailSender.send(message);

            // 5. Marquer estEnvoye = true en base
            certificatJpaRepository.updateEstEnvoye(cert.getId(), true);

            log.info("Email certificat envoyé à {} — {}",
                    cert.getApprenantEmail(), cert.getNumeroCertificat());

        } catch (Exception e) {
            log.error("Erreur envoi email certificat {} : {}",
                    cert.getNumeroCertificat(), e.getMessage());
            throw new RuntimeException("Erreur envoi email : " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════
    // TEMPLATE HTML — score circle via table (compatible Gmail)
    // ══════════════════════════════════════════════════════

    private String buildCertificatEmail(String prenom, String formationTitre,
                                        String numeroCertificat,
                                        float noteFinal, String dateCreation) {

        String scoreColor = noteFinal >= 90 ? "#27ae60"
                : noteFinal >= 80 ? "#4A7C7E"
                : "#e67e22";

        String mention = noteFinal >= 90 ? "Mention Très Bien 🏆"
                : noteFinal >= 80 ? "Mention Bien 🎉"
                : "Admis(e) ✓";

        return """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8">
            <style>
              body{font-family:'DM Sans',Arial,sans-serif;background:#F5F1EB;margin:0;padding:0}
              .wrapper{max-width:600px;margin:32px auto;padding:0 16px}
              .container{background:#FFF;border-radius:22px;border:1px solid #EDE8DF;overflow:hidden;box-shadow:0 8px 40px rgba(26,22,18,.12)}
 
              /* ── Header ── */
              .header{background:linear-gradient(135deg,#0F1E50 0%%,#1a3060 55%%,#2C5F61 100%%);padding:52px 40px 44px;text-align:center}
              .trophy-ring{width:96px;height:96px;border-radius:50%%;background:rgba(180,140,50,.18);border:2px solid rgba(180,140,50,.45);margin:0 auto 22px;line-height:96px;font-size:44px}
              .header-badge{display:inline-block;background:rgba(180,140,50,.25);border:1px solid rgba(180,140,50,.5);color:#f0c84a;font-size:11px;font-weight:700;letter-spacing:.08em;padding:5px 14px;border-radius:20px;margin-bottom:14px}
              .header-title{font-size:30px;font-weight:700;color:#FFF;margin:0 0 8px;font-family:'Cormorant Garamond',Georgia,serif}
              .header-sub{font-size:13px;color:rgba(255,255,255,.6);margin:0}
 
              /* ── Barre or ── */
              .gold-bar{height:3px;background:linear-gradient(90deg,transparent 0%%,#B48C32 30%%,#f0c84a 50%%,#B48C32 70%%,transparent 100%%)}
 
              /* ── Corps ── */
              .body{padding:44px 40px}
              .greeting{font-size:24px;font-weight:700;color:#1A1612;margin:0 0 10px;font-family:'Cormorant Garamond',Georgia,serif}
              .body-intro{font-size:14px;color:#6B5F52;line-height:1.75;margin:0 0 32px}
 
              /* ── Carte certificat ── */
              .cert-card{background:#F8F6F0;border:1.5px solid #E8E0D5;border-radius:18px;padding:0;margin-bottom:28px;overflow:hidden}
              .cert-card-top{height:5px;background:linear-gradient(90deg,#0F1E50,#B48C32,#4A7C7E)}
              .cert-card-inner{padding:28px 32px}
              .cert-section-label{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.12em;color:#9B8B6E;margin:0 0 6px;display:block}
              .cert-formation-titre{font-size:20px;font-weight:700;color:#1A1612;margin:0 0 22px;font-family:'Cormorant Garamond',Georgia,serif;line-height:1.3}
 
              /* ── Grille meta (table) ── */
              .meta-table{width:100%%;border-collapse:separate;border-spacing:10px 0;margin-bottom:20px}
              .meta-cell{background:#FAFAF7;border:1px solid #EDE8DF;border-radius:12px;padding:14px 16px;vertical-align:top}
              .meta-label{font-size:10px;font-weight:700;color:#9B8B6E;text-transform:uppercase;letter-spacing:.08em;display:block;margin-bottom:5px}
              .meta-val{font-size:14px;font-weight:700;color:#1A1612;margin:0}
 
              /* ── Score (table — compatible Gmail) ── */
              .score-wrap{width:100%%;border-collapse:collapse}
              .score-circle-td{width:74px;vertical-align:middle;padding-right:16px}
              .score-circle{width:62px;height:62px;border-radius:50%%;border:2.5px solid %s;background:%s1a;text-align:center;padding-top:12px;box-sizing:border-box}
              .score-val{font-size:15px;font-weight:800;color:%s;line-height:1;display:block}
              .score-txt{font-size:9px;color:#9B8B6E;display:block;margin-top:2px}
              .score-info-td{vertical-align:middle}
              .score-pct{font-size:26px;font-weight:800;color:%s;margin:0;line-height:1}
              .score-mention{font-size:13px;font-weight:600;color:%s;margin:4px 0 0}
 
              /* ── N° Certificat ── */
              .cert-num-zone{background:#F5F1EB;border-radius:8px;padding:10px 14px;margin-top:8px;display:inline-block}
              .cert-num-label{font-size:10px;color:#9B8B6E;font-weight:600}
              .cert-num-val{font-size:12px;font-weight:700;color:#4A7C7E;font-family:monospace;letter-spacing:.05em;margin-left:6px}
 
              /* ── Notice pièce jointe ── */
              .attach-notice{border:1px solid rgba(74,124,126,.2);border-radius:14px;padding:0;margin-bottom:28px;overflow:hidden;background:rgba(74,124,126,.07)}
              .attach-table{width:100%%;border-collapse:collapse}
              .attach-icon-td{width:66px;background:#4A7C7E;text-align:center;vertical-align:middle;font-size:26px;padding:18px}
              .attach-body-td{padding:14px 18px;vertical-align:middle}
              .attach-title{font-size:14px;font-weight:700;color:#1A1612;margin:0 0 4px}
              .attach-sub{font-size:12px;color:#6B5F52;margin:0}
 
              /* ── Bannière suite ── */
              .next-banner{background:linear-gradient(135deg,#1A1612 0%%,#2C5F61 100%%);border-radius:14px;padding:22px 28px;text-align:center;margin-bottom:28px}
              .nb-title{font-size:15px;font-weight:700;color:#FFF;margin:0 0 6px}
              .nb-sub{font-size:12px;color:rgba(255,255,255,.6);margin:0}
 
              /* ── Footer ── */
              .footer{background:#F5F3EF;padding:22px 40px;text-align:center;border-top:1px solid #EDE8DF}
              .footer-logo{font-size:14px;font-weight:700;color:#1A1612;margin:0 0 4px;font-family:'Cormorant Garamond',Georgia,serif}
              .footer-text{font-size:11px;color:#9B8B6E;margin:0}
            </style>
            </head><body>
            <div class="wrapper">
            <div class="container">
 
              <!-- Header -->
              <div class="header">
                <div class="trophy-ring">🏆</div>
                <div class="header-badge">CERTIFICAT OFFICIEL</div>
                <h1 class="header-title">Félicitations !</h1>
                <p class="header-sub">Digital Is Yours — Académie en ligne</p>
              </div>
              <div class="gold-bar"></div>
 
              <!-- Corps -->
              <div class="body">
                <h2 class="greeting">Bravo, %s !</h2>
                <p class="body-intro">
                  Votre travail et votre persévérance ont porté leurs fruits. 🎉<br>
                  Vous avez brillamment réussi l'évaluation finale et obtenu votre
                  <strong>certificat officiel Digital Is Yours</strong>.
                  C'est une belle étape dans votre parcours d'apprentissage —
                  soyez fier(e) de cette réussite !
                </p>
 
                <!-- Carte certificat -->
                <div class="cert-card">
                  <div class="cert-card-top"></div>
                  <div class="cert-card-inner">
 
                    <span class="cert-section-label">🎓 Formation certifiée</span>
                    <p class="cert-formation-titre">%s</p>
 
                    <!-- Grille meta : date + score -->
                    <table class="meta-table">
                      <tr>
                        <!-- Colonne date -->
                        <td class="meta-cell" style="width:48%%">
                          <span class="meta-label">📅 Date d'obtention</span>
                          <p class="meta-val">%s</p>
                        </td>
                        <!-- Colonne score -->
                        <td class="meta-cell" style="width:52%%">
                          <span class="meta-label">🎯 Score obtenu</span>
                          <table class="score-wrap">
                            <tr>
                              <td class="score-circle-td">
                                <div class="score-circle">
                                  <span class="score-val">%.0f%%</span>
                                  <span class="score-txt">score</span>
                                </div>
                              </td>
                              <td class="score-info-td">
                                <p class="score-pct">%.0f%%</p>
                                <p class="score-mention">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
 
                    <!-- N° Certificat -->
                    <div class="cert-num-zone">
                      <span class="cert-num-label">🔖 N° Certificat</span>
                      <span class="cert-num-val">%s</span>
                    </div>
 
                  </div>
                </div>
 
                <!-- Notice PDF -->
                <div class="attach-notice">
                  <table class="attach-table">
                    <tr>
                      <td class="attach-icon-td">📎</td>
                      <td class="attach-body-td">
                       <p class="attach-title">Votre certificat PDF est en pièce jointe</p>
                           <p class="attach-sub">
                             Le fichier officiel est joint à cet email.<br>
                             Faites défiler vers le bas pour voir et télécharger le fichier.<br>
                             Conservez-le précieusement pour votre portfolio professionnel.
                           </p>
                      </td>
                    </tr>
                  </table>
                </div>
 
                <!-- Bannière suivante -->
                <div class="next-banner">
                  <p class="nb-title">🌟 Continuez sur cette lancée !</p>
                  <p class="nb-sub">
                    Explorez d'autres formations et renforcez encore vos compétences sur
                    Digital Is Yours.
                  </p>
                </div>
              </div>
 
              <!-- Footer -->
              <div class="footer">
                <p class="footer-logo">Digital Is Yours</p>
                <p class="footer-text">© 2026 Digital Is Yours · Académie en ligne · Tous droits réservés</p>
              </div>
 
            </div>
            </div>
            </body></html>
            """.formatted(
                // score-circle border + bg + val (5× scoreColor pour les 5 %s CSS)
                scoreColor, scoreColor, scoreColor, scoreColor, scoreColor,
                // body
                prenom,
                formationTitre,
                dateCreation,
                noteFinal, noteFinal, mention,
                numeroCertificat
        );
    }
}