package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Formation;

import java.util.List;
import java.util.Map;

public interface FormateurUseCase {
    List<Formation> getMesFormations(String email);
    Map<String, Object> getStats(String email);
}
