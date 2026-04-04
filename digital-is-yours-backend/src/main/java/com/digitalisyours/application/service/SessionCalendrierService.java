package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.SessionCalendrier;
import com.digitalisyours.domain.port.in.SessionCalendrierUseCase;
import com.digitalisyours.domain.port.out.ProfilApprenantRepositoryPort;
import com.digitalisyours.domain.port.out.SessionCalendrierRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessionCalendrierService implements SessionCalendrierUseCase {
    private final SessionCalendrierRepositoryPort sessionRepo;
    private final ProfilApprenantRepositoryPort apprenantRepo;

    @Override
    public List<SessionCalendrier> getMesSessions(String email) {
        return sessionRepo.findByApprenantEmail(email);
    }

    @Override
    public SessionCalendrier ajouterSession(String email,
                                            Map<String, Object> payload) {
        Long apprenantId = apprenantRepo.findUserIdByEmail(email)
                .orElseThrow(() -> new RuntimeException("Apprenant non trouvé"));

        if (payload.get("titre") == null ||
                payload.get("titre").toString().isBlank())
            throw new RuntimeException("Le titre est requis");

        if (payload.get("dateSession") == null)
            throw new RuntimeException("La date est requise");

        SessionCalendrier session = SessionCalendrier.builder()
                .apprenantId(apprenantId)
                .formationId(getLong(payload, "formationId"))
                .titrePersonnalise(payload.get("titre").toString())
                .dateSession(LocalDateTime.parse(
                        payload.get("dateSession").toString()))
                .dureeMinutes(getInt(payload, "dureeMinutes", 60))
                .typeSession(payload.getOrDefault(
                        "typeSession", "COURS").toString())
                .notes((String) payload.get("notes"))
                .rappel24h(getBool(payload, "rappel24h", true))
                .rappelEnvoye(false)
                .isTerminee(false)
                .build();

        return sessionRepo.save(session);
    }

    @Override
    public SessionCalendrier modifierSession(String email, Long id,
                                             Map<String, Object> payload) {
        SessionCalendrier session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session non trouvée"));

        if (payload.containsKey("titre"))
            session.setTitrePersonnalise(
                    payload.get("titre").toString());
        if (payload.containsKey("dateSession"))
            session.setDateSession(LocalDateTime.parse(
                    payload.get("dateSession").toString()));
        if (payload.containsKey("dureeMinutes"))
            session.setDureeMinutes(
                    getInt(payload, "dureeMinutes", 60));
        if (payload.containsKey("typeSession"))
            session.setTypeSession(
                    payload.get("typeSession").toString());
        if (payload.containsKey("notes"))
            session.setNotes((String) payload.get("notes"));
        if (payload.containsKey("rappel24h"))
            session.setRappel24h(
                    getBool(payload, "rappel24h", true));
        if (payload.containsKey("isTerminee"))
            session.setTerminee(
                    getBool(payload, "isTerminee", false));

        return sessionRepo.save(session);
    }

    @Override
    public void supprimerSession(String email, Long id) {
        sessionRepo.deleteById(id);
    }

    // ── Helpers ──
    private Long getLong(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Long.valueOf(v.toString()) : null;
    }

    private int getInt(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        return v != null ? Integer.parseInt(v.toString()) : def;
    }

    private boolean getBool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v != null ? Boolean.parseBoolean(v.toString()) : def;
    }
}