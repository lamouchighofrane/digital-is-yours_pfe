package com.digitalisyours.infrastructure.pdf;

import com.digitalisyours.domain.model.Certificat;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@Slf4j
public class CertificatPdfGenerator {

    // ── Palette couleurs (inspirée du logo JR + images de référence) ──
    private static final Color NAVY        = new Color(15,  30,  80);   // Bleu marine foncé
    private static final Color GOLD        = new Color(180, 140, 50);   // Or
    private static final Color TEAL        = new Color(74,  124, 126);  // Teal (identité DIY)
    private static final Color BORDEAUX    = new Color(139, 58,  58);   // Bordeaux (identité DIY)
    private static final Color LIGHT_BG    = new Color(248, 246, 240);  // Fond légèrement crème
    private static final Color DARK_TEXT   = new Color(20,  20,  50);   // Texte principal
    private static final Color GRAY_TEXT   = new Color(100, 100, 120);  // Texte secondaire
    private static final Color WHITE       = Color.WHITE;

    // ── Dimensions A4 paysage ──
    private static final float W = PDRectangle.A4.getHeight(); // 841.9
    private static final float H = PDRectangle.A4.getWidth();  // 595.3

    public byte[] generer(Certificat cert) throws IOException {

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {

                // 1. Fond blanc légèrement crème
                drawRect(cs, 0, 0, W, H, LIGHT_BG);

                // 2. Bordure extérieure bleu marine épaisse
                drawBorderRect(cs, 12, 12, W - 24, H - 24, NAVY, 4f);

                // 3. Bordure intérieure or fine
                drawBorderRect(cs, 20, 20, W - 40, H - 40, GOLD, 1.2f);

                // 4. Coins décoratifs géométriques (inspirés image 2)
                drawCornerDecorations(cs);

                // 5. Bande supérieure bleu marine
                drawRect(cs, 12, H - 90, W - 24, 78, NAVY);

                // 6. Accent or sous la bande
                drawRect(cs, 12, H - 94, W - 24, 4, GOLD);

                // 7. Logo PNG dans la bande supérieure
                drawLogo(doc, cs, cert);

                // 8. "CERTIFICAT OFFICIEL" dans la bande (droite)
                PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                setFontColor(cs, WHITE);
                cs.setFont(boldFont, 9);
                float badgeTxt = W - 190;
                cs.beginText();
                cs.newLineAtOffset(badgeTxt, H - 52);
                cs.showText("CERTIFICAT OFFICIEL");
                cs.endText();
                // Encadré autour
                drawBorderRect(cs, badgeTxt - 8, H - 64, 180, 20, GOLD, 1f);

                // 9. Titre principal "CERTIFICAT"
                PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                setFontColor(cs, NAVY);
                cs.setFont(titleFont, 52);
                String titre1 = "CERTIFICAT";
                float tw1 = titleFont.getStringWidth(titre1) / 1000 * 52;
                cs.beginText();
                cs.newLineAtOffset((W - tw1) / 2f, H - 160);
                cs.showText(titre1);
                cs.endText();

                // 10. Bandeau "DE REUSSITE" or (style image 2)
                float badgeW = 260f, badgeH2 = 28f;
                float badgeX = (W - badgeW) / 2f;
                float badgeY = H - 200;
                drawRect(cs, badgeX, badgeY, badgeW, badgeH2, GOLD);
                PDType1Font subTitleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                setFontColor(cs, WHITE);
                cs.setFont(subTitleFont, 13);
                String sub = "DE R\u00c9USSITE";
                float sw = subTitleFont.getStringWidth(sub) / 1000 * 13;
                cs.beginText();
                cs.newLineAtOffset((W - sw) / 2f, badgeY + 8);
                cs.showText(sub);
                cs.endText();

                // 11. "est décerné avec distinction à"
                PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
                setFontColor(cs, GRAY_TEXT);
                cs.setFont(regularFont, 13);
                String intro = "est d\u00e9cern\u00e9 avec distinction \u00e0";
                float iw = regularFont.getStringWidth(intro) / 1000 * 13;
                cs.beginText();
                cs.newLineAtOffset((W - iw) / 2f, H - 240);
                cs.showText(intro);
                cs.endText();

                // 12. NOM APPRENANT — grand et centré (style image 1 et 2)
                PDType1Font nameFont = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC);
                setFontColor(cs, DARK_TEXT);
                cs.setFont(nameFont, 42);
                String nom = cert.getApprenantPrenom() + " " + cert.getApprenantNom();
                float nw = nameFont.getStringWidth(nom) / 1000 * 42;
                float nameY = H - 290;
                cs.beginText();
                cs.newLineAtOffset((W - nw) / 2f, nameY);
                cs.showText(nom);
                cs.endText();

                // Ligne sous le nom (style image 2)
                cs.setStrokingColor(NAVY);
                cs.setLineWidth(1.5f);
                float lineX1 = (W - Math.min(nw + 60, W - 120)) / 2f;
                float lineX2 = lineX1 + Math.min(nw + 60, W - 120);
                cs.moveTo(lineX1, nameY - 8);
                cs.lineTo(lineX2, nameY - 8);
                cs.stroke();

                // 13. Texte formation
                PDType1Font medFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                setFontColor(cs, GRAY_TEXT);
                cs.setFont(medFont, 11);
                String ligne1 = "qui a compl\u00e9t\u00e9 avec succ\u00e8s la formation";
                float l1w = medFont.getStringWidth(ligne1) / 1000 * 11;
                cs.beginText();
                cs.newLineAtOffset((W - l1w) / 2f, H - 325);
                cs.showText(ligne1);
                cs.endText();

                // Nom formation en gras + teal
                PDType1Font boldMed = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                setFontColor(cs, TEAL);
                cs.setFont(boldMed, 13);
                String formation = cert.getFormationTitre();
                // Tronquer si trop long
                if (formation != null && formation.length() > 70)
                    formation = formation.substring(0, 67) + "...";
                if (formation != null) {
                    float fw = boldMed.getStringWidth(formation) / 1000 * 13;
                    cs.beginText();
                    cs.newLineAtOffset((W - fw) / 2f, H - 345);
                    cs.showText(formation);
                    cs.endText();
                }

                // Niveau et durée
                setFontColor(cs, GRAY_TEXT);
                cs.setFont(medFont, 10);
                String meta = "Niveau " + nvLabel(cert.getFormationNiveau())
                        + "   \u2022   Formation certifiante"
                        + (cert.getFormationDuree() != null ? "   \u2022   " + cert.getFormationDuree() + "h" : "");
                float mw = medFont.getStringWidth(meta) / 1000 * 10;
                cs.beginText();
                cs.newLineAtOffset((W - mw) / 2f, H - 362);
                cs.showText(meta);
                cs.endText();

                // 14. Ligne séparatrice centrale
                cs.setStrokingColor(new Color(220, 210, 190));
                cs.setLineWidth(0.8f);
                cs.moveTo(60, H - 378);
                cs.lineTo(W - 60, H - 378);
                cs.stroke();

                // 15. Zone note finale (gauche) + texte attestation (droite)
                float noteBoxX = 65, noteBoxY = H - 445, noteBoxW = 130, noteBoxH = 58;
                // Fond bleu marine pour la note
                drawRect(cs, noteBoxX, noteBoxY, noteBoxW, noteBoxH, NAVY);
                // Note en grand blanc
                PDType1Font bigBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                setFontColor(cs, WHITE);
                cs.setFont(bigBold, 28);
                String noteStr = String.format("%.0f%%", cert.getNoteFinal() != null ? cert.getNoteFinal() : 0f);
                float nSw = bigBold.getStringWidth(noteStr) / 1000 * 28;
                cs.beginText();
                cs.newLineAtOffset(noteBoxX + (noteBoxW - nSw) / 2f, noteBoxY + 30);
                cs.showText(noteStr);
                cs.endText();
                setFontColor(cs, GOLD);
                cs.setFont(medFont, 9);
                String noteLabel = "NOTE FINALE";
                float nlw = medFont.getStringWidth(noteLabel) / 1000 * 9;
                cs.beginText();
                cs.newLineAtOffset(noteBoxX + (noteBoxW - nlw) / 2f, noteBoxY + 13);
                cs.showText(noteLabel);
                cs.endText();

                // Texte attestation à droite de la note
                setFontColor(cs, GRAY_TEXT);
                cs.setFont(medFont, 10);
                float txtX = noteBoxX + noteBoxW + 20;
                float txtMaxW = W - txtX - 70;
                String[] lignesAttestation = wrapText(
                        "Ce certificat atteste que " + cert.getApprenantPrenom() + " "
                                + cert.getApprenantNom() + " a d\u00e9montr\u00e9 les comp\u00e9tences "
                                + "requises et a compl\u00e9t\u00e9 avec succ\u00e8s l'int\u00e9gralit\u00e9 "
                                + "du programme sur la plateforme Digital Is Yours.",
                        medFont, 10, txtMaxW);
                float lineHt = 15f;
                float startY = noteBoxY + noteBoxH - 5;
                for (String ligne : lignesAttestation) {
                    cs.beginText();
                    cs.setFont(medFont, 10);
                    cs.newLineAtOffset(txtX, startY);
                    cs.showText(ligne);
                    cs.endText();
                    startY -= lineHt;
                }

                // 16. Bande inférieure bleu marine
                float footH = 72f;
                drawRect(cs, 12, 16, W - 24, footH, NAVY);
                drawRect(cs, 12, footH + 16, W - 24, 3, GOLD);

                // Signature gauche
                setFontColor(cs, WHITE);
                cs.setFont(boldMed, 10);
                cs.beginText();
                cs.newLineAtOffset(60, 68);
                cs.showText("Directeur P\u00e9dagogique");
                cs.endText();
                cs.setFont(medFont, 9);
                cs.beginText();
                cs.newLineAtOffset(60, 54);
                cs.showText("Digital Is Yours | Acad\u00e9mie en ligne");
                cs.endText();
                // Ligne signature
                cs.setStrokingColor(GOLD);
                cs.setLineWidth(0.8f);
                cs.moveTo(60, 74);
                cs.lineTo(200, 74);
                cs.stroke();

                // Date de délivrance centre bas
                cs.setFont(medFont, 9);
                setFontColor(cs, new Color(180, 180, 200));
                String dateStr = "D\u00e9livr\u00e9 le " + (cert.getDateCreation() != null
                        ? cert.getDateCreation().format(
                        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
                        : "");
                float dw = medFont.getStringWidth(dateStr) / 1000 * 9;
                cs.beginText();
                cs.newLineAtOffset((W - dw) / 2f, 38);
                cs.showText(dateStr);
                cs.endText();

                // Numéro certificat droite bas
                cs.setFont(boldMed, 9);
                setFontColor(cs, GOLD);
                String numLabel = "N\u00b0 DE CERTIFICAT";
                float nLw = boldMed.getStringWidth(numLabel) / 1000 * 9;
                cs.beginText();
                cs.newLineAtOffset(W - nLw - 60, 68);
                cs.showText(numLabel);
                cs.endText();

                cs.setFont(boldMed, 10);
                setFontColor(cs, WHITE);
                String numVal = cert.getNumeroCertificat() != null ? cert.getNumeroCertificat() : "";
                float nvw = boldMed.getStringWidth(numVal) / 1000 * 10;
                cs.beginText();
                cs.newLineAtOffset(W - nvw - 60, 54);
                cs.showText(numVal);
                cs.endText();

                // Email contact
                cs.setFont(medFont, 8);
                setFontColor(cs, new Color(160, 160, 180));
                cs.beginText();
                cs.newLineAtOffset(W - 200, 26);
                cs.showText("contact.digisyours@gmail.com");
                cs.endText();

            } // fin PDPageContentStream

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // ══════════════════════════════════════════════════════
    // Coins décoratifs géométriques (style image 2)
    // ══════════════════════════════════════════════════════
    private void drawCornerDecorations(PDPageContentStream cs) throws IOException {
        float s = 50f; // taille coin
        float m = 20f; // marge (bord intérieur)

        // Coin haut-gauche — bleu marine
        cs.setNonStrokingColor(NAVY);
        // Triangle
        cs.moveTo(m, H - m);
        cs.lineTo(m + s, H - m);
        cs.lineTo(m, H - m - s);
        cs.fill();

        // Coin haut-droite — or
        cs.setNonStrokingColor(GOLD);
        cs.moveTo(W - m, H - m);
        cs.lineTo(W - m - s, H - m);
        cs.lineTo(W - m, H - m - s);
        cs.fill();

        // Coin bas-gauche — or
        cs.setNonStrokingColor(GOLD);
        cs.moveTo(m, m);
        cs.lineTo(m + s, m);
        cs.lineTo(m, m + s);
        cs.fill();

        // Coin bas-droite — bleu marine
        cs.setNonStrokingColor(NAVY);
        cs.moveTo(W - m, m);
        cs.lineTo(W - m - s, m);
        cs.lineTo(W - m, m + s);
        cs.fill();

        // Petits traits décoratifs côtés (style image 1 — chevrons)
        cs.setNonStrokingColor(new Color(180, 200, 230));
        float chevSize = 8f;
        // Haut : suite de chevrons
        for (int i = 0; i < 8; i++) {
            float cx = m + s + 10 + i * (chevSize + 4);
            cs.moveTo(cx, H - m);
            cs.lineTo(cx + chevSize / 2, H - m - chevSize / 2);
            cs.lineTo(cx + chevSize, H - m);
            cs.fill();
        }
        // Bas : suite de chevrons
        for (int i = 0; i < 8; i++) {
            float cx = W - m - s - 10 - i * (chevSize + 4) - chevSize;
            cs.moveTo(cx, m);
            cs.lineTo(cx + chevSize / 2, m + chevSize / 2);
            cs.lineTo(cx + chevSize, m);
            cs.fill();
        }
    }

    // ══════════════════════════════════════════════════════
    // Logo PNG (depuis resources)
    // ══════════════════════════════════════════════════════
    private void drawLogo(PDDocument doc, PDPageContentStream cs, Certificat cert)
            throws IOException {
        try {
            InputStream logoStream = getClass().getResourceAsStream("/static/logo.png");
            if (logoStream != null) {
                PDImageXObject logo = PDImageXObject.createFromByteArray(
                        doc, logoStream.readAllBytes(), "logo");
                float logoH = 55f;
                float logoW = logoH * ((float) logo.getWidth() / logo.getHeight());
                cs.drawImage(logo, 35, H - 82, logoW, logoH);
            } else {
                // Fallback texte si logo absent
                PDType1Font fb = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                cs.setFont(fb, 22);
                cs.setNonStrokingColor(TEAL);
                cs.beginText();
                cs.newLineAtOffset(38, H - 52);
                cs.showText("JR");
                cs.endText();
                cs.setNonStrokingColor(GOLD);
                cs.setFont(fb, 14);
                cs.beginText();
                cs.newLineAtOffset(68, H - 48);
                cs.showText("DIGITAL");
                cs.endText();
                PDType1Font fn = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                cs.setNonStrokingColor(new Color(200, 180, 150));
                cs.setFont(fn, 9);
                cs.beginText();
                cs.newLineAtOffset(68, H - 62);
                cs.showText("is yours");
                cs.endText();
            }
        } catch (Exception e) {
            log.warn("Logo non chargé : {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    // Helpers dessin
    // ══════════════════════════════════════════════════════

    private void drawRect(PDPageContentStream cs, float x, float y,
                          float w, float h, Color c) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void drawBorderRect(PDPageContentStream cs, float x, float y,
                                float w, float h, Color c, float lw) throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(lw);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private void setFontColor(PDPageContentStream cs, Color c) throws IOException {
        cs.setNonStrokingColor(c);
    }

    /** Découpe un texte long en lignes selon largeur max */
    private String[] wrapText(String text, PDType1Font font, float fontSize, float maxWidth)
            throws IOException {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();

        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            float tw = font.getStringWidth(test) / 1000 * fontSize;
            if (tw > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    private String nvLabel(String niveau) {
        if (niveau == null) return "";
        return switch (niveau.toUpperCase()) {
            case "DEBUTANT"      -> "D\u00e9butant";
            case "INTERMEDIAIRE" -> "Interm\u00e9diaire";
            case "AVANCE"        -> "Avanc\u00e9";
            default              -> niveau;
        };
    }
}