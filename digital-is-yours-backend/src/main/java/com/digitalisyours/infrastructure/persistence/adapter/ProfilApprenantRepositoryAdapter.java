package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.port.out.ProfilApprenantRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.ApprenantEntity;
import com.digitalisyours.infrastructure.persistence.repository.ApprenantJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfilApprenantRepositoryAdapter implements ProfilApprenantRepositoryPort {
    private final ApprenantJpaRepository apprenantJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Optional<Apprenant> findByEmail(String email) {
        return apprenantJpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Apprenant save(Apprenant apprenant) {
        ApprenantEntity entity = apprenantJpaRepository.findByEmail(apprenant.getEmail())
                .orElseThrow(() -> new RuntimeException("ApprenantEntity non trouvé"));

        entity.setPrenom(apprenant.getPrenom());
        entity.setNom(apprenant.getNom());
        entity.setTelephone(apprenant.getTelephone());
        entity.setPhoto(apprenant.getPhoto());
        entity.setMotDePasse(apprenant.getMotDePasse());
        entity.setBio(apprenant.getBio());
        entity.setNiveauActuel(apprenant.getNiveauActuel());
        entity.setDomainesInteret(listToJson(apprenant.getDomainesInteret()));
        entity.setDisponibilites(listToJson(apprenant.getDisponibilites()));
        entity.setObjectifsApprentissage(apprenant.getObjectifsApprentissage());
        entity.setDisponibilitesHeuresParSemaine(apprenant.getDisponibilitesHeuresParSemaine());

        return toDomain(apprenantJpaRepository.save(entity));
    }

    @Override
    public void insertLigneApprenantManquante(Long userId) {
        apprenantJpaRepository.insertLigneApprenantManquante(userId);
    }

    @Override
    public Optional<Long> findUserIdByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(u -> u.getId());
    }

    @Override
    public boolean isApprenant(String email) {
        return userJpaRepository.findByEmail(email)
                .map(u -> u.getRole() == Role.APPRENANT)
                .orElse(false);
    }

    // ── Mapping entity → domain ───────────────────────────────

    private Apprenant toDomain(ApprenantEntity e) {
        return Apprenant.builder()
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
                .niveauActuel(e.getNiveauActuel())
                .domainesInteret(parseJsonList(e.getDomainesInteret()))
                .disponibilites(parseJsonList(e.getDisponibilites()))
                .objectifsApprentissage(e.getObjectifsApprentissage())
                .disponibilitesHeuresParSemaine(e.getDisponibilitesHeuresParSemaine())
                .build();
    }

    // ── JSON helpers ──────────────────────────────────────────

    private String listToJson(List<String> list) {
        try { return list != null ? objectMapper.writeValueAsString(list) : "[]"; }
        catch (Exception e) { return "[]"; }
    }

    private List<String> parseJsonList(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }
}
