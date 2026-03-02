package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.Role;
import com.digitalisyours.infrastructure.persistence.entity.FormateurEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.FormateurJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/formateur/profil")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class ProfilFormateurController {
    private final UserJpaRepository userRepository;
    private final FormateurJpaRepository formateurRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // ══ GET profil complet ════════════════════════════════════
    @GetMapping
    public ResponseEntity<?> getProfil(HttpServletRequest request) {
        FormateurEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        return ResponseEntity.ok(toResponse(formateur));
    }

    // ══ PUT mettre à jour le profil ═══════════════════════════
    @PutMapping
    public ResponseEntity<?> updateProfil(
            HttpServletRequest request,
            @RequestBody Map<String, Object> payload) {

        FormateurEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        // Champs UserEntity
        setStr(payload, "prenom",    v -> { if (!v.isBlank()) formateur.setPrenom(v); });
        setStr(payload, "nom",       v -> { if (!v.isBlank()) formateur.setNom(v); });
        setStr(payload, "telephone", formateur::setTelephone);
        setStr(payload, "photo",     formateur::setPhoto);

        // Champs FormateurEntity
        setStr(payload, "bio",       formateur::setBio);
        setStr(payload, "specialite",formateur::setSpecialite);

        if (payload.containsKey("anneesExperience")) {
            Object v = payload.get("anneesExperience");
            formateur.setAnneesExperience(v != null ? Integer.valueOf(v.toString()) : null);
        }
        if (payload.containsKey("competences"))
            formateur.setCompetences(listToJson(payload.get("competences")));
        if (payload.containsKey("reseauxSociaux"))
            formateur.setReseauxSociaux(mapToJson(payload.get("reseauxSociaux")));

        formateurRepository.save(formateur);
        log.info("Profil formateur mis à jour : {} {}", formateur.getPrenom(), formateur.getNom());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Profil mis à jour avec succès",
                "profil",  toResponse(formateur)
        ));
    }

    // ══ PATCH changer mot de passe ════════════════════════════
    @PatchMapping("/mot-de-passe")
    public ResponseEntity<?> changerMotDePasse(
            HttpServletRequest request,
            @RequestBody Map<String, Object> payload) {

        FormateurEntity formateur = getFormateurFromRequest(request);
        if (formateur == null)
            return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));

        String ancien  = (String) payload.get("ancienMotDePasse");
        String nouveau = (String) payload.get("nouveauMotDePasse");
        String confirm = (String) payload.get("confirmMotDePasse");

        if (ancien == null || nouveau == null || confirm == null)
            return bad("Tous les champs sont requis");
        if (!passwordEncoder.matches(ancien, formateur.getMotDePasse()))
            return bad("Mot de passe actuel incorrect");
        if (!nouveau.equals(confirm))
            return bad("Les mots de passe ne correspondent pas");
        if (nouveau.length() < 8)
            return bad("Minimum 8 caractères requis");

        formateur.setMotDePasse(passwordEncoder.encode(nouveau));
        formateurRepository.save(formateur);
        return ResponseEntity.ok(Map.of("success", true, "message", "Mot de passe modifié avec succès"));
    }

    // ══ HELPERS ═══════════════════════════════════════════════

    /**
     * Extrait le formateur depuis le JWT.
     *
     * CORRECTION DU BUG :
     * L'utilisateur peut exister dans "users" avec rôle FORMATEUR
     * mais ne pas avoir de ligne dans "formateurs" (créé avant la migration JOINED).
     *
     * Solution :
     * 1. Vérifier le rôle via UserJpaRepository
     * 2. Chercher dans FormateurJpaRepository (table formateurs)
     * 3. Si absent → insérer la ligne manquante via requête native
     */
    private FormateurEntity getFormateurFromRequest(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            if (!jwtUtil.isValid(token)) return null;
            String email = jwtUtil.extractEmail(token);

            // 1. Vérifier rôle
            UserEntity user = userRepository.findByEmail(email).orElse(null);
            if (user == null || user.getRole() != Role.FORMATEUR) return null;

            // 2. Chercher dans la table formateurs
            FormateurEntity formateur = formateurRepository.findByEmail(email).orElse(null);

            // 3. Ligne absente → créer automatiquement (migration)
            if (formateur == null) {
                log.warn("Formateur {} sans entrée dans table 'formateurs' — insertion automatique", email);
                formateurRepository.insertLigneFormateurManquante(user.getId());
                formateur = formateurRepository.findByEmail(email).orElse(null);
                if (formateur == null) {
                    log.error("Impossible de créer la ligne formateurs pour {}", email);
                    return null;
                }
                log.info("Ligne formateurs créée automatiquement pour id={} email={}", user.getId(), email);
            }

            return formateur;
        } catch (Exception e) {
            log.warn("Erreur JWT profil formateur: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> toResponse(FormateurEntity f) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",                f.getId());
        map.put("prenom",            f.getPrenom());
        map.put("nom",               f.getNom());
        map.put("email",             f.getEmail());
        map.put("telephone",         f.getTelephone());
        map.put("photo",             f.getPhoto());
        map.put("role",              f.getRole());
        map.put("active",            f.isActive());
        map.put("dateInscription",   f.getDateInscription());
        map.put("derniereConnexion", f.getDerniereConnexion());
        map.put("bio",               f.getBio());
        map.put("specialite",        f.getSpecialite());
        map.put("anneesExperience",  f.getAnneesExperience());
        map.put("competences",       parseJsonList(f.getCompetences()));
        map.put("reseauxSociaux",    parseJsonMap(f.getReseauxSociaux()));
        return map;
    }

    private interface Setter { void set(String v); }

    private void setStr(Map<String, Object> payload, String key, Setter setter) {
        if (payload.containsKey(key)) {
            Object v = payload.get(key);
            setter.set(v != null ? v.toString() : null);
        }
    }

    private ResponseEntity<Map<String, Object>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg));
    }

    private String listToJson(Object raw) {
        if (raw == null) return "[]";
        if (raw instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append("\"").append(list.get(i).toString().replace("\"", "\\\"")).append("\"");
                if (i < list.size() - 1) sb.append(",");
            }
            return sb.append("]").toString();
        }
        return raw.toString();
    }

    private String mapToJson(Object raw) {
        if (raw == null) return "{}";
        if (raw instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":")
                        .append("\"").append(e.getValue() != null ? e.getValue().toString().replace("\"", "\\\"") : "").append("\"");
                first = false;
            }
            return sb.append("}").toString();
        }
        return raw.toString();
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return new ArrayList<>();
        try {
            String cleaned = json.trim().replaceAll("^\\[|]$", "");
            if (cleaned.isBlank()) return new ArrayList<>();
            List<String> result = new ArrayList<>();
            for (String p : cleaned.split(",")) {
                String tag = p.trim().replaceAll("^\"|\"$", "");
                if (!tag.isBlank()) result.add(tag);
            }
            return result;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private Map<String, String> parseJsonMap(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("linkedin", ""); result.put("twitter", "");
        result.put("portfolio", ""); result.put("github", "");
        if (json == null || json.isBlank() || json.equals("{}")) return result;
        try {
            String cleaned = json.trim().replaceAll("^\\{|\\}$", "");
            for (String pair : cleaned.split(",(?=\")")) {
                String[] kv = pair.split("\":\"", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    String v = kv[1].trim().replaceAll("\"$", "");
                    result.put(k, v);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }
}
