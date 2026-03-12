package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Formateur;
import com.digitalisyours.domain.port.in.ProfilFormateurUseCase;
import com.digitalisyours.domain.port.out.ProfilFormateurRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProfilFormateurService implements ProfilFormateurUseCase {
    private final ProfilFormateurRepositoryPort profilRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Formateur getProfil(String email) {
        ensureFormateurRow(email);
        return profilRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Profil formateur non trouvé"));
    }

    @Override
    public Formateur updateProfil(String email, Map<String, Object> payload) {
        Formateur formateur = getProfil(email);

        setStr(payload, "prenom",    v -> { if (!v.isBlank()) formateur.setPrenom(v); });
        setStr(payload, "nom",       v -> { if (!v.isBlank()) formateur.setNom(v); });
        setStr(payload, "telephone", formateur::setTelephone);
        setStr(payload, "photo",     formateur::setPhoto);
        setStr(payload, "bio",       formateur::setBio);
        setStr(payload, "specialite",formateur::setSpecialite);

        if (payload.containsKey("anneesExperience")) {
            Object v = payload.get("anneesExperience");
            formateur.setAnneesExperience(v != null ? Integer.valueOf(v.toString()) : null);
        }
        if (payload.containsKey("competences")) {
            formateur.setCompetences(parseList(payload.get("competences")));
        }
        if (payload.containsKey("reseauxSociaux")) {
            formateur.setReseauxSociaux(parseMap(payload.get("reseauxSociaux")));
        }

        return profilRepository.save(formateur);
    }

    @Override
    public void changerMotDePasse(String email, String ancien, String nouveau, String confirm) {
        if (ancien == null || nouveau == null || confirm == null)
            throw new RuntimeException("Tous les champs sont requis");

        Formateur formateur = getProfil(email);

        if (!passwordEncoder.matches(ancien, formateur.getMotDePasse()))
            throw new RuntimeException("Mot de passe actuel incorrect");
        if (!nouveau.equals(confirm))
            throw new RuntimeException("Les mots de passe ne correspondent pas");
        if (nouveau.length() < 8)
            throw new RuntimeException("Minimum 8 caractères requis");

        formateur.setMotDePasse(passwordEncoder.encode(nouveau));
        profilRepository.save(formateur);
    }

    // ── Helpers ───────────────────────────────────────────────

    private void ensureFormateurRow(String email) {
        if (profilRepository.findByEmail(email).isEmpty()) {
            profilRepository.findUserIdByEmail(email).ifPresent(
                    profilRepository::insertLigneFormateurManquante
            );
        }
    }

    private interface Setter { void set(String v); }

    private void setStr(Map<String, Object> payload, String key, Setter setter) {
        if (payload.containsKey(key)) {
            Object v = payload.get(key);
            setter.set(v != null ? v.toString() : null);
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> parseList(Object raw) {
        if (raw instanceof java.util.List<?> list)
            return list.stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
        return new java.util.ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, String> parseMap(Object raw) {
        if (raw instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k.toString(), v != null ? v.toString() : ""));
            return result;
        }
        return new java.util.LinkedHashMap<>();
    }
}
