package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Certificat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface AdminCertificatRepositoryPort {

    Page<Certificat> findAllWithFilters(
            String formation,
            String apprenant,
            LocalDate dateDebut,
            LocalDate dateFin,
            String search,
            Pageable pageable
    );

    long countThisMonth();
    long countFormationsActives();
    double getTauxReussite();
    long countTotal();
}