package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Inscription;
import com.digitalisyours.domain.port.in.InscriptionUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apprenant/formations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class InscriptionController {
    private final InscriptionUseCase inscriptionUseCase;
    private final JwtUtil jwtUtil;

    /**
     * GET /api/apprenant/formations/mes-inscriptions
     * Retourne toutes les formations payées de l'apprenant connecté
     */
    @GetMapping("/mes-inscriptions")
    public ResponseEntity<?> getMesInscriptions(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            List<Map<String, Object>> result = inscriptionUseCase.getMesInscriptions(email)
                    .stream()
                    .map(i -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("id",              i.getId());
                        m.put("formationId",     i.getFormationId());          // ← AJOUT CRUCIAL
                        m.put("titre",           i.getFormationTitre()       != null ? i.getFormationTitre()       : "");
                        m.put("description",     i.getFormationDescription() != null ? i.getFormationDescription() : "");
                        m.put("imageCouverture", i.getFormationImage()        != null ? i.getFormationImage()       : "");
                        m.put("niveau",          i.getFormationNiveau()       != null ? i.getFormationNiveau()      : "DEBUTANT");
                        m.put("progression",     i.getProgression()           != null ? i.getProgression()          : 0);
                        m.put("statutPaiement",  i.getStatutPaiement()        != null ? i.getStatutPaiement()       : "");
                        m.put("dateInscription", i.getDateInscription()       != null ? i.getDateInscription().toString() : "");
                        return m;
                    })
                    .toList();
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * GET /api/apprenant/formations/{formationId}/statut-inscription
     * Vérifie si l'apprenant est inscrit à une formation
     */
    @GetMapping("/{formationId}/statut-inscription")
    public ResponseEntity<?> getStatutInscription(
            @PathVariable Long formationId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        boolean inscrit = inscriptionUseCase.estInscrit(email, formationId);
        return ResponseEntity.ok(Map.of("inscrit", inscrit));
    }

    /**
     * POST /api/apprenant/formations/{formationId}/paiement/initier
     * Crée une inscription EN_ATTENTE et retourne les infos de la formation (prix, etc.)
     */
    @PostMapping("/{formationId}/paiement/initier")
    public ResponseEntity<?> initierPaiement(
            @PathVariable Long formationId,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Inscription inscription = inscriptionUseCase.initierPaiement(email, formationId);
            log.info("Paiement initié : apprenant={} formation={}", email, formationId);
            return ResponseEntity.ok(Map.of(
                    "success",       true,
                    "inscriptionId", inscription.getId(),
                    "formationTitre",inscription.getFormationTitre(),
                    "montant",       inscription.getMontantPaye(),
                    "statut",        inscription.getStatutPaiement()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/apprenant/formations/{formationId}/paiement/confirmer
     * Confirme le paiement avec les données carte simulées
     * Body: { numeroCarte, nomCarte, expiration, cvv }
     */
    @PostMapping("/{formationId}/paiement/confirmer")
    public ResponseEntity<?> confirmerPaiement(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> payloadCarte,
            HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Inscription inscription = inscriptionUseCase.confirmerPaiement(
                    email, formationId, payloadCarte);
            log.info("Paiement confirmé : apprenant={} formation={} ref={}",
                    email, formationId, inscription.getReferencePaiement());
            return ResponseEntity.ok(Map.of(
                    "success",    true,
                    "message",    "Paiement accepté ! Vous êtes maintenant inscrit.",
                    "reference",  inscription.getReferencePaiement(),
                    "montant",    inscription.getMontantPaye(),
                    "datePaiement", inscription.getDatePaiement().toString()
            ));
        } catch (RuntimeException e) {
            log.warn("Échec paiement : apprenant={} formation={} raison={}",
                    email, formationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

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
