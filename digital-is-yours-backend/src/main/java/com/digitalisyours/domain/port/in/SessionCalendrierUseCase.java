package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.SessionCalendrier;

import java.util.List;
import java.util.Map;

public interface SessionCalendrierUseCase {
    List<SessionCalendrier> getMesSessions(String email);
    SessionCalendrier ajouterSession(String email, Map<String, Object> payload);
    SessionCalendrier modifierSession(String email, Long id, Map<String, Object> payload);
    void supprimerSession(String email, Long id);
}