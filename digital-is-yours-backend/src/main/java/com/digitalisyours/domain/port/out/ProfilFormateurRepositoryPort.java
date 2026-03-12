package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Formateur;

import java.util.Optional;

public interface ProfilFormateurRepositoryPort {
    Optional<Formateur> findByEmail(String email);
    Formateur save(Formateur formateur);
    void insertLigneFormateurManquante(Long userId);
    Optional<Long> findUserIdByEmail(String email);
    boolean isFormateur(String email);
}
