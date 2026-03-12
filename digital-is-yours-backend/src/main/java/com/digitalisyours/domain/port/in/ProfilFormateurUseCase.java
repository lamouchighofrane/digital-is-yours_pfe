package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Formateur;

import java.util.Map;

public interface ProfilFormateurUseCase {
    Formateur getProfil(String email);
    Formateur updateProfil(String email, Map<String, Object> payload);
    void changerMotDePasse(String email, String ancien, String nouveau, String confirm);
}
