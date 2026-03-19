package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Document;
import com.digitalisyours.domain.port.in.SuivreCoursUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class SuivreCoursController {
    private final SuivreCoursUseCase suivreCoursUseCase;
    private final JwtUtil jwtUtil;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    // ════════════════════════════════════════════════════════
    // DOCUMENTS
    // ════════════════════════════════════════════════════════

    /**
     * Liste des documents d'un cours.
     * GET /api/apprenant/formations/{formationId}/cours/{coursId}/documents
     */
    @GetMapping("/api/apprenant/formations/{formationId}/cours/{coursId}/documents")
    public ResponseEntity<?> getDocuments(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            List<Document> documents = suivreCoursUseCase
                    .getDocumentsDuCours(email, formationId, coursId);

            return ResponseEntity.ok(Map.of(
                    "documents", documents,
                    "total",     documents.size()
            ));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Télécharger un document.
     * GET /api/apprenant/formations/{formationId}/cours/{coursId}/documents/{docId}/download
     *
     * Note : La vérification d'accès est faite via verifierAccesCours.
     * Le fichier est servi depuis le dossier uploads/documents/{coursId}/{uuid}.
     */
    @GetMapping("/api/apprenant/formations/{formationId}/cours/{coursId}/documents/{docId}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long docId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            // Vérification accès
            suivreCoursUseCase.verifierAccesCours(email, formationId, coursId);

            // Récupérer le document depuis la liste
            List<Document> documents = suivreCoursUseCase
                    .getDocumentsDuCours(email, formationId, coursId);

            Document doc = documents.stream()
                    .filter(d -> docId.equals(d.getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Document non trouvé"));

            Path filePath = Paths.get(uploadDir, "documents", coursId.toString(), doc.getUrl());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = doc.getTypeFichier() != null
                    ? doc.getTypeFichier() : "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + doc.getNomFichier() + "\"")
                    .body(resource);

        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ════════════════════════════════════════════════════════
    // STREAM VIDÉO LOCALE
    // ════════════════════════════════════════════════════════

    /**
     * Stream d'une vidéo locale pour l'apprenant.
     * GET /api/apprenant/cours/{coursId}/video/stream/{filename}?formationId={fid}
     *
     * Le paramètre formationId est obligatoire pour vérifier l'accès.
     * Sécurité : path traversal bloqué sur le filename.
     */
    @GetMapping("/api/apprenant/cours/{coursId}/video/stream/{filename}")
    public ResponseEntity<Resource> streamVideo(
            @PathVariable Long   coursId,
            @PathVariable String filename,
            @RequestParam Long   formationId,
            HttpServletRequest   request) {

        // Sécurité basique : bloquer path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("Tentative de path traversal bloquée : {}", filename);
            return ResponseEntity.badRequest().build();
        }

        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).build();

        try {
            // Vérification accès métier
            suivreCoursUseCase.verifierAccesCours(email, formationId, coursId);
        } catch (SecurityException e) {
            log.warn("Accès vidéo refusé pour {} cours={}", email, coursId);
            return ResponseEntity.status(403).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = Paths.get(uploadDir, "cours", coursId.toString(), filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Vidéo introuvable : {}", filePath);
                return ResponseEntity.notFound().build();
            }

            String contentType = detectContentType(filename);

            log.info("Stream vidéo : apprenant={} cours={} fichier={}", email, coursId, filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("URL malformée pour le fichier {} : {}", filename, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // ════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401)
                .body(Map.of("message", "Non autorisé"));
    }

    private ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(403)
                .body(Map.of("message", message));
    }

    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".avi"))  return "video/x-msvideo";
        if (lower.endsWith(".mov"))  return "video/quicktime";
        if (lower.endsWith(".mkv"))  return "video/x-matroska";
        return "video/mp4";
    }
}
