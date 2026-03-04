package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/formateur/formations/{formationId}/cours")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CoursFormateurController {
    private final CoursJpaRepository coursRepository;
    private final FormationJpaRepository formationRepository;
    private final UserJpaRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // Formats vidéo acceptés
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
            "video/mp4", "video/avi", "video/quicktime",
            "video/x-msvideo", "video/webm", "video/x-matroska"
    );
    // Taille max : 1 GB
    private static final long MAX_FILE_SIZE = 1L * 1024 * 1024 * 1024;

    // ══ LISTER LES COURS ══════════════════════════════════════
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCours(
            @PathVariable Long formationId,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        FormationEntity formation = getFormationAuthorisee(formationId, formateur);
        if (formation == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit à cette formation"));

        List<CoursEntity> cours = coursRepository.findByFormationIdOrderByOrdre(formationId);
        List<Map<String, Object>> result = cours.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ══ CRÉER UN COURS ════════════════════════════════════════
    @PostMapping
    @Transactional
    public ResponseEntity<?> createCours(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        FormationEntity formation = getFormationAuthorisee(formationId, formateur);
        if (formation == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit à cette formation"));

        String titre = (String) payload.get("titre");
        if (titre == null || titre.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Le titre est obligatoire"));

        Integer maxOrdre = coursRepository.findMaxOrdreByFormationId(formationId);
        int nextOrdre = (maxOrdre != null ? maxOrdre : 0) + 1;

        CoursEntity cours = CoursEntity.builder()
                .titre(titre.trim())
                .description((String) payload.get("description"))
                .objectifs((String) payload.get("objectifs"))
                .dureeEstimee(getInt(payload, "dureeEstimee", 0))
                .ordre(payload.get("ordre") != null ? getInt(payload, "ordre", nextOrdre) : nextOrdre)
                .statut(payload.get("statut") != null ? (String) payload.get("statut") : "BROUILLON")
                .formation(formation)
                // videoType et videoUrl sont null à la création
                .videoType(null)
                .videoUrl(null)
                .build();

        coursRepository.save(cours);
        log.info("Formateur {} a créé le cours '{}' (id={})", formateur.getEmail(), cours.getTitre(), cours.getId());
        return ResponseEntity.ok(toResponse(cours));
    }

    // ══ MODIFIER UN COURS ════════════════════════════════════
    @PutMapping("/{coursId}")
    @Transactional
    public ResponseEntity<?> updateCours(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        FormationEntity formation = getFormationAuthorisee(formationId, formateur);
        if (formation == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        CoursEntity cours = getCoursFromFormation(coursId, formationId);
        if (cours == null) return ResponseEntity.notFound().build();

        String titre = (String) payload.get("titre");
        if (titre == null || titre.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Le titre est obligatoire"));

        cours.setTitre(titre.trim());
        cours.setDescription((String) payload.get("description"));
        cours.setObjectifs((String) payload.get("objectifs"));
        if (payload.get("dureeEstimee") != null) cours.setDureeEstimee(getInt(payload, "dureeEstimee", 0));
        if (payload.get("ordre") != null)        cours.setOrdre(getInt(payload, "ordre", cours.getOrdre()));
        if (payload.get("statut") != null)       cours.setStatut((String) payload.get("statut"));
        // NE PAS modifier videoType/videoUrl ici (endpoints dédiés)

        coursRepository.save(cours);
        return ResponseEntity.ok(toResponse(cours));
    }

    // ══ TOGGLE STATUT ═════════════════════════════════════════
    @PatchMapping("/{coursId}/toggle-statut")
    @Transactional
    public ResponseEntity<?> toggleStatut(
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

        String newStatut = "PUBLIE".equals(cours.getStatut()) ? "BROUILLON" : "PUBLIE";
        cours.setStatut(newStatut);
        coursRepository.save(cours);
        return ResponseEntity.ok(Map.of("success", true, "statut", newStatut));
    }

    // ══ SUPPRIMER UN COURS ════════════════════════════════════
    @DeleteMapping("/{coursId}")
    @Transactional
    public ResponseEntity<?> deleteCours(
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

        // Supprimer le fichier vidéo local si existant
        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        coursRepository.deleteById(coursId);
        log.info("Formateur {} a supprimé le cours '{}' (id={})", formateur.getEmail(), cours.getTitre(), coursId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Cours supprimé"));
    }

    // ══ RÉORDONNER ════════════════════════════════════════════
    @PatchMapping("/reordonner")
    @Transactional
    public ResponseEntity<?> reordonner(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ordres = (List<Map<String, Object>>) payload.get("ordres");
        if (ordres == null)
            return ResponseEntity.badRequest().body(Map.of("message", "Données manquantes"));

        for (Map<String, Object> item : ordres) {
            Long cid = Long.valueOf(item.get("id").toString());
            Integer ord = Integer.valueOf(item.get("ordre").toString());
            coursRepository.findById(cid).ifPresent(c -> { c.setOrdre(ord); coursRepository.save(c); });
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ══════════════════════════════════════════════════════════
    //  VIDÉO — selon diagramme : videoType (Enum Local/YouTube)
    //          et videoUrl (String) sont des attributs du Cours
    // ══════════════════════════════════════════════════════════

    /**
     * POST /formations/{formationId}/cours/{coursId}/video/youtube
     * Associe une vidéo YouTube au cours (met à jour videoType + videoUrl)
     */
    @PostMapping("/{coursId}/video/youtube")
    @Transactional
    public ResponseEntity<?> ajouterVideoYoutube(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        UserEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        if (getFormationAuthorisee(formationId, formateur) == null)
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit"));

        CoursEntity cours = getCoursFromFormation(coursId, formationId);
        if (cours == null) return ResponseEntity.notFound().build();

        String url = body.get("url");
        String videoId = extractYoutubeId(url);
        if (videoId == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "URL YouTube invalide. Formats acceptés : youtube.com/watch?v=, youtu.be/, /shorts/"));

        // Si une vidéo locale existait, on supprime le fichier
        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        String normalizedUrl = "https://www.youtube.com/watch?v=" + videoId;
        cours.setVideoType("YOUTUBE");
        cours.setVideoUrl(normalizedUrl);
        coursRepository.save(cours);

        log.info("Formateur {} a associé la vidéo YouTube {} au cours {}", formateur.getEmail(), normalizedUrl, coursId);
        return ResponseEntity.ok(toResponse(cours));
    }

    /**
     * POST /formations/{formationId}/cours/{coursId}/video/upload
     * Upload d'une vidéo locale — met à jour videoType=LOCAL et videoUrl=nomFichier
     */
    @PostMapping("/{coursId}/video/upload")
    @Transactional
    public ResponseEntity<?> uploadVideoLocale(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            @RequestParam("fichier") MultipartFile fichier,
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

        if (!ALLOWED_VIDEO_TYPES.contains(fichier.getContentType()))
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Format non supporté. Formats acceptés : MP4, AVI, MOV, WebM"));

        if (fichier.getSize() > MAX_FILE_SIZE)
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("message", "Fichier trop volumineux. Maximum : 1 GB"));

        // Supprimer l'ancienne vidéo locale si elle existe
        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        // Sauvegarder le fichier
        String extension = getExtension(fichier.getOriginalFilename());
        String nomFichier = UUID.randomUUID().toString() + extension;

        try {
            Path uploadPath = Paths.get(uploadDir, "cours", coursId.toString());
            Files.createDirectories(uploadPath);
            Files.copy(fichier.getInputStream(), uploadPath.resolve(nomFichier), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Erreur upload vidéo cours {}: {}", coursId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de l'enregistrement du fichier."));
        }

        // Mettre à jour les champs vidéo du cours
        cours.setVideoType("LOCAL");
        cours.setVideoUrl(nomFichier);
        coursRepository.save(cours);

        log.info("Formateur {} a uploadé la vidéo {} pour le cours {}", formateur.getEmail(), nomFichier, coursId);
        return ResponseEntity.ok(toResponse(cours));
    }

    /**
     * DELETE /formations/{formationId}/cours/{coursId}/video
     * Supprime la vidéo associée au cours (remet videoType et videoUrl à null)
     */
    @DeleteMapping("/{coursId}/video")
    @Transactional
    public ResponseEntity<?> supprimerVideo(
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

        // Supprimer le fichier physique si LOCAL
        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        cours.setVideoType(null);
        cours.setVideoUrl(null);
        coursRepository.save(cours);

        log.info("Formateur {} a supprimé la vidéo du cours {}", formateur.getEmail(), coursId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Vidéo supprimée"));
    }

    /**
     * GET /cours/{coursId}/video/stream/{filename}
     * Streaming de la vidéo locale (URL publique appelée par le lecteur vidéo)
     *
     * Note : ce mapping est à la racine /api/formateur/cours/{coursId}/...
     * Il faut ajouter un @RestController séparé OU le déclarer ici avec un path complet.
     * Voir VideoStreamController ci-dessous (fichier séparé recommandé).
     */

    // ══ HELPERS PRIVÉS ═════════════════════════════════════════

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

    private String extractYoutubeId(String url) {
        if (url == null || url.isBlank()) return null;
        Pattern pattern = Pattern.compile(
                "(?:youtube\\.com/(?:watch\\?v=|shorts/|embed/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
        );
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".mp4";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    private void supprimerFichierPhysique(Long coursId, String nomFichier) {
        try {
            Path filePath = Paths.get(uploadDir, "cours", coursId.toString(), nomFichier);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier vidéo {}: {}", nomFichier, e.getMessage());
        }
    }

    private Integer getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        return Integer.valueOf(val.toString());
    }

    /**
     * Sérialise CoursEntity → Map (réponse JSON)
     * Inclut videoType et videoUrl
     */
    private Map<String, Object> toResponse(CoursEntity c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",               c.getId());
        map.put("titre",            c.getTitre());
        map.put("description",      c.getDescription());
        map.put("objectifs",        c.getObjectifs());
        map.put("dureeEstimee",     c.getDureeEstimee());
        map.put("ordre",            c.getOrdre());
        map.put("statut",           c.getStatut());
        map.put("dateCreation",     c.getDateCreation());
        map.put("dateModification", c.getDateModification());

        // ── Vidéo ──
        map.put("videoType", c.getVideoType());   // "LOCAL" | "YOUTUBE" | null
        map.put("videoUrl",  c.getVideoUrl());    // URL YouTube ou nom fichier local

        if (c.getFormation() != null) {
            map.put("formationId",    c.getFormation().getId());
            map.put("formationTitre", c.getFormation().getTitre());
        }
        return map;
    }
}
