package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Certificat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Map;

public interface AdminCertificatUseCase {

    Page<Certificat> getAllCertificats(
            String formation,
            String apprenant,
            LocalDate dateDebut,
            LocalDate dateFin,
            String search,
            Pageable pageable
    );

    Map<String, Object> getStatsCertificats();
}