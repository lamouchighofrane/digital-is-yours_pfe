package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.QuizJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apprenant/formations/{formationId}")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CoursApprenantController {
    private final CoursJpaRepository coursJpaRepository;
    private final InscriptionJpaRepository inscriptionJpaRepository;
    private final QuizJpaRepository quizJpaRepository;
    private final JwtUtil jwtUtil;

    // ── GET liste des cours d'une formation (apprenant inscrit) ──
    @GetMapping("/cours")
    public ResponseEntity<?> getCours(
            @PathVariable Long formationId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return unauthorized();

        // Vérifier que l'apprenant est inscrit et a payé
        boolean inscrit = inscriptionJpaRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(email, formationId, "PAYE");

        if (!inscrit) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Vous n'êtes pas inscrit à cette formation."));
        }

        // Retourner les cours PUBLIÉS uniquement, triés par ordre
        List<CoursEntity> cours = coursJpaRepository
                .findByFormationIdOrderByOrdre(formationId)
                .stream()
                .filter(c -> "PUBLIE".equals(c.getStatut()))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = cours.stream()
                .map(c -> toMap(c))
                .collect(Collectors.toList());
        // ── Infos Quiz Final ──
        Map<String, Object> quizInfo = new LinkedHashMap<>();
        try {
            // Chercher le quiz final de cette formation
            List<QuizEntity> quizList = quizJpaRepository.findAll().stream()
                    .filter(q -> "QuizFinal".equals(q.getType())
                            && q.getFormation() != null
                            && formationId.equals(q.getFormation().getId()))
                    .toList();

            if (!quizList.isEmpty()) {
                QuizEntity quiz = quizList.get(0);
                quizInfo.put("existe",          true);
                quizInfo.put("notePassage",      quiz.getNotePassage()      != null ? quiz.getNotePassage()      : 70f);
                quizInfo.put("dureeMinutes",     quiz.getDureeMinutes()     != null ? quiz.getDureeMinutes()     : 45);
                quizInfo.put("nombreTentatives", quiz.getNombreTentatives() != null ? quiz.getNombreTentatives() : 3);
            } else {
                quizInfo.put("existe",      false);
                quizInfo.put("notePassage", 75f);
            }
        } catch (Exception e) {
            quizInfo.put("existe",      false);
            quizInfo.put("notePassage", 75f);
        }

        return ResponseEntity.ok(Map.of(
                "formationId", formationId,
                "total",       cours.size(),
                "cours",       result,
                "quiz",        quizInfo
        ));
    }

    // ── Helpers ───────────────────────────────────────────────

    private Map<String, Object> toMap(CoursEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           c.getId());
        m.put("titre",        c.getTitre());
        m.put("description",  c.getDescription());
        m.put("objectifs",    c.getObjectifs());
        m.put("dureeEstimee", c.getDureeEstimee());
        m.put("ordre",        c.getOrdre());
        m.put("statut",       c.getStatut());
        m.put("videoType",    c.getVideoType());
        // Ne pas exposer l'URL directe de la vidéo locale (sécurité)
        // Pour YouTube, on peut donner l'URL publique
        m.put("videoUrl",     "YOUTUBE".equals(c.getVideoType()) ? c.getVideoUrl() : null);
        m.put("hasVideo",     c.getVideoType() != null);
        return m;
    }

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
