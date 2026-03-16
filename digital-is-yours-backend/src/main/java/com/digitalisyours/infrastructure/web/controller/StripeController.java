package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.application.service.InscriptionService;
import com.digitalisyours.application.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/apprenant")
@RequiredArgsConstructor
@Slf4j
public class StripeController {
    private final StripeService stripeService;
    private final InscriptionService inscriptionService;

    /**
     * Crée une Stripe Checkout Session et retourne l'URL de redirection
     */
    @PostMapping("/formations/{formationId}/stripe/checkout")
    public ResponseEntity<?> creerCheckoutSession(
            @PathVariable Long formationId,
            Authentication auth) {
        try {
            String email = auth.getName();
            String url = stripeService.createCheckoutSession(formationId, email);
            log.info("Stripe Checkout créée pour {} formation={}", email, formationId);
            return ResponseEntity.ok(Map.of("checkoutUrl", url));
        } catch (Exception e) {
            log.error("Erreur Stripe: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Appelé après retour de Stripe (succès) pour confirmer l'inscription en BD
     */
    @PostMapping("/formations/{formationId}/stripe/confirmer")
    public ResponseEntity<?> confirmerApresStripe(
            @PathVariable Long formationId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        try {
            String email = auth.getName();
            String sessionId = (String) body.get("sessionId");

            // Initier l'inscription si elle n'existe pas encore
            var inscription = inscriptionService.initierPaiement(email, formationId);

            // Confirmer avec référence Stripe
            inscription.setStatutPaiement("PAYE");
            inscription.setMethodePaiement("STRIPE");
            inscription.setReferencePaiement(sessionId != null ? sessionId.substring(0, Math.min(20, sessionId.length())) : "STRIPE-OK");
            inscription.setDatePaiement(java.time.LocalDateTime.now());

            // Sauvegarder via le service
            var result = inscriptionService.confirmerDepuisStripe(email, formationId, sessionId);

            log.info("Inscription confirmée via Stripe: {} formation={}", email, formationId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur confirmation Stripe: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
