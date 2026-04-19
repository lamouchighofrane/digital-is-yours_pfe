package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.application.service.PortfolioService;
import com.digitalisyours.domain.port.in.ProfilApprenantUseCase;
import com.digitalisyours.infrastructure.persistence.entity.PortfolioEntity;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class PortfolioController {

    private final PortfolioService       portfolioService;
    private final ProfilApprenantUseCase profilUseCase;
    private final JwtUtil                jwtUtil;

    /** GET /api/apprenant/portfolio — infos du portfolio de l'apprenant connecté */
    @GetMapping("/api/apprenant/portfolio")
    public ResponseEntity<?> getMonPortfolio(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Long apprenantId = profilUseCase.getProfil(email).getId();
            PortfolioEntity portfolio = portfolioService.getPortfolio(apprenantId);
            if (portfolio == null) {
                return ResponseEntity.ok(Map.of("existe", false));
            }
            return ResponseEntity.ok(Map.of(
                    "existe",            true,
                    "slug",              portfolio.getSlug(),
                    "urlGithubPages",    portfolio.getUrlGithubPages() != null
                            ? portfolio.getUrlGithubPages() : "",
                    "estPublie",         portfolio.getEstPublie(),
                    "nombreCertificats", portfolio.getNombreCertificats() != null
                            ? portfolio.getNombreCertificats() : 0,
                    "dateCreation",      portfolio.getDateCreation() != null
                            ? portfolio.getDateCreation().toString() : "",
                    "derniereMiseAJour", portfolio.getDerniereMiseAJour() != null
                            ? portfolio.getDerniereMiseAJour().toString() : ""
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** POST /api/apprenant/portfolio/regenerer — régénère depuis zéro (supprime + recrée) */
    @PostMapping("/api/apprenant/portfolio/regenerer")
    public ResponseEntity<?> regenererPortfolio(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Long apprenantId = profilUseCase.getProfil(email).getId();
            PortfolioEntity portfolio = portfolioService.regenerer(apprenantId);
            if (portfolio == null || !portfolio.getEstPublie()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Erreur lors de la publication sur GitHub Pages."
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "success",        true,
                    "urlGithubPages", portfolio.getUrlGithubPages(),
                    "slug",           portfolio.getSlug(),
                    "message",        "Portfolio régénéré et publié avec succès !"
            ));
        } catch (RuntimeException e) {
            log.error("Erreur régénération portfolio : {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /** GET /api/public/portfolio/{slug} — accès public */
    @GetMapping("/api/public/portfolio/{slug}")
    public ResponseEntity<?> getPortfolioPublic(@PathVariable String slug) {
        PortfolioEntity portfolio = portfolioService.getPortfolioParSlug(slug);
        if (portfolio == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Portfolio introuvable"));
        }
        return ResponseEntity.ok(Map.of(
                "slug",           portfolio.getSlug(),
                "urlGithubPages", portfolio.getUrlGithubPages() != null
                        ? portfolio.getUrlGithubPages() : "",
                "estPublie",      portfolio.getEstPublie()
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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