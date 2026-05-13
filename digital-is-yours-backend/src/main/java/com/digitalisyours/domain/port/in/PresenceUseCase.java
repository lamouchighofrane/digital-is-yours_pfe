package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.PresenceSeance;
import java.util.List;

public interface PresenceUseCase {
    void apprenantRejoindre(Long seanceId, String email);
    void apprenantQuitter(Long seanceId, String email);
    List<PresenceSeance> getPresences(Long seanceId);
}