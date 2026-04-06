package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.QuestionForum;
import com.digitalisyours.domain.model.ReponsesForum;
import com.digitalisyours.domain.port.in.ForumUseCase;
import com.digitalisyours.domain.port.in.ReponseForumUseCase;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/formateur/forum")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class ForumFormateurController {

    private final ForumUseCase        forumUseCase;
    private final ReponseForumUseCase reponseUseCase;
    private final UserJpaRepository   userRepo;
    private final JwtUtil             jwtUtil;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    // ════════════════════════════════════════════════════════════════
    // LECTURE
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/questions")
    public ResponseEntity<?> getQuestions(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(defaultValue = "")  String formationId,
            @RequestParam(defaultValue = "")  String statut,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = "8") int    size,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            Long formateurId = userRepo.findByEmail(email)
                    .map(u -> u.getId())
                    .orElseThrow(() -> new RuntimeException("Formateur introuvable"));

            PageRequest pageable = PageRequest.of(
                    page, size, Sort.by(Sort.Direction.DESC, "dateCreation"));

            Page<QuestionForum> result = forumUseCase.getQuestionsFormateur(
                    formateurId, search, formationId, statut, pageable);

            return ResponseEntity.ok(Map.of(
                    "questions",   result.getContent(),
                    "total",       result.getTotalElements(),
                    "totalPages",  result.getTotalPages(),
                    "currentPage", result.getNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<?> getQuestion(
            @PathVariable Long id,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            QuestionForum q = forumUseCase.getQuestionById(id, email);
            return ResponseEntity.ok(q);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            Long formateurId = userRepo.findByEmail(email)
                    .map(u -> u.getId())
                    .orElseThrow();

            return ResponseEntity.ok(Map.of(
                    "questionsNonRepondues",
                    forumUseCase.countQuestionsNonRepondues(formateurId),
                    "questionsPopulaires",
                    forumUseCase.getQuestionsPopulaires(),
                    "contributeursActifs",
                    forumUseCase.getContributeursActifs()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // RÉPONDRE — texte simple
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/questions/{questionId}/reponses")
    public ResponseEntity<?> repondre(
            @PathVariable Long questionId,
            @RequestBody  Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String contenu = (String) payload.get("contenu");
            ReponsesForum r = reponseUseCase.repondre(questionId, email, contenu);
            return ResponseEntity.ok(Map.of("success", true, "reponse", r));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : RÉPONDRE AVEC FICHIER JOINT
    // ════════════════════════════════════════════════════════════════

    @PostMapping(value = "/questions/{questionId}/reponses/avec-fichier",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> repondreAvecFichier(
            @PathVariable Long questionId,
            @RequestParam("contenu") String contenu,
            @RequestParam(value = "fichier", required = false) MultipartFile fichier,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            ReponsesForum r = reponseUseCase.repondreAvecFichier(
                    questionId, email, contenu, fichier, uploadDir);
            return ResponseEntity.ok(Map.of("success", true, "reponse", r));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : TÉLÉCHARGER UN FICHIER JOINT D'UNE RÉPONSE
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/reponses/{reponseId}/documents/{nomFichier}/download")
    public ResponseEntity<Resource> downloadReponseDoc(
            @PathVariable Long   reponseId,
            @PathVariable String nomFichier,
            HttpServletRequest   request) {

        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).build();

        // Sécurité : bloquer path traversal
        if (nomFichier.contains("..") || nomFichier.contains("/") || nomFichier.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(uploadDir, "forum", String.valueOf(reponseId), nomFichier);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = detectContentType(nomFichier);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + nomFichier + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // MARQUER SOLUTION
    // ════════════════════════════════════════════════════════════════

    @PatchMapping("/questions/{questionId}/reponses/{reponseId}/solution")
    public ResponseEntity<?> marquerSolution(
            @PathVariable Long questionId,
            @PathVariable Long reponseId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            ReponsesForum r = reponseUseCase.marquerSolution(
                    questionId, reponseId, email);
            return ResponseEntity.ok(Map.of("success", true, "reponse", r));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : LIKE SUR RÉPONSE
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/reponses/{reponseId}/like")
    public ResponseEntity<?> likerReponse(
            @PathVariable Long reponseId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            Map<String, Object> result = reponseUseCase.toggleLikeReponse(reponseId, email);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : RÉACTIONS EMOJI
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/reponses/{reponseId}/reaction")
    public ResponseEntity<?> reagir(
            @PathVariable Long reponseId,
            @RequestBody  Map<String, String> body,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String emoji = body.get("emoji");
            if (emoji == null || emoji.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "L'emoji est requis."));
            }
            Map<String, Object> result = reponseUseCase.toggleReaction(reponseId, email, emoji);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : IS TYPING
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/questions/{questionId}/typing")
    public ResponseEntity<?> setTyping(
            @PathVariable Long questionId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        reponseUseCase.setTyping(questionId, email);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

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

    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}