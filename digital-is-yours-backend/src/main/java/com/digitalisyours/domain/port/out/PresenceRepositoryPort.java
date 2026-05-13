package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.PresenceSeance;
import java.util.List;
import java.util.Optional;

public interface PresenceRepositoryPort {
    PresenceSeance save(PresenceSeance presence);
    Optional<PresenceSeance> findBySeanceIdAndEmail(Long seanceId, String email);
    List<PresenceSeance> findBySeanceId(Long seanceId);
}