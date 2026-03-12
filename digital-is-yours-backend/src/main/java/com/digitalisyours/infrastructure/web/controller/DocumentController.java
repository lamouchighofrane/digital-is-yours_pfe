package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Document;

import com.digitalisyours.domain.port.in.DocumentUseCase;

import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Map;


@RestController
@RequestMapping("/api/formateur/formations/{formationId}/cours/{coursId}/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class DocumentController {
    private final DocumentUseCase documentUseCase;
    private final JwtUtil jwtUtil;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    @GetMapping
    public ResponseEntity<?> getDocuments(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(documentUseCase.getDocuments(formationId, coursId, email));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "titre", required = false) String titre,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Document doc = documentUseCase.uploadDocument(
                    formationId, coursId, email,
                    fichier.getOriginalFilename(),
                    fichier.getContentType(),
                    fichier.getSize(),
                    fichier.getBytes(),
                    titre
            );
            log.info("Formateur {} a uploadé le document '{}' pour le cours {}", email, doc.getTitre(), coursId);
            return ResponseEntity.ok(doc);
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lecture fichier"));
        }
    }

    @PutMapping("/{docId}")
    public ResponseEntity<?> updateDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long docId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(documentUseCase.updateDocument(
                    formationId, coursId, docId, email, body.get("titre")));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long docId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            documentUseCase.deleteDocument(formationId, coursId, docId, email);
            log.info("Formateur {} a supprimé le document id={} du cours {}", email, docId, coursId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Document supprimé"));
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{docId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long docId,
            HttpServletRequest request) {
        try {
            Document doc = documentUseCase.getDocumentForDownload(coursId, docId);
            Path filePath = Paths.get(uploadDir, "documents", coursId.toString(), doc.getUrl());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable())
                return ResponseEntity.notFound().build();

            String contentType = doc.getTypeFichier() != null
                    ? doc.getTypeFichier() : "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + doc.getNomFichier() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
    }

    private ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(403).body(Map.of("message", message));
    }
}
