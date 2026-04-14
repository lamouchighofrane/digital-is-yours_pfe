package com.digitalisyours.application.service;

import com.digitalisyours.infrastructure.persistence.entity.AnalyseRisqueEntity;
import com.digitalisyours.infrastructure.persistence.entity.ApprenantEntity;
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
public class RisqueEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    // ═══════════════════════════════════════════════════════
    // MÉTHODE PRINCIPALE — appelée pour TOUS les niveaux
    // ═══════════════════════════════════════════════════════

    public void envoyerEmailMotivation(ApprenantEntity apprenant,
                                       AnalyseRisqueEntity analyse) {
        try {
            String sujet = buildSujet(analyse);
            String html  = buildEmailHtml(apprenant, analyse);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(apprenant.getEmail());
            helper.setSubject(sujet);
            helper.setText(html, true);
            mailSender.send(message);

            log.info("Email {} envoyé à {} ({})",
                    analyse.getNiveauRisque(),
                    apprenant.getEmail(),
                    analyse.getNiveauRisque());

        } catch (Exception e) {
            log.error("Erreur email risque : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ═══════════════════════════════════════════════════════
    // SUJET selon le niveau
    // ═══════════════════════════════════════════════════════

    private String buildSujet(AnalyseRisqueEntity analyse) {
        return switch (analyse.getNiveauRisque()) {
            case "ELEVE"  -> "🚨 Reprenez votre formation — nous avons besoin de vous !";
            case "MOYEN"  -> "💪 Continuez vos efforts — vous êtes sur la bonne voie !";
            default       -> "✅ Excellent travail — continuez ainsi !";
        };
    }

    // ═══════════════════════════════════════════════════════
    // HTML selon le niveau
    // ═══════════════════════════════════════════════════════

    private String buildEmailHtml(ApprenantEntity apprenant,
                                  AnalyseRisqueEntity analyse) {

        // ── Couleurs selon le niveau ──────────────────────────────
        String couleurPrimaire = switch (analyse.getNiveauRisque()) {
            case "ELEVE"  -> "#e74c3c";
            case "MOYEN"  -> "#f39c12";
            default       -> "#27ae60";
        };
        String couleurBg = switch (analyse.getNiveauRisque()) {
            case "ELEVE"  -> "#fef2f2";
            case "MOYEN"  -> "#fffbf0";
            default       -> "#f0fdf4";
        };
        String emoji = switch (analyse.getNiveauRisque()) {
            case "ELEVE"  -> "🚨";
            case "MOYEN"  -> "💪";
            default       -> "🎉";
        };
        String titreHero = switch (analyse.getNiveauRisque()) {
            case "ELEVE"  -> "On a besoin de vous !";
            case "MOYEN"  -> "Continuez sur votre lancée !";
            default       -> "Vous êtes en pleine forme !";
        };
        String sousTitre = switch (analyse.getNiveauRisque()) {
            case "ELEVE"  -> "Votre formation vous attend";
            case "MOYEN"  -> "Quelques efforts et vous y êtes";
            default       -> "Continuez comme ça pour décrocher votre certificat";
        };
        String ctaTexte = switch (analyse.getNiveauRisque()) {
            case "ELEVE"  -> "🚀 Reprendre ma formation maintenant";
            case "MOYEN"  -> "▶ Continuer ma formation";
            default       -> "🏆 Voir ma progression";
        };

        String lienFormation = baseUrl +
                "/apprenant/dashboard?formationId=" + analyse.getFormationId();

        float prog     = analyse.getProgression()    != null ? analyse.getProgression()    : 0f;
        int   jours    = analyse.getJoursInactivite() != null ? analyse.getJoursInactivite() : 0;
        float quiz     = analyse.getScoreMoyenQuiz()  != null ? analyse.getScoreMoyenQuiz()  : 0f;
        int   videos   = analyse.getNbVideosVues()    != null ? analyse.getNbVideosVues()    : 0;
        int   quizNb   = analyse.getNbQuizPasses()    != null ? analyse.getNbQuizPasses()    : 0;

        String progressBar = buildProgressBar(prog, couleurPrimaire);

        return """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              *{box-sizing:border-box;margin:0;padding:0}
              body{font-family:'Segoe UI',Arial,sans-serif;background:#F5F1EB;color:#1A1612}
              .wrapper{max-width:600px;margin:24px auto;padding:0 16px}
              .card{background:#FFF;border-radius:20px;overflow:hidden;
                    box-shadow:0 8px 32px rgba(26,22,18,.12)}
              /* Hero */
              .hero{padding:44px 40px 36px;text-align:center;
                    background:linear-gradient(135deg,#0F1E50 0%%,#1a3060 50%%,#2C5F61 100%%)}
              .hero-emoji{font-size:52px;display:block;margin-bottom:14px}
              .hero-titre{font-size:24px;font-weight:700;color:#FFF;margin:0 0 6px}
              .hero-sous{font-size:13px;color:rgba(255,255,255,.7);margin:0}
              .badge-niveau{display:inline-block;padding:6px 18px;border-radius:20px;
                font-size:12px;font-weight:700;margin-top:14px;
                background:%s;color:#FFF}
              /* Body */
              .body{padding:32px 40px}
              .greeting{font-size:20px;font-weight:700;margin:0 0 12px}
              .intro{font-size:14px;color:#6B5F52;line-height:1.7;margin:0 0 24px}
              /* Stats */
              .stats-titre{font-size:11px;font-weight:700;color:#9B8B6E;
                text-transform:uppercase;letter-spacing:.1em;margin:0 0 14px}
              .stats-grid{display:grid;grid-template-columns:repeat(4,1fr);
                gap:10px;margin-bottom:24px}
              .stat-box{background:#FAFAF7;border:1px solid #EDE8DF;
                border-radius:12px;padding:14px 8px;text-align:center}
              .stat-val{font-size:20px;font-weight:800;display:block;line-height:1}
              .stat-lbl{font-size:10px;color:#9B8B6E;margin-top:4px;display:block}
              /* Progress */
              .prog-zone{margin-bottom:24px}
              .prog-label{display:flex;justify-content:space-between;
                font-size:12px;font-weight:600;color:#6B5F52;margin-bottom:8px}
              .prog-track{height:10px;background:#EDE8DF;border-radius:10px;overflow:hidden}
              .prog-fill{height:100%%;border-radius:10px;
                background:%s;transition:width .5s}
              /* Explication */
              .expl-box{background:%s;border-left:4px solid %s;
                border-radius:0 12px 12px 0;padding:16px 18px;margin-bottom:24px}
              .expl-titre{font-size:11px;font-weight:700;
                text-transform:uppercase;letter-spacing:.1em;
                color:%s;margin:0 0 6px}
              .expl-text{font-size:13px;color:#1A1612;margin:0;line-height:1.6}
              /* Recommandation */
              .reco-box{background:linear-gradient(135deg,rgba(74,124,126,.08),
                rgba(74,124,126,.04));border:1px solid rgba(74,124,126,.2);
                border-radius:14px;padding:20px;margin-bottom:28px}
              .reco-titre{font-size:11px;font-weight:700;color:#4A7C7E;
                text-transform:uppercase;letter-spacing:.1em;margin:0 0 8px}
              .reco-text{font-size:14px;color:#1A1612;line-height:1.7;
                margin:0;font-style:italic}
              /* CTA */
              .cta{display:block;background:%s;color:#FFF;
                text-decoration:none;text-align:center;padding:16px 32px;
                border-radius:12px;font-size:15px;font-weight:700;
                box-shadow:0 4px 14px rgba(74,124,126,.35)}
              /* Footer */
              .footer{background:#F5F3EF;padding:20px 40px;text-align:center;
                border-top:1px solid #EDE8DF}
              .footer-text{font-size:11px;color:#9B8B6E;margin:0}
              /* Responsive */
              @media(max-width:500px){
                .body{padding:24px 20px}
                .stats-grid{grid-template-columns:repeat(2,1fr)}
              }
            </style>
            </head><body>
            <div class="wrapper"><div class="card">

              <!-- Hero -->
              <div class="hero">
                <span class="hero-emoji">%s</span>
                <h1 class="hero-titre">%s</h1>
                <p class="hero-sous">%s</p>
                <span class="badge-niveau">%s</span>
              </div>

              <!-- Body -->
              <div class="body">
                <p class="greeting">Bonjour %s !</p>
                <p class="intro">
                  Votre coach Digital Is Yours a analysé votre activité sur
                  <strong>%s</strong>. Voici votre bilan personnalisé.
                </p>

                <!-- Stats -->
                <p class="stats-titre">📊 Votre tableau de bord</p>
                <div class="stats-grid">
                  <div class="stat-box">
                    <span class="stat-val" style="color:%s">%.0f%%</span>
                    <span class="stat-lbl">Progression</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-val" style="color:%s">%dj</span>
                    <span class="stat-lbl">Inactivité</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-val" style="color:%s">%.0f%%</span>
                    <span class="stat-lbl">Score quiz</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-val" style="color:#4A7C7E">%d/%d</span>
                    <span class="stat-lbl">Quiz/Vidéos</span>
                  </div>
                </div>

                <!-- Barre progression -->
                <div class="prog-zone">
                  <div class="prog-label">
                    <span>Progression formation</span>
                    <span>%.0f%%</span>
                  </div>
                  <div class="prog-track">
                    <div class="prog-fill" style="width:%.0f%%"></div>
                  </div>
                </div>

                <!-- Explication -->
                <div class="expl-box">
                  <p class="expl-titre">🔍 Diagnostic</p>
                  <p class="expl-text">%s</p>
                </div>

                <!-- Recommandation -->
                <div class="reco-box">
                  <p class="reco-titre">💡 Conseil personnalisé</p>
                  <p class="reco-text">%s</p>
                </div>

                <!-- CTA -->
                <a href="%s" class="cta">%s</a>
              </div>

              <!-- Footer -->
              <div class="footer">
                <p class="footer-text">
                  © 2026 Digital Is Yours · Académie en ligne ·
                  <a href="%s" style="color:#4A7C7E;text-decoration:none">
                    Se désabonner
                  </a>
                </p>
              </div>

            </div></div>
            </body></html>
            """.formatted(
                // Badge niveau
                couleurPrimaire,
                // Barre progression
                couleurPrimaire,
                // Explication box
                couleurBg, couleurPrimaire, couleurPrimaire,
                // CTA
                couleurPrimaire,
                // Hero
                emoji, titreHero, sousTitre,
                // Badge niveau texte
                "ELEVE".equals(analyse.getNiveauRisque()) ? "⚠️ Risque élevé"
                        : "MOYEN".equals(analyse.getNiveauRisque()) ? "📊 À surveiller"
                        : "✅ Bonne progression",
                // Greeting
                apprenant.getPrenom() != null ? apprenant.getPrenom() : "Apprenant",
                // Nom formation
                analyse.getFormationTitre() != null
                        ? analyse.getFormationTitre()
                        : "votre formation",
                // Stats couleurs
                couleurPrimaire, prog,
                jours > 7 ? "#e74c3c" : "#27ae60", jours,
                quiz < 50 ? "#e74c3c" : "#27ae60", quiz,
                quizNb, videos,
                // Progress bar
                prog, prog,
                // Explication
                analyse.getExplication() != null
                        ? analyse.getExplication() : "Analyse en cours.",
                // Recommandation
                analyse.getRecommandationIA() != null
                        ? analyse.getRecommandationIA() : "",
                // CTA
                lienFormation, ctaTexte,
                // Footer
                lienFormation
        );
    }

    private String buildProgressBar(float prog, String couleur) {
        return String.format(
                "<div style='height:10px;background:#EDE8DF;border-radius:10px;overflow:hidden'>" +
                        "<div style='width:%.0f%%;height:100%%;background:%s;border-radius:10px'></div></div>",
                Math.min(prog, 100), couleur
        );
    }
}