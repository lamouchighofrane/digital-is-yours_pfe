package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.port.in.ProfilApprenantUseCase;
import com.digitalisyours.domain.port.out.ProfilApprenantRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProfilApprenantService implements ProfilApprenantUseCase {
    private final ProfilApprenantRepositoryPort profilRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Apprenant getProfil(String email) {
        ensureApprenantRow(email);
        return profilRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Profil apprenant non trouvé"));
    }

    @Override
    public Apprenant updateProfil(String email, Map<String, Object> payload) {
        Apprenant apprenant = getProfil(email);

        setStr(payload, "prenom",    v -> { if (!v.isBlank()) apprenant.setPrenom(v); });
        setStr(payload, "nom",       v -> { if (!v.isBlank()) apprenant.setNom(v); });
        setStr(payload, "telephone", apprenant::setTelephone);
        setStr(payload, "photo",     apprenant::setPhoto);
        setStr(payload, "bio",       apprenant::setBio);
        setStr(payload, "niveauActuel",           apprenant::setNiveauActuel);
        setStr(payload, "objectifsApprentissage", apprenant::setObjectifsApprentissage);

        if (payload.containsKey("disponibilitesHeuresParSemaine")) {
            Object v = payload.get("disponibilitesHeuresParSemaine");
            apprenant.setDisponibilitesHeuresParSemaine(v != null ? Integer.valueOf(v.toString()) : null);
        }
        if (payload.containsKey("domainesInteret")) {
            apprenant.setDomainesInteret(parseList(payload.get("domainesInteret")));
        }
        if (payload.containsKey("disponibilites")) {
            apprenant.setDisponibilites(parseList(payload.get("disponibilites")));
        }

        return profilRepository.save(apprenant);
    }

    @Override
    public void changerMotDePasse(String email, String ancien, String nouveau, String confirm) {
        if (ancien == null || nouveau == null || confirm == null)
            throw new RuntimeException("Tous les champs sont requis");

        Apprenant apprenant = getProfil(email);

        if (!passwordEncoder.matches(ancien, apprenant.getMotDePasse()))
            throw new RuntimeException("Mot de passe actuel incorrect");
        if (!nouveau.equals(confirm))
            throw new RuntimeException("Les mots de passe ne correspondent pas");
        if (nouveau.length() < 8)
            throw new RuntimeException("Minimum 8 caractères requis");

        apprenant.setMotDePasse(passwordEncoder.encode(nouveau));
        profilRepository.save(apprenant);
    }

    // ── Helpers ───────────────────────────────────────────────

    private void ensureApprenantRow(String email) {
        if (profilRepository.findByEmail(email).isEmpty()) {
            profilRepository.findUserIdByEmail(email).ifPresent(
                    profilRepository::insertLigneApprenantManquante
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
    private List<String> parseList(Object raw) {
        if (raw instanceof List<?> list)
            return list.stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
        return new java.util.ArrayList<>();
    }
}