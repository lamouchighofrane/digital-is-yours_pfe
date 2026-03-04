package com.digitalisyours.infrastructure.web.controller;

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

@RestController
@RequestMapping("/api/formateur/cours")
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class VideoStreamController {
    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    /**
     * Stream d'une vidéo locale.
     *
     * @param coursId  ID du cours (permet de construire le chemin : uploads/videos/cours/{coursId}/{filename})
     * @param filename Nom du fichier UUID (ex: "550e8400-e29b-41d4-a716-446655440000.mp4")
     */
    @GetMapping("/{coursId}/video/stream/{filename}")
    public ResponseEntity<Resource> streamVideo(
            @PathVariable Long coursId,
            @PathVariable String filename) {

        // Sécurité basique : le filename ne doit pas contenir de path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(uploadDir, "cours", coursId.toString(), filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Fichier vidéo introuvable : {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Détecter le content type selon l'extension
            String contentType = detectContentType(filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("URL malformée pour le fichier {}: {}", filename, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".avi"))  return "video/x-msvideo";
        if (lower.endsWith(".mov"))  return "video/quicktime";
        if (lower.endsWith(".mkv"))  return "video/x-matroska";
        return "video/mp4"; // défaut
    }

}
