package com.digitalisyours.infrastructure.ia;


import com.digitalisyours.infrastructure.persistence.entity.DocumentEntity;
import com.digitalisyours.infrastructure.persistence.repository.DocumentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoursContentExtractor {
    private final DocumentJpaRepository documentRepository;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ══════════════════════════════════════════════════════════
    // EXTRACTION TEXTE PDF
    // ══════════════════════════════════════════════════════════

    /**
     * Extrait le texte de tous les documents (PDF, Word .doc et .docx) associés au cours.
     * Limite chaque document à 3000 caractères pour ne pas surcharger le prompt Groq.
     */
    public String extrairePdfs(Long coursId) {
        List<DocumentEntity> documents = documentRepository.findByCoursId(coursId);
        if (documents.isEmpty()) return "";

        StringBuilder contenu = new StringBuilder();

        for (DocumentEntity doc : documents) {
            String type = doc.getTypeFichier();
            if (type == null) continue;

            // Accepter PDF et Word (.doc et .docx)
            boolean isPdf  = "application/pdf".equals(type);
            boolean isDocx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(type);
            boolean isDoc  = "application/msword".equals(type);

            if (!isPdf && !isDocx && !isDoc) continue;

            try {
                Path filePath = Paths.get(uploadDir, "documents", coursId.toString(), doc.getUrl());
                String texte = "";

                if (isPdf) {
                    // ── Extraction PDF ────────────────────────────
                    PDDocument pdDoc = Loader.loadPDF(filePath.toFile());
                    PDFTextStripper stripper = new PDFTextStripper();
                    texte = stripper.getText(pdDoc);
                    pdDoc.close();
                    log.info("PDF extrait : '{}'", doc.getTitre());

                } else if (isDocx) {
                    // ── Extraction Word .docx ─────────────────────
                    try (FileInputStream fis = new FileInputStream(filePath.toFile());
                         XWPFDocument xwpf = new XWPFDocument(fis);
                         XWPFWordExtractor extractor = new XWPFWordExtractor(xwpf)) {
                        texte = extractor.getText();
                    }
                    log.info("Word .docx extrait : '{}'", doc.getTitre());

                } else {
                    // ── Extraction Word .doc (ancien format) ──────
                    try (FileInputStream fis = new FileInputStream(filePath.toFile());
                         HWPFDocument hwpf = new HWPFDocument(fis);
                         WordExtractor extractor = new WordExtractor(hwpf)) {
                        texte = extractor.getText();
                    }
                    log.info("Word .doc extrait : '{}'", doc.getTitre());
                }

                // Nettoyage commun
                texte = texte.replaceAll("[ \t]+", " ")
                        .replaceAll("(\r?\n){3,}", "\n\n")
                        .trim();

                // Limiter à 3000 caractères par document
                if (texte.length() > 3000) {
                    texte = texte.substring(0, 3000) + "...";
                }

                if (!texte.isBlank()) {
                    contenu.append("--- Document : ").append(doc.getTitre()).append(" ---\n");
                    contenu.append(texte).append("\n\n");
                    log.info("Contenu extrait : '{}' — {} caractères", doc.getTitre(), texte.length());
                }

            } catch (Exception e) {
                log.warn("Impossible d'extraire le document '{}' (cours {}) : {}",
                        doc.getTitre(), coursId, e.getMessage());
            }
        }

        return contenu.toString().trim();
    }

    // ══════════════════════════════════════════════════════════
    // EXTRACTION TRANSCRIPTION YOUTUBE (sous-titres automatiques)
    // ══════════════════════════════════════════════════════════

    /**
     * Récupère la transcription automatique d'une vidéo YouTube via ses sous-titres.
     * Fonctionne gratuitement sans API key, via l'URL de sous-titres intégrée à la page YouTube.
     */
    public String extraireTranscriptionYoutube(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) return "";

        String videoId = extractYoutubeId(videoUrl);
        if (videoId == null) {
            log.warn("Impossible d'extraire l'ID YouTube depuis : {}", videoUrl);
            return "";
        }

        try {
            // Étape 1 : charger la page YouTube pour trouver l'URL des sous-titres
            String pageHtml = fetchYoutubePage(videoId);
            if (pageHtml == null) return "";

            // Étape 2 : extraire l'URL de caption depuis le JSON embarqué
            String captionUrl = extractCaptionUrl(pageHtml);
            if (captionUrl == null) {
                log.warn("Pas de sous-titres disponibles pour la vidéo YouTube : {}", videoId);
                return "";
            }

            // Étape 3 : récupérer et parser le XML des sous-titres
            String xmlContent = fetchCaptionXml(captionUrl);
            if (xmlContent == null || xmlContent.isBlank()) return "";

            String transcription = parseXmlCaptions(xmlContent);
            if (transcription.isBlank()) {
                log.warn("Transcription vide pour la vidéo YouTube : {}", videoId);
                return "";
            }

            // Limiter à 3000 caractères
            if (transcription.length() > 3000) {
                transcription = transcription.substring(0, 3000) + "...";
            }

            log.info("Transcription YouTube extraite : {} caractères pour la vidéo {}",
                    transcription.length(), videoId);
            return transcription;

        } catch (Exception e) {
            log.warn("Impossible d'extraire la transcription YouTube '{}' : {}", videoId, e.getMessage());
            return "";
        }
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════

    private String extractYoutubeId(String url) {
        Pattern pattern = Pattern.compile(
                "(?:youtube\\.com/(?:watch\\?v=|shorts/|embed/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
        );
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String fetchYoutubePage(String videoId) throws Exception {
        // Headers complets simulant un vrai navigateur Chrome pour éviter le blocage anti-bot
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.youtube.com/watch?v=" + videoId))
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Cache-Control", "max-age=0")
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("YouTube page status: {} | taille: {} chars", response.statusCode(), response.body().length());

        if (response.statusCode() != 200) {
            log.warn("Erreur HTTP {} lors du chargement de la page YouTube {}", response.statusCode(), videoId);
            return null;
        }

        String body = response.body();

        // Vérifier si YouTube a bloqué la requête (anti-bot)
        if (body.contains("unusual traffic") || body.contains("captcha") || body.length() < 50000) {
            log.warn("YouTube semble avoir bloqué la requête pour la vidéo {} (taille: {} chars)", videoId, body.length());
        }

        return body;
    }

    private String fetchCaptionXml(String captionUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(captionUrl))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : null;
    }

    private String extractCaptionUrl(String pageContent) {

        // Nettoyage des échappements JSON multiples avant la recherche
        String cleaned = pageContent
                .replace("\\u0026", "&")
                .replace("\\\\u0026", "&")
                .replace("\u0026", "&");

        // Pattern 1 : format standard captionTracks
        Pattern p1 = Pattern.compile(
                "\"captionTracks\":\\[\\{\"baseUrl\":\"(https://[^\"]+)\"",
                Pattern.DOTALL
        );
        Matcher m1 = p1.matcher(cleaned);
        if (m1.find()) {
            String url = m1.group(1).replace("\\/", "/");
            log.debug("Caption URL trouvée (pattern 1) : {}", url.substring(0, Math.min(80, url.length())));
            return url;
        }

        // Pattern 2 : baseUrl direct timedtext
        Pattern p2 = Pattern.compile(
                "\"baseUrl\":\"(https://www\\.youtube\\.com/api/timedtext[^\"]+)\""
        );
        Matcher m2 = p2.matcher(cleaned);
        if (m2.find()) {
            String url = m2.group(1).replace("\\/", "/");
            log.debug("Caption URL trouvée (pattern 2) : {}", url.substring(0, Math.min(80, url.length())));
            return url;
        }

        // Pattern 3 : chercher n'importe quelle URL timedtext dans la page
        Pattern p3 = Pattern.compile(
                "(https://www\\.youtube\\.com/api/timedtext\\?[^\\s\"]+)"
        );
        Matcher m3 = p3.matcher(cleaned);
        if (m3.find()) {
            log.debug("Caption URL trouvée (pattern 3)");
            return m3.group(1);
        }

        // Pattern 4 : format JSON avec playerCaptionsTracklistRenderer
        Pattern p4 = Pattern.compile(
                "playerCaptionsTracklistRenderer.*?\"baseUrl\":\"(https://[^\"]+timedtext[^\"]+)\"",
                Pattern.DOTALL
        );
        Matcher m4 = p4.matcher(cleaned);
        if (m4.find()) {
            String url = m4.group(1).replace("\\/", "/");
            log.debug("Caption URL trouvée (pattern 4)");
            return url;
        }

        log.warn("Aucun pattern de sous-titres trouvé. Taille page : {} chars", pageContent.length());
        return null;
    }

    private String parseXmlCaptions(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) return "";

        StringBuilder sb = new StringBuilder();
        Pattern pattern = Pattern.compile("<text[^>]*>([^<]+)</text>");
        Matcher matcher = pattern.matcher(xmlContent);

        while (matcher.find()) {
            String text = matcher.group(1)
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("\n", " ")
                    .trim();

            if (!text.isEmpty()) {
                sb.append(text).append(" ");
            }
        }

        return sb.toString().trim();
    }
}
