package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.port.in.FormateurUseCase;
import com.digitalisyours.domain.port.out.FormateurRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FormateurService implements FormateurUseCase {
    private final FormateurRepositoryPort formateurRepository;

    @Override
    public List<Formation> getMesFormations(String email) {
        return formateurRepository.findFormationsByFormateurEmail(email);
    }

    @Override
    public Map<String, Object> getStats(String email) {
        List<Formation> formations = formateurRepository.findFormationsByFormateurEmail(email);

        int totalApprenants = formations.stream()
                .mapToInt(f -> f.getNombreInscrits() != null ? f.getNombreInscrits() : 0)
                .sum();

        int totalCertifies = formations.stream()
                .mapToInt(f -> f.getNombreCertifies() != null ? f.getNombreCertifies() : 0)
                .sum();

        int tauxReussite = totalApprenants > 0
                ? (int) Math.round((double) totalCertifies / totalApprenants * 100) : 0;

        double noteMoyenne = formations.stream()
                .filter(f -> f.getNoteMoyenne() != null && f.getNoteMoyenne() > 0)
                .mapToDouble(Formation::getNoteMoyenne)
                .average()
                .orElse(0.0);

        return Map.of(
                "totalApprenants",  totalApprenants,
                "tauxReussite",     tauxReussite,
                "nouveauxInscrits", 0,
                "noteMoyenne",      Math.round(noteMoyenne * 10.0) / 10.0
        );
    }
}
