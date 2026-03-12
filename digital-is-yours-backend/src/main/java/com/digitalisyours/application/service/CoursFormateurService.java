package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Cours;
import com.digitalisyours.domain.port.in.CoursFormateurUseCase;
import com.digitalisyours.domain.port.out.CoursRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoursFormateurService implements CoursFormateurUseCase {
    private final CoursRepositoryPort coursRepository;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
            "video/mp4", "video/avi", "video/quicktime",
            "video/x-msvideo", "video/webm", "video/x-matroska"
    );
    private static final long MAX_FILE_SIZE = 1L * 1024 * 1024 * 1024;

    @Override
    public List<Cours> getCours(Long formationId, String email) {
        checkAcces(formationId, email);
        return coursRepository.findByFormationIdOrderByOrdre(formationId);
    }

    @Override
    public Cours createCours(Long formationId, String email, Map<String, Object> payload) {
        checkAcces(formationId, email);

        String titre = (String) payload.get("titre");
        if (titre == null || titre.isBlank())
            throw new RuntimeException("Le titre est obligatoire");

        Integer maxOrdre = coursRepository.findMaxOrdreByFormationId(formationId);
        int nextOrdre = (maxOrdre != null ? maxOrdre : 0) + 1;

        Cours cours = Cours.builder()
                .titre(titre.trim())
                .description((String) payload.get("description"))
                .objectifs((String) payload.get("objectifs"))
                .dureeEstimee(getInt(payload, "dureeEstimee", 0))
                .ordre(payload.get("ordre") != null ? getInt(payload, "ordre", nextOrdre) : nextOrdre)
                .statut(payload.get("statut") != null ? (String) payload.get("statut") : "BROUILLON")
                .formationId(formationId)
                .videoType(null)
                .videoUrl(null)
                .build();

        return coursRepository.save(cours);
    }

    @Override
    public Cours updateCours(Long formationId, Long coursId, String email, Map<String, Object> payload) {
        checkAcces(formationId, email);

        Cours cours = coursRepository.findByIdAndFormationId(coursId, formationId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        String titre = (String) payload.get("titre");
        if (titre == null || titre.isBlank())
            throw new RuntimeException("Le titre est obligatoire");

        cours.setTitre(titre.trim());
        cours.setDescription((String) payload.get("description"));
        cours.setObjectifs((String) payload.get("objectifs"));
        if (payload.get("dureeEstimee") != null) cours.setDureeEstimee(getInt(payload, "dureeEstimee", 0));
        if (payload.get("ordre") != null)         cours.setOrdre(getInt(payload, "ordre", cours.getOrdre()));
        if (payload.get("statut") != null)        cours.setStatut((String) payload.get("statut"));

        return coursRepository.save(cours);
    }

    @Override
    public Cours toggleStatut(Long formationId, Long coursId, String email) {
        checkAcces(formationId, email);

        Cours cours = coursRepository.findByIdAndFormationId(coursId, formationId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        cours.setStatut("PUBLIE".equals(cours.getStatut()) ? "BROUILLON" : "PUBLIE");
        return coursRepository.save(cours);
    }

    @Override
    public void deleteCours(Long formationId, Long coursId, String email) {
        checkAcces(formationId, email);

        Cours cours = coursRepository.findByIdAndFormationId(coursId, formationId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        coursRepository.deleteById(coursId);
    }

    @Override
    public void reordonner(Long formationId, String email, List<Map<String, Object>> ordres) {
        checkAcces(formationId, email);

        for (Map<String, Object> item : ordres) {
            Long cid = Long.valueOf(item.get("id").toString());
            Integer ord = Integer.valueOf(item.get("ordre").toString());
            coursRepository.findById(cid).ifPresent(c -> {
                c.setOrdre(ord);
                coursRepository.save(c);
            });
        }
    }

    @Override
    public Cours ajouterVideoYoutube(Long formationId, Long coursId, String email, String url) {
        checkAcces(formationId, email);

        Cours cours = coursRepository.findByIdAndFormationId(coursId, formationId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        String videoId = extractYoutubeId(url);
        if (videoId == null)
            throw new RuntimeException("URL YouTube invalide. Formats acceptés : youtube.com/watch?v=, youtu.be/, /shorts/");

        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        cours.setVideoType("YOUTUBE");
        cours.setVideoUrl("https://www.youtube.com/watch?v=" + videoId);
        return coursRepository.save(cours);
    }

    @Override
    public Cours uploadVideoLocale(Long formationId, Long coursId, String email,
                                   String originalFilename, String contentType,
                                   long fileSize, byte[] fileBytes) {
        checkAcces(formationId, email);

        Cours cours = coursRepository.findByIdAndFormationId(coursId, formationId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (fileBytes == null || fileBytes.length == 0)
            throw new RuntimeException("Le fichier est vide.");
        if (!ALLOWED_VIDEO_TYPES.contains(contentType))
            throw new RuntimeException("Format non supporté. Formats acceptés : MP4, AVI, MOV, WebM");
        if (fileSize > MAX_FILE_SIZE)
            throw new RuntimeException("Fichier trop volumineux. Maximum : 1 GB");

        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        String extension = getExtension(originalFilename);
        String nomFichier = UUID.randomUUID().toString() + extension;

        try {
            Path uploadPath = Paths.get(uploadDir, "cours", coursId.toString());
            Files.createDirectories(uploadPath);
            Files.write(uploadPath.resolve(nomFichier), fileBytes);
        } catch (IOException e) {
            log.error("Erreur upload vidéo cours {}: {}", coursId, e.getMessage());
            throw new RuntimeException("Erreur lors de l'enregistrement du fichier.");
        }

        cours.setVideoType("LOCAL");
        cours.setVideoUrl(nomFichier);
        return coursRepository.save(cours);
    }

    @Override
    public Cours supprimerVideo(Long formationId, Long coursId, String email) {
        checkAcces(formationId, email);

        Cours cours = coursRepository.findByIdAndFormationId(coursId, formationId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if ("LOCAL".equals(cours.getVideoType()) && cours.getVideoUrl() != null) {
            supprimerFichierPhysique(coursId, cours.getVideoUrl());
        }

        cours.setVideoType(null);
        cours.setVideoUrl(null);
        return coursRepository.save(cours);
    }

    // ── Helpers ───────────────────────────────────────────────

    private void checkAcces(Long formationId, String email) {
        if (!coursRepository.isFormateurOfFormation(formationId, email))
            throw new SecurityException("Accès interdit à cette formation");
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
}
