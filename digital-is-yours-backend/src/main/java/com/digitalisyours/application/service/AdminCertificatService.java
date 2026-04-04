package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.port.in.AdminCertificatUseCase;
import com.digitalisyours.domain.port.out.AdminCertificatRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminCertificatService implements AdminCertificatUseCase {

    private final AdminCertificatRepositoryPort adminCertificatRepository;

    @Override
    public Page<Certificat> getAllCertificats(
            String formation,
            String apprenant,
            LocalDate dateDebut,
            LocalDate dateFin,
            String search,
            Pageable pageable) {

        // Normaliser les filtres vides
        String f = (formation != null && !formation.isBlank()) ? formation.trim() : null;
        String a = (apprenant != null && !apprenant.isBlank()) ? apprenant.trim() : null;
        String s = (search    != null && !search.isBlank())    ? search.trim()    : null;

        return adminCertificatRepository.findAllWithFilters(f, a, dateDebut, dateFin, s, pageable);
    }

    @Override
    public Map<String, Object> getStatsCertificats() {
        long totalCeMois       = adminCertificatRepository.countThisMonth();
        long formationsActives = adminCertificatRepository.countFormationsActives();
        double tauxReussite    = adminCertificatRepository.getTauxReussite();
        long total             = adminCertificatRepository.countTotal();

        return Map.of(
                "totalCeMois",       totalCeMois,
                "formationsActives", formationsActives,
                "tauxReussite",      Math.round(tauxReussite * 10.0) / 10.0,
                "total",             total
        );
    }
}