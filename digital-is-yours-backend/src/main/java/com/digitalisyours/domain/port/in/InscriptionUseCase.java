package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Inscription;

import java.util.List;
import java.util.Map;

public interface InscriptionUseCase {
    List<Inscription> getMesInscriptions(String email);
    boolean estInscrit(String email, Long formationId);
    Inscription initierPaiement(String email, Long formationId);
    Inscription confirmerPaiement(String email, Long formationId, Map<String, Object> payloadCarte);
    Inscription confirmerDepuisStripe(String email, Long formationId, String stripeSessionId);
}
