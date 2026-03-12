package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Formateur;
import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.port.out.ProfilFormateurRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.FormateurEntity;
import com.digitalisyours.infrastructure.persistence.repository.FormateurJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfilFormateurRepositoryAdapter implements ProfilFormateurRepositoryPort {
    private final FormateurJpaRepository formateurJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Optional<Formateur> findByEmail(String email) {
        return formateurJpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Formateur save(Formateur formateur) {
        FormateurEntity entity = formateurJpaRepository.findByEmail(formateur.getEmail())
                .orElseThrow(() -> new RuntimeException("FormateurEntity non trouvé"));

        entity.setPrenom(formateur.getPrenom());
        entity.setNom(formateur.getNom());
        entity.setTelephone(formateur.getTelephone());
        entity.setPhoto(formateur.getPhoto());
        entity.setMotDePasse(formateur.getMotDePasse());
        entity.setBio(formateur.getBio());
        entity.setSpecialite(formateur.getSpecialite());
        entity.setAnneesExperience(formateur.getAnneesExperience());
        entity.setCompetences(listToJson(formateur.getCompetences()));
        entity.setReseauxSociaux(mapToJson(formateur.getReseauxSociaux()));

        return toDomain(formateurJpaRepository.save(entity));
    }

    @Override
    public void insertLigneFormateurManquante(Long userId) {
        formateurJpaRepository.insertLigneFormateurManquante(userId);
    }

    @Override
    public Optional<Long> findUserIdByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(u -> u.getId());
    }

    @Override
    public boolean isFormateur(String email) {
        return userJpaRepository.findByEmail(email)
                .map(u -> u.getRole() == Role.FORMATEUR)
                .orElse(false);
    }

    // ── Mapping ───────────────────────────────────────────────

    private Formateur toDomain(FormateurEntity e) {
        return Formateur.builder()
                .id(e.getId())
                .prenom(e.getPrenom())
                .nom(e.getNom())
                .email(e.getEmail())
                .telephone(e.getTelephone())
                .photo(e.getPhoto())
                .motDePasse(e.getMotDePasse())
                .role(e.getRole())
                .active(e.isActive())
                .dateInscription(e.getDateInscription())
                .derniereConnexion(e.getDerniereConnexion())
                .bio(e.getBio())
                .specialite(e.getSpecialite())
                .anneesExperience(e.getAnneesExperience())
                .competences(parseJsonList(e.getCompetences()))
                .reseauxSociaux(parseJsonMap(e.getReseauxSociaux()))
                .build();
    }

    private String listToJson(List<String> list) {
        try { return list != null ? objectMapper.writeValueAsString(list) : "[]"; }
        catch (Exception e) { return "[]"; }
    }

    private String mapToJson(Map<String, String> map) {
        try { return map != null ? objectMapper.writeValueAsString(map) : "{}"; }
        catch (Exception e) { return "{}"; }
    }

    private List<String> parseJsonList(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private Map<String, String> parseJsonMap(String json) {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("linkedin", ""); defaults.put("twitter", "");
        defaults.put("portfolio", ""); defaults.put("github", "");
        try {
            if (json == null || json.isBlank()) return defaults;
            Map<String, String> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, String>>() {});
            defaults.putAll(parsed);
            return defaults;
        } catch (Exception e) { return defaults; }
    }
}
