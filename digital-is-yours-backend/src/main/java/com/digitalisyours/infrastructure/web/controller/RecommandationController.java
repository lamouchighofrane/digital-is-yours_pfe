package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.RecommandationIA;
import com.digitalisyours.domain.port.in.RecommandationUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apprenant/recommandations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class RecommandationController {
    private final RecommandationUseCase recommandationUseCase;
    private final JwtUtil jwtUtil;

    /**
     * GET /api/apprenant/recommandations
     * Retourne le Top 5 des formations recommandées pour l'apprenant connecté.
     * Résultat mis en cache 30 minutes.
     */
    @GetMapping
    public ResponseEntity<?> getRecommandations(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();

        try {
            List<RecommandationIA> recommandations = recommandationUseCase.getRecommandations(email);
            log.info("Recommandations retournées pour {} : {} formations", email, recommandations.size());
            return ResponseEntity.ok(recommandations);
        } catch (Exception e) {
            log.error("Erreur recommandations pour {} : {}", email, e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("message", "Impossible de calculer les recommandations."));
        }
    }

    /**
     * DELETE /api/apprenant/recommandations/cache
     * Invalide le cache pour forcer un recalcul au prochain appel.
     */
    @DeleteMapping("/cache")
    public ResponseEntity<?> invaliderCache(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();

        recommandationUseCase.invaliderCache(email);
        return ResponseEntity.ok(Map.of("message", "Cache invalidé, nouvelles recommandations au prochain chargement."));
    }

    // ── Helpers ───────────────────────────────────────────────

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
