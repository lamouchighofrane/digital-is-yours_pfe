package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.QuestionForum;
import com.digitalisyours.domain.port.in.ForumUseCase;
import com.digitalisyours.infrastructure.persistence.repository.*;
import com.digitalisyours.infrastructure.persistence.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/forum")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class AdminForumController {

    private final ForumUseCase forumUseCase;
    private final QuestionForumJpaRepository questionRepo;
    private final ReponsesForumJpaRepository reponseRepo;

    @GetMapping("/questions")
    public ResponseEntity<?> getAllQuestions(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String formationId,
            @RequestParam(defaultValue = "") String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            PageRequest pageable = PageRequest.of(page, size,
                    Sort.by(Sort.Direction.DESC, "dateCreation"));
            Page<QuestionForum> result = forumUseCase.getQuestions(
                    search.isBlank() ? null : search,
                    formationId.isBlank() ? null : formationId,
                    statut.isBlank() ? null : statut,
                    pageable, null);
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
    public ResponseEntity<?> getQuestion(@PathVariable Long id) {
        try {
            QuestionForum q = forumUseCase.getQuestionById(id, null);
            return ResponseEntity.ok(q);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long id) {
        try {
            QuestionForumEntity q = questionRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Question introuvable"));
            questionRepo.delete(q);
            log.info("Admin a supprimé la question forum #{}", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Question supprimée"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/reponses/{id}")
    public ResponseEntity<?> deleteReponse(@PathVariable Long id) {
        try {
            ReponsesForumEntity r = reponseRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Réponse introuvable"));
            Long questionId = r.getQuestion().getId();
            reponseRepo.delete(r);
            // Mettre à jour le statut de la question si plus de réponses
            long nbReponses = reponseRepo.countByQuestionId(questionId);
            if (nbReponses == 0) {
                questionRepo.findById(questionId).ifPresent(q -> {
                    q.setStatut("NON_REPONDU");
                    questionRepo.save(q);
                });
            }
            log.info("Admin a supprimé la réponse forum #{}", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Réponse supprimée"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            long totalQuestions = questionRepo.count();
            long nonRepondues = questionRepo.findWithFilters(null, null, "NON_REPONDU",
                    PageRequest.of(0, 1)).getTotalElements();
            long repondues = questionRepo.findWithFilters(null, null, "REPONDU",
                    PageRequest.of(0, 1)).getTotalElements();
            long resolues = questionRepo.findWithFilters(null, null, "RESOLU",
                    PageRequest.of(0, 1)).getTotalElements();
            long totalReponses = reponseRepo.count();
            return ResponseEntity.ok(Map.of(
                    "totalQuestions", totalQuestions,
                    "nonRepondues",   nonRepondues,
                    "repondues",      repondues,
                    "resolues",       resolues,
                    "totalReponses",  totalReponses
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}