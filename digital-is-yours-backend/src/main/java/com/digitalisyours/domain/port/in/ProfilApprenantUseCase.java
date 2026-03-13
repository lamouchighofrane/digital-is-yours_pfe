package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Apprenant;

import java.util.Map;

public interface ProfilApprenantUseCase {
    Apprenant getProfil(String email);
    Apprenant updateProfil(String email, Map<String, Object> payload);
    void changerMotDePasse(String email, String ancien, String nouveau, String confirm);
}
