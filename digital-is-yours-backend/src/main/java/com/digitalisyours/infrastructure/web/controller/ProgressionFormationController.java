package com.digitalisyours.infrastructure.web.controller;
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

    private final ProgressionCoursJpaRepository   progressionRepository;
    private final InscriptionJpaRepository        inscriptionRepository;
    private final ConsulterCoursFormationJpaRepository coursRepository;
    private final JwtUtil                         jwtUtil;

    /**
     * GET /api/apprenant/formations/{formationId}/progression
     *
     * Retourne la progression calculée selon la formule :
     * Progression = ((vidéos vues / total) × 50)
     *             + ((documents ouverts / total) × 20)
     *             + ((mini-quiz passés / total) × 30)
     */
    @GetMapping
    public ResponseEntity<?> getProgression(
            @PathVariable Long formationId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).build();

        boolean inscrit = inscriptionRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(email, formationId, "PAYE");
        if (!inscrit) return ResponseEntity.status(403).build();

        // Total cours publiés dans la formation
        int totalCours = coursRepository.findCoursPubiesByFormationId(formationId).size();
        if (totalCours == 0) return ResponseEntity.ok(Map.of("progression", 0.0, "totalCours", 0));

        // Compteurs
        int videosVues       = progressionRepository.countVideosVues(email, formationId);
        int documentsOuverts = progressionRepository.countDocumentsOuverts(email, formationId);
        int quizPasses       = progressionRepository.countQuizPasses(email, formationId);
        int coursTermines    = progressionRepository.countCoursTermines(email, formationId);

        // Formule :
        // ((vidéos vues / total) × 50) + ((documents ouverts / total) × 20) + ((quiz passés / total) × 30)
        double progression = ((double) videosVues       / totalCours * 50)
                + ((double) documentsOuverts / totalCours * 20)
                + ((double) quizPasses       / totalCours * 30);

        // Arrondir à 1 décimale
        progression = Math.round(progression * 10.0) / 10.0;

        // Détail par cours
        List<Map<String, Object>> detailCours = progressionRepository
                .findByEmailAndFormationId(email, formationId)
                .stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("coursId",        p.getCours().getId());
                    m.put("coursTitre",     p.getCours().getTitre());
                    m.put("statut",         p.getStatut());
                    m.put("videoVue",       p.isVideoVue());
                    m.put("documentOuvert", p.isDocumentOuvert());
                    m.put("quizPasse",      p.isQuizPasse());
                    m.put("dateDebut",      p.getDateDebut());
                    m.put("dateFin",        p.getDateFin());
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
