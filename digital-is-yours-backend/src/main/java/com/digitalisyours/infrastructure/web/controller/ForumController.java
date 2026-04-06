package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.QuestionForum;
import com.digitalisyours.domain.port.in.ForumUseCase;
import com.digitalisyours.domain.port.in.ReponseForumUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apprenant/forum")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class ForumController {

    private final ForumUseCase        forumUseCase;
    private final ReponseForumUseCase reponseUseCase;   // ← NOUVEAU : injecter ReponseForumUseCase
    private final JwtUtil             jwtUtil;

    // ════════════════════════════════════════════════════════════════
    // QUESTIONS (inchangé)
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
            PageRequest pageable = PageRequest.of(page, size,
                    Sort.by(Sort.Direction.DESC, "dateCreation"));

            Page<QuestionForum> result = forumUseCase.getQuestions(
                    search, formationId, statut, pageable, email);

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

    @PostMapping("/questions")
    public ResponseEntity<?> poserQuestion(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String titre   = (String) payload.get("titre");
            String contenu = (String) payload.get("contenu");
            Long   fid     = payload.get("formationId") != null
                    ? Long.valueOf(payload.get("formationId").toString()) : null;

            @SuppressWarnings("unchecked")
            List<String> tags = payload.get("tags") instanceof List<?>
                    ? (List<String>) payload.get("tags") : List.of();

            QuestionForum q = forumUseCase.poserQuestion(email, titre, contenu, fid, tags);
            log.info("Forum: question #{} postée par {}", q.getId(), email);
            return ResponseEntity.ok(Map.of("success", true, "question", q));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<?> modifierQuestion(
            @PathVariable Long id,
            @RequestBody  Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String       titre   = (String) payload.get("titre");
            String       contenu = (String) payload.get("contenu");
            @SuppressWarnings("unchecked")
            List<String> tags    = (List<String>) payload.get("tags");

            QuestionForum q = forumUseCase.modifierQuestion(id, email, titre, contenu, tags);
            return ResponseEntity.ok(Map.of("success", true, "question", q));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> supprimerQuestion(
            @PathVariable Long id,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            forumUseCase.supprimerQuestion(id, email);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/questions/{id}/like")
    public ResponseEntity<?> likerQuestion(
            @PathVariable Long id,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            QuestionForum q = forumUseCase.likerQuestion(id, email);
            return ResponseEntity.ok(Map.of(
                    "nombreLikes", q.getNombreLikes(),
                    "likeParMoi",  q.isLikeParMoi()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            return ResponseEntity.ok(Map.of(
                    "mesQuestions",          forumUseCase.countMesQuestions(email),
                    "mesQuestionsEnAttente", forumUseCase.countMesQuestionsEnAttente(email),
                    "questionsPopulaires",   forumUseCase.getQuestionsPopulaires(),
                    "contributeursActifs",   forumUseCase.getContributeursActifs()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : LIKE SUR RÉPONSE (apprenant)
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
    // NOUVEAU : RÉACTIONS EMOJI (apprenant)
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
    // NOUVEAU : IS TYPING — lecture côté apprenant
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/questions/{questionId}/typing")
    public ResponseEntity<?> getTyping(@PathVariable Long questionId) {
        boolean typing = reponseUseCase.isTyping(questionId);
        return ResponseEntity.ok(Map.of("isTyping", typing));
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
}