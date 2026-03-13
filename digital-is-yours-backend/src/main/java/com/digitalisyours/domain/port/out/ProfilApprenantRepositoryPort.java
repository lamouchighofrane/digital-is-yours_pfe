package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Apprenant;

import java.util.Optional;

public interface ProfilApprenantRepositoryPort {
    Optional<Apprenant> findByEmail(String email);
    Apprenant save(Apprenant apprenant);
    void insertLigneApprenantManquante(Long userId);
    Optional<Long> findUserIdByEmail(String email);
    boolean isApprenant(String email);
}
