package com.digitalisyours.application.service;

import com.digitalisyours.domain.port.out.FormationRepositoryPort;
import com.stripe.Stripe;

import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StripeService {
    private final FormationRepositoryPort formationRepository;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public String createCheckoutSession(Long formationId, String apprenantEmail) throws Exception {
        Stripe.apiKey = secretKey;

        var formation = formationRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        long montantCentimes = Math.round(formation.getPrix() * 100);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(apprenantEmail)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}&formation_id=" + formationId)
                .setCancelUrl(cancelUrl + "?formation_id=" + formationId)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd") // Stripe test ne supporte pas TND
                                                .setUnitAmount(montantCentimes)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(formation.getTitre())
                                                                .setDescription("Formation — Digital Is Yours")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .putMetadata("formation_id", String.valueOf(formationId))
                .putMetadata("apprenant_email", apprenantEmail)
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }
}
