package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.AnalyseRisque;
import com.digitalisyours.domain.port.in.RisqueAbandonUseCase;
import com.digitalisyours.infrastructure.persistence.repository.ApprenantJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apprenant/risque")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class RisqueAbandonController {

    private final RisqueAbandonUseCase    risqueUseCase;
    private final ApprenantJpaRepository  apprenantRepo;
    private final JwtUtil                 jwtUtil;

    // ── GET toutes mes analyses ──────────────────────────────
    @GetMapping
    public ResponseEntity<?> getMesAnalyses(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            List<AnalyseRisque> analyses = risqueUseCase.getMesAnalyses(email);
            return ResponseEntity.ok(analyses);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── GET dernière analyse pour une formation ──────────────
    @GetMapping("/{formationId}")
    public ResponseEntity<?> getDerniereAnalyse(
            @PathVariable Long formationId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            AnalyseRisque a = risqueUseCase.getDerniereAnalyse(email, formationId);
            if (a == null)
                return ResponseEntity.ok(Map.of("existe", false));
            return ResponseEntity.ok(a);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── POST analyser manuellement une formation ─────────────
    @PostMapping("/{formationId}/analyser")
    public ResponseEntity<?> analyserMaintenant(
            @PathVariable Long formationId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Long apprenantId = apprenantRepo.findByEmail(email)
                    .map(a -> a.getId())
                    .orElseThrow(() -> new RuntimeException("Apprenant introuvable"));
            AnalyseRisque analyse =
                    risqueUseCase.analyserApprenant(apprenantId, formationId);
            return ResponseEntity.ok(analyse);
        } catch (Exception e) {
            log.error("Erreur analyse manuelle : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── POST déclencher analyse globale (test) ───────────────
    @PostMapping("/admin/lancer-analyse")
    public ResponseEntity<?> lancerAnalyseGlobale() {
        try {
            log.info("Analyse globale déclenchée manuellement");
            risqueUseCase.analyserTousLesApprenants();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Analyse globale terminée"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────
    private String extractEmail(HttpServletRequest req) {
        try {
            String auth = req.getHeader("Authorization");
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