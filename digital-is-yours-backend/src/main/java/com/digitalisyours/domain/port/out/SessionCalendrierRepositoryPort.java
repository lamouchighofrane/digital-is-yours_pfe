package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.SessionCalendrier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessionCalendrierRepositoryPort {
    List<SessionCalendrier> findByApprenantEmail(String email);
    List<SessionCalendrier> findRappelsAEnvoyer(LocalDateTime from, LocalDateTime to);
    Optional<SessionCalendrier> findById(Long id);
    SessionCalendrier save(SessionCalendrier session);
    void deleteById(Long id);
}
