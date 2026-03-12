package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Formation;

import java.util.List;

public interface FormateurRepositoryPort {
    List<Formation> findFormationsByFormateurEmail(String email);
}
