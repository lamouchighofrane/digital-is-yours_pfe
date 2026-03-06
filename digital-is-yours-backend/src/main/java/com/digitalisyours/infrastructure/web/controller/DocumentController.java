package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.DocumentEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.DocumentJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/formateur/formations/{formationId}/cours/{coursId}/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class DocumentController {
    private final DocumentJpaRepository documentRepository;
    private final CoursJpaRepository coursRepository;
    private final FormationJpaRepository formationRepository;
    private final UserJpaRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    // Types de fichiers autorisés
    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "image/png", "image/jpeg", "image/gif", "image/webp"
    );

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    // ══ LISTER LES DOCUMENTS D'UN COURS ══════════════════════
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDocuments(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        CoursEntity cours = getCoursFromFormation(coursId, formationId);
        if (cours == null) return ResponseEntity.notFound().build();

        List<DocumentEntity> documents = documentRepository.findByCoursId(coursId);
        long totalTaille = documentRepository.sumTailleByCoursId(coursId);

        return ResponseEntity.ok(Map.of(
                "documents", documents.stream().map(this::toResponse).collect(Collectors.toList()),
                "total", documents.size(),
                "totalTaille", totalTaille
        ));
    }

    // ══ UPLOADER UN DOCUMENT ══════════════════════════════════
    @PostMapping("/upload")
    @Transactional
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "titre", required = false) String titre,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        CoursEntity cours = getCoursFromFormation(coursId, formationId);
        if (cours == null) return ResponseEntity.notFound().build();

        // Validations
        if (fichier.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide."));

        if (!ALLOWED_TYPES.contains(fichier.getContentType()))
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Format non supporté. Formats acceptés : PDF, Word, PowerPoint, Excel, images, texte"));

        if (fichier.getSize() > MAX_FILE_SIZE)
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("message", "Fichier trop volumineux. Maximum : 50 MB"));

        // Limiter à 10 documents par cours
        if (documentRepository.countByCoursId(coursId) >= 10)
            return ResponseEntity.badRequest().body(Map.of("message", "Maximum 10 documents par cours."));

        // Sauvegarder le fichier physiquement
        String extension  = getExtension(fichier.getOriginalFilename());
        String nomFichier = UUID.randomUUID().toString() + extension;
        String titreFinal = (titre != null && !titre.isBlank()) ? titre.trim() : getNameWithoutExtension(fichier.getOriginalFilename());

        try {
            Path uploadPath = Paths.get(uploadDir, "documents", coursId.toString());
            Files.createDirectories(uploadPath);
            Files.copy(fichier.getInputStream(), uploadPath.resolve(nomFichier), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Erreur upload document cours {}: {}", coursId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de l'enregistrement du fichier."));
        }

        // Enregistrer en base
        DocumentEntity doc = DocumentEntity.builder()
                .titre(titreFinal)
                .nomFichier(fichier.getOriginalFilename())
                .url(nomFichier)
                .typeFichier(fichier.getContentType())
                .taille(fichier.getSize())
                .cours(cours)
                .build();

        documentRepository.save(doc);
        log.info("Formateur {} a uploadé le document '{}' pour le cours {}", formateur.getEmail(), titreFinal, coursId);

        return ResponseEntity.ok(toResponse(doc));
    }

    // ══ MODIFIER LE TITRE D'UN DOCUMENT ══════════════════════
    @PutMapping("/{docId}")
    @Transactional
    public ResponseEntity<?> updateDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long docId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        DocumentEntity doc = documentRepository.findById(docId)
                .filter(d -> d.getCours().getId().equals(coursId))
                .orElse(null);
        if (doc == null) return ResponseEntity.notFound().build();

        String titre = body.get("titre");
        if (titre == null || titre.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Le titre est obligatoire"));

        doc.setTitre(titre.trim());
        documentRepository.save(doc);

        return ResponseEntity.ok(toResponse(doc));
    }

    // ══ SUPPRIMER UN DOCUMENT ═════════════════════════════════
    @DeleteMapping("/{docId}")
    @Transactional
    public ResponseEntity<?> deleteDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long docId,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        DocumentEntity doc = documentRepository.findById(docId)
                .filter(d -> d.getCours().getId().equals(coursId))
                .orElse(null);
        if (doc == null) return ResponseEntity.notFound().build();

        // Supprimer le fichier physique
        try {
            Path filePath = Paths.get(uploadDir, "documents", coursId.toString(), doc.getUrl());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier document {}: {}", doc.getUrl(), e.getMessage());
        }

        documentRepository.deleteById(docId);
        log.info("Formateur {} a supprimé le document '{}' du cours {}", formateur.getEmail(), doc.getTitre(), coursId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Document supprimé"));
    }

    // ══ TÉLÉCHARGER UN DOCUMENT ═══════════════════════════════
    @GetMapping("/{docId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @PathVariable Long docId,
            HttpServletRequest request) {

        DocumentEntity doc = documentRepository.findById(docId)
                .filter(d -> d.getCours().getId().equals(coursId))
                .orElse(null);
        if (doc == null) return ResponseEntity.notFound().build();

        try {
            Path filePath = Paths.get(uploadDir, "documents", coursId.toString(), doc.getUrl());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable())
                return ResponseEntity.notFound().build();

            String contentType = doc.getTypeFichier() != null ? doc.getTypeFichier() : "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + doc.getNomFichier() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ══ HELPERS PRIVÉS ════════════════════════════════════════

    private UserEntity getFormateurFromRequest(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            if (!jwtUtil.isValid(token)) return null;
            String email = jwtUtil.extractEmail(token);
            UserEntity user = userRepository.findByEmail(email).orElse(null);
            if (user == null || user.getRole() != Role.FORMATEUR) return null;
            return user;
        } catch (Exception e) {
            log.warn("Erreur JWT: {}", e.getMessage());
            return null;
        }
    }

    private FormationEntity getFormationAuthorisee(Long formationId, UserEntity formateur) {
        FormationEntity formation = formationRepository.findById(formationId).orElse(null);
        if (formation == null) return null;
        if (formation.getFormateur() == null || !formation.getFormateur().getId().equals(formateur.getId()))
            return null;
        return formation;
    }

    private CoursEntity getCoursFromFormation(Long coursId, Long formationId) {
        return coursRepository.findById(coursId)
                .filter(c -> c.getFormation().getId().equals(formationId))
                .orElse(null);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    private String getNameWithoutExtension(String filename) {
        if (filename == null) return "Document";
        int dot = filename.lastIndexOf(".");
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private Map<String, Object> toResponse(DocumentEntity d) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",          d.getId());
        map.put("titre",       d.getTitre());
        map.put("nomFichier",  d.getNomFichier());
        map.put("url",         d.getUrl());
        map.put("typeFichier", d.getTypeFichier());
        map.put("taille",      d.getTaille());
        map.put("dateAjout",   d.getDateAjout());
        if (d.getCours() != null) {
            map.put("coursId", d.getCours().getId());
        }
        return map;
    }
}
