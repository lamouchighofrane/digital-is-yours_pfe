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
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // ── Lister les questions de ses formations ────────────────────────────
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
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── Détail d'une question ─────────────────────────────────────────────
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

    // ── Stats sidebar formateur ───────────────────────────────────────────
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
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── Répondre à une question ───────────────────────────────────────────
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
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── Modifier SA réponse ───────────────────────────────────────────────
    @PutMapping("/reponses/{reponseId}")
    public ResponseEntity<?> modifierReponse(
            @PathVariable Long reponseId,
            @RequestBody  Map<String, Object> payload,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            String contenu = (String) payload.get("contenu");
            ReponsesForum r = reponseUseCase.modifierReponse(reponseId, email, contenu);
            return ResponseEntity.ok(Map.of("success", true, "reponse", r));
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── Marquer solution ──────────────────────────────────────────────────
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
            return ResponseEntity.status(403)
                    .body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401)
                .body(Map.of("message", "Non autorisé"));
    }
}