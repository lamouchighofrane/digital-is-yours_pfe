package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.infrastructure.persistence.entity.ApprenantEntity;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.ProgressionCoursEntity;
import com.digitalisyours.infrastructure.persistence.repository.*;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/apprenant/formations/{formationId}/cours/{coursId}")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class TerminerCoursController {

    private final ProgressionCoursJpaRepository progressionRepository;
    private final InscriptionJpaRepository      inscriptionRepository;
    private final CoursJpaRepository            coursRepository;
    private final ApprenantJpaRepository        apprenantRepository;
    private final JwtUtil                       jwtUtil;
    private final ConsulterCoursFormationJpaRepository consulterCoursRepository;

    // ══════════════════════════════════════════════════════
    // 1. POST /marquer-video-vue
    //    Appelé quand l'apprenant clique "Marquer comme vu"
    // ══════════════════════════════════════════════════════
    @PostMapping("/marquer-video-vue")
    @Transactional
    public ResponseEntity<?> marquerVideoVue(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).build();
        if (!estInscrit(email, formationId)) return ResponseEntity.status(403).build();

        ProgressionCoursEntity p = getOrCreate(email, formationId, coursId);
        if (!p.isVideoVue()) {
            p.setVideoVue(true);
            p.recalculerStatut();
            progressionRepository.save(p);
            log.info("Vidéo du cours {} marquée comme vue par {}", coursId, email);
            mettreAJourStatutApprenant(email, formationId); // ← AJOUTER ICI
            if ("TERMINE".equals(p.getStatut())) {
                mettreAJourInscriptionProgression(email, formationId);
            }
        }

        return ResponseEntity.ok(Map.of(
                "videoVue",       p.isVideoVue(),
                "documentOuvert", p.isDocumentOuvert(),
                "quizPasse",      p.isQuizPasse(),
                "statut",         p.getStatut()
        ));
    }

    // ══════════════════════════════════════════════════════
    // 2. POST /marquer-document-ouvert
    //    Appelé quand l'apprenant ouvre un document
    // ══════════════════════════════════════════════════════
    @PostMapping("/marquer-document-ouvert")
    @Transactional
    public ResponseEntity<?> marquerDocumentOuvert(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).build();
        if (!estInscrit(email, formationId)) return ResponseEntity.status(403).build();

        ProgressionCoursEntity p = getOrCreate(email, formationId, coursId);
        if (!p.isDocumentOuvert()) {
            p.setDocumentOuvert(true);
            p.recalculerStatut();
            progressionRepository.save(p);
            log.info("Document du cours {} ouvert par {}", coursId, email);
            mettreAJourStatutApprenant(email, formationId); // ← AJOUTER ICI
            if ("TERMINE".equals(p.getStatut())) {
                mettreAJourInscriptionProgression(email, formationId);
            }
        }

        return ResponseEntity.ok(Map.of(
                "videoVue",       p.isVideoVue(),
                "documentOuvert", p.isDocumentOuvert(),
                "quizPasse",      p.isQuizPasse(),
                "statut",         p.getStatut()
        ));
    }

    // ══════════════════════════════════════════════════════
    // 3. POST /terminer-apres-quiz
    //    Appelé après soumission du mini-quiz
    // ══════════════════════════════════════════════════════
    @PostMapping("/terminer-apres-quiz")
    @Transactional
    public ResponseEntity<?> terminerApresQuiz(
            @PathVariable Long formationId,
            @PathVariable Long coursId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).build();
        if (!estInscrit(email, formationId)) return ResponseEntity.status(403).build();

        ProgressionCoursEntity p = getOrCreate(email, formationId, coursId);
        if (!p.isQuizPasse()) {
            p.setQuizPasse(true);
            p.recalculerStatut();
            progressionRepository.save(p);
            log.info("Quiz du cours {} passé par {}", coursId, email);
            mettreAJourStatutApprenant(email, formationId); // ← AJOUTER ICI
            if ("TERMINE".equals(p.getStatut())) {
                mettreAJourInscriptionProgression(email, formationId);
            }
        }

        return ResponseEntity.ok(Map.of(
                "videoVue",       p.isVideoVue(),
                "documentOuvert", p.isDocumentOuvert(),
                "quizPasse",      p.isQuizPasse(),
                "statut",         p.getStatut()
        ));
    }

    private void mettreAJourInscriptionProgression(String email, Long formationId) {
        apprenantRepository.findByEmail(email).ifPresent(apprenant -> {

            long coursTotal = coursRepository
                    .countPubliesByFormationId(formationId);

            List<Long> terminesIds = progressionRepository
                    .findCoursTerminesIds(email, formationId);

            inscriptionRepository.updateCoursProgression(
                    apprenant.getId(),
                    formationId,
                    terminesIds.size(),
                    (int) coursTotal
            );
        });
    }
    private void mettreAJourStatutApprenant(String email, Long formationId) {
        try {
            int total    = consulterCoursRepository
                    .findCoursPubiesByFormationId(formationId).size();
            int termines = progressionRepository.countCoursTermines(email, formationId);
            String nouveauStatut = (total > 0 && termines >= total)
                    ? "TERMINE" : "EN_COURS";
            inscriptionRepository.updateStatutApprenantByEmail(
                    email, formationId, nouveauStatut);
            log.info("Statut apprenant mis à {} : email={} formationId={}",
                    nouveauStatut, email, formationId);
        } catch (Exception e) {
            log.warn("Impossible de mettre à jour le statut apprenant : {}",
                    e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════

    private ProgressionCoursEntity getOrCreate(String email, Long formationId, Long coursId) {
        return progressionRepository.findByEmailAndCoursId(email, coursId)
                .orElseGet(() -> {
                    CoursEntity cours = coursRepository.findById(coursId)
                            .orElseThrow(() -> new RuntimeException("Cours introuvable"));
                    ApprenantEntity apprenant = apprenantRepository.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("Apprenant introuvable"));
                    return ProgressionCoursEntity.builder()
                            .apprenant(apprenant)
                            .cours(cours)
                            .formation(cours.getFormation())
                            .statut("A_FAIRE")
                            .build();
                });
    }

    private boolean estInscrit(String email, Long formationId) {
        return inscriptionRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(email, formationId, "PAYE");
    }

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }
}