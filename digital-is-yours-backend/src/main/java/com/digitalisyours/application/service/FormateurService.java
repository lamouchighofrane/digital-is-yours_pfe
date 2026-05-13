package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.port.in.FormateurUseCase;
import com.digitalisyours.domain.port.out.FormateurRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                .mapToInt(f -> f.getNombreInscrits() != null ? f.getNombreInscrits() : 0).sum();
        int totalCertifies = formations.stream()
                .mapToInt(f -> f.getNombreCertifies() != null ? f.getNombreCertifies() : 0).sum();
        int tauxReussite = totalApprenants > 0
                ? (int) Math.round((double) totalCertifies / totalApprenants * 100) : 0;
        double noteMoyenne = formations.stream()
                .filter(f -> f.getNoteMoyenne() != null && f.getNoteMoyenne() > 0)
                .mapToDouble(Formation::getNoteMoyenne).average().orElse(0.0);
        return Map.of(
                "totalApprenants",  totalApprenants,
                "tauxReussite",     tauxReussite,
                "nouveauxInscrits", 0,
                "noteMoyenne",      Math.round(noteMoyenne * 10.0) / 10.0
        );
    }

    // ── NOUVEAU ──────────────────────────────────────────────────

    @Override
    public Map<String, Object> getMesApprenants(String email, int page, int size,
                                                String search, String statut,
                                                Long formationId) {
        List<Long> formationIds = formateurRepository
                .findFormationsByFormateurEmail(email)
                .stream().map(Formation::getId).collect(Collectors.toList());

        if (formationIds.isEmpty()) {
            return Map.of("apprenants", List.of(), "total", 0, "totalPages", 0, "currentPage", 0);
        }

        List<Map<String, Object>> all = formateurRepository.findApprenantsAvecStatut(formationIds);

        // Filtre formation
        if (formationId != null) {
            all = all.stream()
                    .filter(a -> formationId.equals(a.get("formationId")))
                    .collect(Collectors.toList());
        }

        // Filtre statut
        if (statut != null && !statut.isBlank() && !"ALL".equals(statut)) {
            all = all.stream()
                    .filter(a -> statut.equals(a.get("statutEnrichi")))
                    .collect(Collectors.toList());
        }

        // Filtre recherche
        if (search != null && !search.isBlank()) {
            String s = search.toLowerCase();
            all = all.stream()
                    .filter(a -> {
                        String prenom   = a.get("prenom")  != null ? a.get("prenom").toString().toLowerCase()  : "";
                        String nom      = a.get("nom")     != null ? a.get("nom").toString().toLowerCase()     : "";
                        String mail     = a.get("email")   != null ? a.get("email").toString().toLowerCase()   : "";
                        return prenom.contains(s) || nom.contains(s) || mail.contains(s);
                    })
                    .collect(Collectors.toList());
        }

        // Pagination
        int total      = all.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int from       = Math.min(page * size, total);
        int to         = Math.min(from + size, total);

        return Map.of(
                "apprenants",   all.subList(from, to),
                "total",        total,
                "totalPages",   totalPages,
                "currentPage",  page
        );
    }

    @Override
    public Map<String, Object> getMesInfractions(String email, int page, int size,
                                                 String search, String type,
                                                 Long formationId) {
        List<Long> formationIds = formateurRepository
                .findFormationsByFormateurEmail(email)
                .stream().map(Formation::getId).collect(Collectors.toList());

        if (formationIds.isEmpty()) {
            return Map.of("infractions", List.of(), "total", 0, "totalPages", 0, "currentPage", 0);
        }

        List<Map<String, Object>> all = formateurRepository.findInfractions(formationIds);

        // Filtre formation
        if (formationId != null) {
            all = all.stream()
                    .filter(a -> formationId.equals(a.get("formationId")))
                    .collect(Collectors.toList());
        }

        // Filtre type
        if (type != null && !type.isBlank() && !"ALL".equals(type)) {
            all = all.stream()
                    .filter(a -> type.equals(a.get("typeQuiz")))
                    .collect(Collectors.toList());
        }

        // Filtre recherche
        if (search != null && !search.isBlank()) {
            String s = search.toLowerCase();
            all = all.stream()
                    .filter(a -> {
                        String prenom   = a.get("apprenantPrenom") != null ? a.get("apprenantPrenom").toString().toLowerCase() : "";
                        String nom      = a.get("apprenantNom")    != null ? a.get("apprenantNom").toString().toLowerCase()    : "";
                        String mail     = a.get("apprenantEmail")  != null ? a.get("apprenantEmail").toString().toLowerCase()  : "";
                        String form     = a.get("formationTitre")  != null ? a.get("formationTitre").toString().toLowerCase()  : "";
                        return prenom.contains(s) || nom.contains(s) || mail.contains(s) || form.contains(s);
                    })
                    .collect(Collectors.toList());
        }

        int total      = all.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int from       = Math.min(page * size, total);
        int to         = Math.min(from + size, total);

        return Map.of(
                "infractions",  all.subList(from, to),
                "total",        total,
                "totalPages",   totalPages,
                "currentPage",  page
        );
    }
}