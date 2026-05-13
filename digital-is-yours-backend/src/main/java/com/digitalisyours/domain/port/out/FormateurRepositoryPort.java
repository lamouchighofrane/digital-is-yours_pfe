package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Formation;

import java.util.List;
import java.util.Map;

public interface FormateurRepositoryPort {
    List<Formation> findFormationsByFormateurEmail(String email);

    // ── NOUVEAU ──────────────────────────────────────────────────
    List<Map<String, Object>> findApprenantsAvecStatut(List<Long> formationIds);
    List<Map<String, Object>> findInfractions(List<Long> formationIds);
}