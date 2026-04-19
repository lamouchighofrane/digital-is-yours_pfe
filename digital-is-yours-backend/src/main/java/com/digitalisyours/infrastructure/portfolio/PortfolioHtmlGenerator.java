package com.digitalisyours.infrastructure.portfolio;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.model.Competence;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PortfolioHtmlGenerator {

    public String generer(Apprenant apprenant,
                          List<Certificat> certificats,
                          Map<Long, List<Competence>> competencesParFormation,
                          String portfolioUrl) {

        String prenom    = safe(apprenant.getPrenom());
        String nom       = safe(apprenant.getNom());
        String email     = safe(apprenant.getEmail());
        String bio       = safe(apprenant.getBio());
        String niveau    = nvLabel(apprenant.getNiveauActuel());
        String initiales = getInitiales(prenom, nom);

        String photoHtml = (apprenant.getPhoto() != null && !apprenant.getPhoto().isBlank())
                ? "<img src=\"" + escapeHtml(apprenant.getPhoto())
                + "\" alt=\"Photo\" class=\"avatar-img\">"
                : "<div class=\"avatar-initiales\">" + escapeHtml(initiales) + "</div>";

        String bioHtml = bio.isBlank()
                ? ""
                : "<p class=\"hero-bio\">" + escapeHtml(bio) + "</p>";

        String domainesHtml    = buildDomainesHtml(apprenant);
        String statsHtml       = buildStatsHtml(certificats);
        String certificatsHtml = buildCertificatsHtml(certificats, competencesParFormation);

        // ── CSS séparé (pas de String.formatted → pas de conflit avec %) ──
        String css = buildCss();

        // ── HTML final assemblé avec StringBuilder ─────────────────────
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <meta name=\"description\" content=\"Portfolio certifié de ")
                .append(escapeHtml(prenom)).append(" ").append(escapeHtml(nom))
                .append(" — Digital Is Yours\">\n");
        html.append("  <meta property=\"og:title\" content=\"")
                .append(escapeHtml(prenom)).append(" ").append(escapeHtml(nom))
                .append(" | Portfolio Digital Is Yours\">\n");
        html.append("  <meta property=\"og:description\" content=\"")
                .append(certificats.size()).append(" certification(s) — Digital Is Yours\">\n");
        html.append("  <title>").append(escapeHtml(prenom)).append(" ")
                .append(escapeHtml(nom)).append(" — Portfolio Digital Is Yours</title>\n");
        html.append("  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n");
        html.append("  <link href=\"https://fonts.googleapis.com/css2?family=Cormorant+Garamond")
                .append(":wght@400;600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700;800")
                .append("&display=swap\" rel=\"stylesheet\">\n");
        html.append("  <style>\n").append(css).append("\n  </style>\n");
        html.append("</head>\n<body>\n\n");

        // Hero
        html.append("<div class=\"hero\">\n  <div class=\"hero-inner\">\n");
        html.append("    <div>").append(photoHtml).append("</div>\n");
        html.append("    <div class=\"hero-info\">\n");
        html.append("      <div class=\"hero-badge\">✦ PORTFOLIO CERTIFIÉ</div>\n");
        html.append("      <h1 class=\"hero-name\">").append(escapeHtml(prenom))
                .append(" ").append(escapeHtml(nom)).append("</h1>\n");
        html.append("      <span class=\"hero-niveau\">").append(escapeHtml(niveau))
                .append("</span>\n");
        html.append("      ").append(bioHtml).append("\n");
        html.append("      <div class=\"hero-email\">📧 ")
                .append(escapeHtml(email)).append("</div>\n");
        html.append("    </div>\n  </div>\n</div>\n");
        html.append("<div class=\"gold-bar\"></div>\n\n");

        // Main content
        html.append("<div class=\"main-container\">\n");
        html.append("  <div class=\"stats-grid\">").append(statsHtml).append("</div>\n");
        html.append(domainesHtml).append("\n");
        html.append("  <div class=\"certificats-section\">\n");
        html.append("    <div class=\"section-header\">\n");
        html.append("      <div class=\"section-icon\">🏆</div>\n");
        html.append("      <h2 class=\"section-title\">Certifications obtenues</h2>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"cert-list\">").append(certificatsHtml).append("</div>\n");
        html.append("  </div>\n</div>\n\n");

        // Footer
        html.append("<div class=\"footer\">\n");
        html.append("  <div class=\"footer-logo\">Digital Is Yours</div>\n");
        html.append("  <div class=\"footer-sub\">")
                .append("© 2026 Digital Is Yours · Académie en ligne · Tous droits réservés")
                .append("</div>\n");
        html.append("  <div class=\"footer-verified\">")
                .append("✓ Portfolio vérifié et certifié par Digital Is Yours</div>\n");
        html.append("</div>\n\n</body>\n</html>");

        return html.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    // CSS — méthode séparée, SANS String.formatted(), pas de conflit %
    // ══════════════════════════════════════════════════════════════════════

    private String buildCss() {
        return """
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
:root {
  --navy:  #0F1E50;
  --teal:  #4A7C7E;
  --gold:  #B48C32;
  --cream: #F5F1EB;
  --white: #FFFFFF;
  --dark:  #1A1612;
  --gray:  #6B5F52;
  --light: #E8E3DB;
  --green: #27ae60;
}
body {
  font-family: 'Plus Jakarta Sans', sans-serif;
  background: var(--cream);
  color: var(--dark);
  min-height: 100vh;
}

/* ── HERO ── */
.hero {
  background: linear-gradient(135deg, var(--navy) 0%, #1a3060 55%, var(--teal) 100%);
  position: relative; overflow: hidden;
}
.hero::before {
  content: ''; position: absolute;
  top: -80px; right: -80px;
  width: 400px; height: 400px; border-radius: 50%;
  background: rgba(180,140,50,.08);
}
.hero::after {
  content: ''; position: absolute;
  bottom: -60px; left: -60px;
  width: 300px; height: 300px; border-radius: 50%;
  background: rgba(74,124,126,.12);
}
.hero-inner {
  max-width: 920px; margin: 0 auto;
  padding: 60px 24px 50px; position: relative; z-index: 1;
  display: flex; align-items: center; gap: 40px; flex-wrap: wrap;
}
.avatar-img {
  width: 110px; height: 110px; border-radius: 50%;
  border: 4px solid var(--gold); object-fit: cover;
  box-shadow: 0 8px 32px rgba(0,0,0,.3);
}
.avatar-initiales {
  width: 110px; height: 110px; border-radius: 50%;
  background: linear-gradient(135deg, var(--teal), #2C5F61);
  border: 4px solid var(--gold);
  display: flex; align-items: center; justify-content: center;
  font-family: 'Cormorant Garamond', serif;
  font-size: 36px; font-weight: 700; color: #FFF;
  box-shadow: 0 8px 32px rgba(0,0,0,.3); flex-shrink: 0;
}
.hero-info { flex: 1; min-width: 200px; }
.hero-badge {
  display: inline-flex; align-items: center; gap: 6px;
  background: rgba(180,140,50,.2); border: 1px solid rgba(180,140,50,.4);
  color: #f0c84a; font-size: 11px; font-weight: 700;
  padding: 4px 12px; border-radius: 20px; margin-bottom: 12px;
  letter-spacing: .06em;
}
.hero-name {
  font-family: 'Cormorant Garamond', serif;
  font-size: 48px; font-weight: 700; color: #FFF;
  line-height: 1.1; margin-bottom: 8px;
}
.hero-niveau {
  display: inline-block;
  background: rgba(74,124,126,.3); border: 1px solid rgba(74,124,126,.5);
  color: #7dcacb; font-size: 12px; font-weight: 600;
  padding: 4px 12px; border-radius: 20px; margin-bottom: 14px;
}
.hero-bio {
  font-size: 14px; color: rgba(255,255,255,.75);
  line-height: 1.7; max-width: 500px; margin-bottom: 8px;
}
.hero-email {
  display: flex; align-items: center; gap: 6px; margin-top: 12px;
  font-size: 13px; color: rgba(255,255,255,.55);
}
.gold-bar {
  height: 4px;
  background: linear-gradient(90deg, transparent, var(--gold), #f0c84a, var(--gold), transparent);
}

/* ── CONTAINER ── */
.main-container { max-width: 920px; margin: 0 auto; padding: 40px 24px 60px; }

/* ── STATS ── */
.stats-grid {
  display: grid; grid-template-columns: repeat(3,1fr);
  gap: 16px; margin-bottom: 36px;
}
.stat-card {
  background: var(--white); border: 1.5px solid var(--light);
  border-radius: 18px; padding: 22px; text-align: center;
  box-shadow: 0 2px 12px rgba(26,22,18,.05); transition: transform .2s;
}
.stat-card:hover { transform: translateY(-3px); }
.stat-icon { font-size: 28px; margin-bottom: 8px; }
.stat-val {
  font-family: 'Cormorant Garamond', serif;
  font-size: 36px; font-weight: 700; color: var(--teal); line-height: 1;
}
.stat-label { font-size: 12px; color: var(--gray); margin-top: 4px; font-weight: 500; }

/* ── SECTION HEADER ── */
.section-header {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 20px; padding-bottom: 12px;
  border-bottom: 2px solid var(--light);
}
.section-icon {
  width: 40px; height: 40px; border-radius: 12px;
  background: rgba(74,124,126,.1); border: 1px solid rgba(74,124,126,.2);
  display: flex; align-items: center; justify-content: center; font-size: 18px;
}
.section-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 24px; font-weight: 700; color: var(--dark);
}

/* ── DOMAINES ── */
.domaines-section { margin-bottom: 36px; }
.domaines-wrap { display: flex; flex-wrap: wrap; gap: 10px; }
.domaine-tag {
  background: var(--white); border: 1.5px solid var(--light);
  border-radius: 25px; padding: 8px 18px;
  font-size: 13px; font-weight: 600; color: var(--dark); transition: all .15s;
}
.domaine-tag:hover {
  border-color: var(--teal); color: var(--teal);
  background: rgba(74,124,126,.05);
}

/* ── CERTIFICATS ── */
.certificats-section { margin-bottom: 36px; }
.cert-list { display: flex; flex-direction: column; gap: 20px; }
.cert-card {
  background: var(--white); border: 1.5px solid var(--light);
  border-radius: 18px; overflow: hidden;
  box-shadow: 0 2px 12px rgba(26,22,18,.05);
  transition: all .22s; display: flex;
}
.cert-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(26,22,18,.1);
  border-color: var(--teal);
}
.cert-accent {
  width: 6px; flex-shrink: 0;
  background: linear-gradient(to bottom, var(--navy), var(--teal));
}
.cert-body { flex: 1; padding: 22px 24px; }
.cert-top-row {
  display: flex; align-items: flex-start;
  justify-content: space-between; gap: 12px;
  margin-bottom: 12px; flex-wrap: wrap;
}
.cert-titre { font-size: 17px; font-weight: 700; color: var(--dark); margin: 0; }
.cert-score-badge {
  display: flex; align-items: center; gap: 6px;
  background: rgba(39,174,96,.1); border: 1.5px solid rgba(39,174,96,.25);
  border-radius: 25px; padding: 6px 16px; flex-shrink: 0;
}
.cert-score-val { font-size: 20px; font-weight: 800; color: var(--green); line-height: 1; }
.cert-score-lbl {
  font-size: 10px; color: var(--gray);
  font-weight: 700; letter-spacing: .05em;
}
.cert-meta {
  display: flex; align-items: center;
  gap: 10px; flex-wrap: wrap; margin-bottom: 16px;
}
.cert-chip {
  display: flex; align-items: center; gap: 5px;
  font-size: 11px; font-weight: 600; color: var(--gray);
  background: var(--cream); padding: 4px 10px; border-radius: 20px;
}
.cert-competences {
  border-top: 1px solid var(--light);
  padding-top: 14px; margin-bottom: 14px;
}
.cert-comp-label {
  font-size: 11px; font-weight: 700; color: var(--gray);
  text-transform: uppercase; letter-spacing: .08em;
  margin-bottom: 10px; display: flex; align-items: center; gap: 6px;
}
.cert-comp-label::before {
  content: ''; display: inline-block;
  width: 16px; height: 2px;
  background: var(--teal); border-radius: 2px;
}
.cert-comp-grid { display: flex; flex-wrap: wrap; gap: 7px; }
.cert-comp-tag {
  display: inline-flex; align-items: center; gap: 5px;
  background: rgba(74,124,126,.08);
  border: 1px solid rgba(74,124,126,.2);
  color: var(--teal); font-size: 12px; font-weight: 600;
  padding: 5px 12px; border-radius: 20px; transition: all .15s;
}
.cert-comp-tag:hover { background: var(--teal); color: var(--white); }
.cert-comp-dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: currentColor; flex-shrink: 0;
}
.cert-bottom-row {
  display: flex; align-items: center;
  justify-content: space-between; flex-wrap: wrap; gap: 10px;
}
.cert-numero {
  font-family: monospace; font-size: 11px; font-weight: 700;
  color: var(--teal); background: rgba(74,124,126,.08);
  border: 1px solid rgba(74,124,126,.2);
  padding: 4px 10px; border-radius: 6px;
}
.cert-verify-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 9px 18px; background: var(--navy); color: var(--white);
  border: none; border-radius: 8px; text-decoration: none;
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: 12px; font-weight: 700; cursor: pointer; transition: background .2s;
}
.cert-verify-btn:hover { background: var(--teal); }

/* ── FOOTER ── */
.footer { background: var(--dark); padding: 32px 24px; text-align: center; }
.footer-logo {
  font-family: 'Cormorant Garamond', serif;
  font-size: 22px; font-weight: 700; color: var(--white); margin-bottom: 6px;
}
.footer-sub { font-size: 12px; color: rgba(255,255,255,.4); }
.footer-verified {
  display: inline-flex; align-items: center; gap: 6px; margin-top: 12px;
  background: rgba(39,174,96,.15); border: 1px solid rgba(39,174,96,.3);
  border-radius: 20px; padding: 6px 16px;
  font-size: 12px; font-weight: 600; color: var(--green);
}

@media (max-width: 640px) {
  .hero-inner { flex-direction: column; text-align: center; }
  .hero-name { font-size: 36px; }
  .stats-grid { grid-template-columns: 1fr 1fr; }
  .cert-card { flex-direction: column; }
  .cert-accent { width: 100%; height: 5px; }
}
""";
    }

    // ══════════════════════════════════════════════════════════════════════
    // Builders des sections
    // ══════════════════════════════════════════════════════════════════════

    private String buildStatsHtml(List<Certificat> certificats) {
        int total = certificats.size();
        float scoreMoyen = 0f;
        long nbAvecScore = certificats.stream().filter(c -> c.getNoteFinal() != null).count();
        if (nbAvecScore > 0) {
            scoreMoyen = certificats.stream()
                    .filter(c -> c.getNoteFinal() != null)
                    .map(Certificat::getNoteFinal)
                    .reduce(0f, Float::sum) / nbAvecScore;
        }
        long reussis = certificats.stream()
                .filter(c -> c.getNoteFinal() != null
                        && c.getNotePassage() != null
                        && c.getNoteFinal() >= c.getNotePassage())
                .count();

        return "<div class=\"stat-card\"><div class=\"stat-icon\">🎓</div>"
                + "<div class=\"stat-val\">" + total + "</div>"
                + "<div class=\"stat-label\">Certification" + (total > 1 ? "s" : "")
                + " obtenue" + (total > 1 ? "s" : "") + "</div></div>"
                + "<div class=\"stat-card\"><div class=\"stat-icon\">⭐</div>"
                + "<div class=\"stat-val\">" + String.format("%.0f", scoreMoyen) + "%</div>"
                + "<div class=\"stat-label\">Score moyen</div></div>"
                + "<div class=\"stat-card\"><div class=\"stat-icon\">✅</div>"
                + "<div class=\"stat-val\">" + reussis + "</div>"
                + "<div class=\"stat-label\">Formation" + (reussis > 1 ? "s" : "")
                + " complétée" + (reussis > 1 ? "s" : "") + "</div></div>";
    }

    private String buildDomainesHtml(Apprenant apprenant) {
        if (apprenant.getDomainesInteret() == null
                || apprenant.getDomainesInteret().isEmpty()) return "";

        StringBuilder tags = new StringBuilder();
        for (String d : apprenant.getDomainesInteret()) {
            if (d != null && !d.isBlank()) {
                tags.append("<span class=\"domaine-tag\">")
                        .append(escapeHtml(d.trim())).append("</span>");
            }
        }
        if (tags.isEmpty()) return "";

        return "<div class=\"domaines-section\">"
                + "<div class=\"section-header\">"
                + "<div class=\"section-icon\">🎯</div>"
                + "<h2 class=\"section-title\">Domaines d'expertise</h2>"
                + "</div>"
                + "<div class=\"domaines-wrap\">" + tags + "</div>"
                + "</div>";
    }

    private String buildCertificatsHtml(List<Certificat> certificats,
                                        Map<Long, List<Competence>> competencesParFormation) {
        if (certificats.isEmpty()) {
            return "<p style=\"color:#9B8B6E;font-size:14px\">"
                    + "Aucune certification pour le moment.</p>";
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
        StringBuilder sb = new StringBuilder();

        for (Certificat cert : certificats) {
            String dateStr  = cert.getDateCreation() != null
                    ? cert.getDateCreation().format(fmt) : "";
            String score    = cert.getNoteFinal() != null
                    ? String.format("%.0f", cert.getNoteFinal()) : "—";
            String niveau   = nvLabel(cert.getFormationNiveau());
            String numero   = cert.getNumeroCertificat() != null
                    ? cert.getNumeroCertificat() : "";
            String duree    = cert.getFormationDuree() != null
                    ? cert.getFormationDuree() + "h" : "";
            String urlVerif = "http://localhost:8080/api/apprenant/certificats/"
                    + cert.getId() + "/download";
            String titre    = cert.getFormationTitre() != null
                    ? escapeHtml(cert.getFormationTitre()) : "";

            String dureeChip = duree.isBlank() ? ""
                    : "<span class=\"cert-chip\">⏱ " + duree + "</span>";

            String competencesHtml =
                    buildCompetencesHtml(cert.getFormationId(), competencesParFormation);

            sb.append("<div class=\"cert-card\">")
                    .append("<div class=\"cert-accent\"></div>")
                    .append("<div class=\"cert-body\">")
                    .append("<div class=\"cert-top-row\">")
                    .append("<h3 class=\"cert-titre\">").append(titre).append("</h3>")
                    .append("<div class=\"cert-score-badge\">")
                    .append("<span class=\"cert-score-val\">").append(score).append("%</span>")
                    .append("<span class=\"cert-score-lbl\">SCORE</span>")
                    .append("</div></div>")
                    .append("<div class=\"cert-meta\">")
                    .append("<span class=\"cert-chip\">📅 ").append(escapeHtml(dateStr)).append("</span>")
                    .append("<span class=\"cert-chip\">📊 ").append(escapeHtml(niveau)).append("</span>")
                    .append(dureeChip)
                    .append("</div>")
                    .append(competencesHtml)
                    .append("<div class=\"cert-bottom-row\">")
                    .append("<span class=\"cert-numero\">🔖 ").append(escapeHtml(numero)).append("</span>")
                    .append("<a href=\"").append(urlVerif)
                    .append("\" target=\"_blank\" class=\"cert-verify-btn\">")
                    .append("✓ Vérifier l'authenticité</a>")
                    .append("</div>")
                    .append("</div></div>");
        }
        return sb.toString();
    }

    private String buildCompetencesHtml(Long formationId,
                                        Map<Long, List<Competence>> competencesParFormation) {
        if (formationId == null || competencesParFormation == null
                || !competencesParFormation.containsKey(formationId)) return "";

        List<Competence> competences = competencesParFormation.get(formationId);
        if (competences == null || competences.isEmpty()) return "";

        StringBuilder tags = new StringBuilder();
        for (Competence comp : competences) {
            if (comp.getNom() != null && !comp.getNom().isBlank()) {
                tags.append("<span class=\"cert-comp-tag\">")
                        .append("<span class=\"cert-comp-dot\"></span>")
                        .append(escapeHtml(comp.getNom()))
                        .append("</span>");
            }
        }
        if (tags.isEmpty()) return "";

        return "<div class=\"cert-competences\">"
                + "<div class=\"cert-comp-label\">Compétences acquises</div>"
                + "<div class=\"cert-comp-grid\">" + tags + "</div>"
                + "</div>";
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String safe(String val) {
        return val != null ? val : "";
    }

    private String getInitiales(String prenom, String nom) {
        String p = prenom.isBlank() ? "?" : String.valueOf(prenom.charAt(0)).toUpperCase();
        String n = nom.isBlank()    ? ""  : String.valueOf(nom.charAt(0)).toUpperCase();
        return p + n;
    }

    private String nvLabel(String niveau) {
        if (niveau == null) return "Apprenant";
        return switch (niveau.toUpperCase()) {
            case "DEBUTANT"      -> "Débutant";
            case "INTERMEDIAIRE" -> "Intermédiaire";
            case "AVANCE"        -> "Avancé";
            default              -> niveau;
        };
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}