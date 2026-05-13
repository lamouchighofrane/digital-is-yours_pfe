package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.repository.*;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apprenant/formations/{formationId}/progression")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class ProgressionFormationController {

    private final ProgressionCoursJpaRepository        progressionRepository;
    private final InscriptionJpaRepository             inscriptionRepository;
    private final ConsulterCoursFormationJpaRepository coursRepository;
    private final ApprenantJpaRepository               apprenantRepository;
    private final JwtUtil                              jwtUtil;

    @GetMapping
    public ResponseEntity<?> getProgression(
            @PathVariable Long formationId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).build();

        boolean inscrit = inscriptionRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(email, formationId, "PAYE");
        if (!inscrit) return ResponseEntity.status(403).build();

        int totalCours = coursRepository.findCoursPubiesByFormationId(formationId).size();
        if (totalCours == 0)
            return ResponseEntity.ok(Map.of("progression", 0.0, "totalCours", 0));

        int videosVues       = progressionRepository.countVideosVues(email, formationId);
        int documentsOuverts = progressionRepository.countDocumentsOuverts(email, formationId);
        int quizPasses       = progressionRepository.countQuizPasses(email, formationId);
        int coursTermines    = progressionRepository.countCoursTermines(email, formationId);

        double progression = ((double) videosVues       / totalCours * 50)
                + ((double) documentsOuverts / totalCours * 20)
                + ((double) quizPasses       / totalCours * 30);
        progression = Math.round(progression * 10.0) / 10.0;

        // ── NOUVEAU : mettre à jour progression dans inscriptions ──────────
        try {
            Long apprenantId = apprenantRepository.findByEmail(email)
                    .map(a -> a.getId())
                    .orElse(null);

            if (apprenantId != null) {
                // Déterminer le statut apprenant
                String nouveauStatut;
                if (progression >= 100) {
                    nouveauStatut = "TERMINE";
                } else if (progression > 0) {
                    nouveauStatut = "EN_COURS";
                } else {
                    nouveauStatut = "A_FAIRE";
                }

                inscriptionRepository.updateProgressionEtStatut(
                        apprenantId,
                        formationId,
                        (float) progression,
                        nouveauStatut
                );
            }
        } catch (Exception e) {
            log.warn("Mise à jour progression inscription non bloquante : {}", e.getMessage());
        }
        // ──────────────────────────────────────────────────────────────────

        List<CoursEntity> tousLesCours =
                coursRepository.findCoursPubiesByFormationId(formationId);

        List<com.digitalisyours.infrastructure.persistence.entity.ProgressionCoursEntity>
                progressionsExistantes =
                progressionRepository.findByEmailAndFormationId(email, formationId);

        Map<Long, com.digitalisyours.infrastructure.persistence.entity.ProgressionCoursEntity>
                progressionMap = progressionsExistantes.stream()
                .collect(Collectors.toMap(
                        p -> p.getCours().getId(),
                        p -> p
                ));

        List<Map<String, Object>> detailCours = tousLesCours.stream()
                .map(cours -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("coursId",    cours.getId());
                    m.put("coursTitre", cours.getTitre());

                    com.digitalisyours.infrastructure.persistence.entity.ProgressionCoursEntity p =
                            progressionMap.get(cours.getId());

                    if (p != null) {
                        m.put("statut",         p.getStatut());
                        m.put("videoVue",       p.isVideoVue());
                        m.put("documentOuvert", p.isDocumentOuvert());
                        m.put("quizPasse",      p.isQuizPasse());
                        m.put("dateDebut",      p.getDateDebut());
                        m.put("dateFin",        p.getDateFin());
                    } else {
                        m.put("statut",         "A_FAIRE");
                        m.put("videoVue",       false);
                        m.put("documentOuvert", false);
                        m.put("quizPasse",      false);
                        m.put("dateDebut",      null);
                        m.put("dateFin",        null);
                    }
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("formationId",      formationId);
        response.put("progression",      progression);
        response.put("totalCours",       totalCours);
        response.put("coursTermines",    coursTermines);
        response.put("videosVues",       videosVues);
        response.put("documentsOuverts", documentsOuverts);
        response.put("quizPasses",       quizPasses);
        response.put("detailCours",      detailCours);

        return ResponseEntity.ok(response);
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