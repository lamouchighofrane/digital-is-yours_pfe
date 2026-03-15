package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.RecommandationIA;
import com.digitalisyours.domain.port.in.RecommandationUseCase;
import com.digitalisyours.domain.port.out.RecommandationRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommandationService implements RecommandationUseCase {
    @Value("${groq.api.key:}")
    private String groqApiKey;

    private static final String GROQ_URL      = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL    = "llama-3.3-70b-versatile";
    private static final int    CACHE_MINUTES = 30;
    private static final int    TOP_N         = 5;

    private final RecommandationRepositoryPort repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final List<RecommandationIA> data;
        final LocalDateTime expireAt;
        CacheEntry(List<RecommandationIA> data) {
            this.data     = data;
            this.expireAt = LocalDateTime.now().plusMinutes(CACHE_MINUTES);
        }
        boolean isValid() { return LocalDateTime.now().isBefore(expireAt); }
    }

    @Override
    public List<RecommandationIA> getRecommandations(String emailApprenant) {

        // 1. Cache
        CacheEntry cached = cache.get(emailApprenant);
        if (cached != null && cached.isValid()) {
            log.info("Recommandations depuis le cache pour {}", emailApprenant);
            return cached.data;
        }

        // 2. Profil apprenant
        Apprenant apprenant = repository.findApprenantByEmail(emailApprenant).orElse(null);
        if (apprenant == null) {
            log.warn("Apprenant non trouvé : {}", emailApprenant);
            return new ArrayList<>();
        }

        // ✅ 3. Vérifier profil suffisamment rempli
        boolean domainesVides = apprenant.getDomainesInteret() == null
                || apprenant.getDomainesInteret().isEmpty();
        boolean objectifsVides = apprenant.getObjectifsApprentissage() == null
                || apprenant.getObjectifsApprentissage().isBlank();

        if (domainesVides && objectifsVides) {
            log.info("Profil incomplet pour {} — recommandations non calculées", emailApprenant);
            return new ArrayList<>(); // Frontend affiche "complétez votre profil"
        }

        // 4. Formations disponibles
        List<RecommandationIA> formations = repository.findFormationsDisponibles(emailApprenant);
        if (formations.isEmpty()) {
            log.info("Aucune formation disponible pour {}", emailApprenant);
            return new ArrayList<>();
        }

        // 5. Calcul
        List<RecommandationIA> recommandations;
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            recommandations = calculerAvecGroq(apprenant, formations);
        } else {
            log.warn("Clé Groq manquante — algorithme de fallback activé");
            recommandations = calculerAvecAlgorithme(apprenant, formations);
        }

        // 6. Cache
        cache.put(emailApprenant, new CacheEntry(recommandations));
        log.info("Recommandations calculées et mises en cache pour {} ({} résultats)",
                emailApprenant, recommandations.size());

        return recommandations;
    }

    @Override
    public void invaliderCache(String emailApprenant) {
        cache.remove(emailApprenant);
        log.info("Cache recommandations invalidé pour {}", emailApprenant);
    }

    private List<RecommandationIA> calculerAvecGroq(Apprenant apprenant,
                                                    List<RecommandationIA> formations) {
        try {
            String prompt = buildPrompt(apprenant, formations);
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", GROQ_MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "Tu es un conseiller pédagogique expert. "
                                            + "Réponds UNIQUEMENT avec un tableau JSON valide, "
                                            + "sans markdown, sans texte avant ou après."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 2000
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                log.warn("Quota Groq dépassé (429) — fallback algorithme");
                return calculerAvecAlgorithme(apprenant, formations);
            }
            if (response.statusCode() != 200) {
                log.error("Erreur Groq {} — fallback algorithme", response.statusCode());
                return calculerAvecAlgorithme(apprenant, formations);
            }

            List<RecommandationIA> result = parseGroqResponse(response.body(), formations);
            if (result.isEmpty()) {
                log.warn("Réponse Groq vide — fallback algorithme");
                return calculerAvecAlgorithme(apprenant, formations);
            }

            log.info("Recommandations Groq réussies : {} formations scorées", result.size());
            return result;

        } catch (Exception e) {
            log.error("Erreur Groq recommandations : {} — fallback algorithme", e.getMessage());
            return calculerAvecAlgorithme(apprenant, formations);
        }
    }

    private String buildPrompt(Apprenant apprenant, List<RecommandationIA> formations) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un conseiller pédagogique. Analyse le profil de cet apprenant ");
        sb.append("et attribue un score de compatibilité (0-100) à chaque formation.\n\n");

        sb.append("=== PROFIL APPRENANT ===\n");
        sb.append("Niveau actuel : ").append(
                apprenant.getNiveauActuel() != null ? apprenant.getNiveauActuel() : "Non précisé"
        ).append("\n");

        if (apprenant.getDomainesInteret() != null && !apprenant.getDomainesInteret().isEmpty()) {
            sb.append("Domaines d'intérêt : ")
                    .append(String.join(", ", apprenant.getDomainesInteret())).append("\n");
        }
        if (apprenant.getDisponibilitesHeuresParSemaine() != null) {
            sb.append("Disponibilités : ")
                    .append(apprenant.getDisponibilitesHeuresParSemaine())
                    .append(" heures/semaine\n");
        }
        if (apprenant.getObjectifsApprentissage() != null &&
                !apprenant.getObjectifsApprentissage().isBlank()) {
            sb.append("Objectifs : ").append(apprenant.getObjectifsApprentissage()).append("\n");
        }

        sb.append("\n=== FORMATIONS DISPONIBLES ===\n");
        for (int i = 0; i < formations.size(); i++) {
            RecommandationIA f = formations.get(i);
            sb.append(i + 1).append(". ID=").append(f.getFormationId())
                    .append(" | Titre: ").append(f.getTitre())
                    .append(" | Niveau: ").append(f.getNiveau());
            if (f.getCategorie() != null) sb.append(" | Catégorie: ").append(f.getCategorie());
            if (f.getDureeEstimee() != null) sb.append(" | Durée: ").append(f.getDureeEstimee()).append("h");
            if (f.getDescription() != null && !f.getDescription().isBlank()) {
                String desc = f.getDescription().length() > 120
                        ? f.getDescription().substring(0, 120) + "..." : f.getDescription();
                sb.append(" | Description: ").append(desc);
            }
            sb.append("\n");
        }

        sb.append("\n=== INSTRUCTIONS ===\n");
        sb.append("Pour chaque formation, calcule un score de compatibilité basé sur :\n");
        sb.append("- Correspondance domaines d'intérêt (40 points)\n");
        sb.append("- Adéquation du niveau (30 points)\n");
        sb.append("- Compatibilité avec les disponibilités (15 points)\n");
        sb.append("- Alignement avec les objectifs (15 points)\n\n");
        sb.append("Retourne UNIQUEMENT le top ").append(TOP_N).append(" formations.\n\n");
        sb.append("FORMAT JSON REQUIS :\n");
        sb.append("[{\"formationId\":1,\"scoreCompatibilite\":85,");
        sb.append("\"raison\":\"Phrase impersonnelle commençant par 'Cette formation...' ou 'Formation idéale...'\",");
        sb.append("\"pointsForts\":\"Point 1, Point 2, Point 3\"}]\n");
        sb.append("IMPORTANT : formationId doit être l'ID exact. ");
        sb.append("Retourne exactement ").append(TOP_N).append(" éléments triés par score décroissant.");

        return sb.toString();
    }

    private List<RecommandationIA> parseGroqResponse(String responseBody,
                                                     List<RecommandationIA> formations) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText().trim();

            if (content.contains("```json")) content = content.substring(content.indexOf("```json") + 7);
            if (content.contains("```"))     content = content.substring(0, content.lastIndexOf("```"));
            content = content.trim();
            int jsonStart = content.indexOf('[');
            if (jsonStart > 0) content = content.substring(jsonStart);
            content = reparerJson(content);

            JsonNode array = objectMapper.readTree(content);
            if (!array.isArray() || array.size() == 0) return new ArrayList<>();

            Map<Long, RecommandationIA> formationMap = formations.stream()
                    .collect(Collectors.toMap(RecommandationIA::getFormationId, f -> f, (a, b) -> a));

            List<RecommandationIA> result = new ArrayList<>();
            for (JsonNode node : array) {
                long id    = node.path("formationId").asLong(-1);
                int  score = node.path("scoreCompatibilite").asInt(0);
                String raison      = node.path("raison").asText("");
                String pointsForts = node.path("pointsForts").asText("");
                RecommandationIA base = formationMap.get(id);
                if (base == null) continue;
                result.add(RecommandationIA.builder()
                        .formationId(base.getFormationId()).titre(base.getTitre())
                        .niveau(base.getNiveau()).description(base.getDescription())
                        .imageCouverture(base.getImageCouverture()).categorie(base.getCategorie())
                        .dureeEstimee(base.getDureeEstimee()).noteMoyenne(base.getNoteMoyenne())
                        .nombreInscrits(base.getNombreInscrits())
                        .scoreCompatibilite(Math.min(100, Math.max(0, score)))
                        .raison(raison).pointsForts(pointsForts).build());
            }
            result.sort(Comparator.comparingInt(RecommandationIA::getScoreCompatibilite).reversed());
            return result.stream().limit(TOP_N).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erreur parsing réponse Groq : {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String reparerJson(String json) {
        if (json == null || json.isBlank()) return "[]";
        json = json.replaceAll("[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F]", " ");
        try { objectMapper.readTree(json); return json; } catch (Exception ignored) {}
        int derniereFin = json.lastIndexOf("}]");
        if (derniereFin != -1) {
            String repare = json.substring(0, derniereFin + 2);
            try { objectMapper.readTree(repare); return repare; } catch (Exception ignored) {}
        }
        return "[]";
    }

    private List<RecommandationIA> calculerAvecAlgorithme(Apprenant apprenant,
                                                          List<RecommandationIA> formations) {
        log.info("Calcul recommandations par algorithme pour {}", apprenant.getEmail());

        String niveau    = apprenant.getNiveauActuel() != null
                ? apprenant.getNiveauActuel().toUpperCase() : "DEBUTANT";
        List<String> domaines = apprenant.getDomainesInteret() != null
                ? apprenant.getDomainesInteret() : new ArrayList<>();
        Integer heures   = apprenant.getDisponibilitesHeuresParSemaine();
        String objectifs = apprenant.getObjectifsApprentissage() != null
                ? apprenant.getObjectifsApprentissage().toLowerCase() : "";

        return formations.stream().map(f -> {
                    int score = 0;
                    List<String> points = new ArrayList<>();

                    String titre = f.getTitre()       != null ? f.getTitre().toLowerCase()       : "";
                    String desc  = f.getDescription() != null ? f.getDescription().toLowerCase() : "";
                    String cat   = f.getCategorie()   != null ? f.getCategorie().toLowerCase()   : "";

                    for (String d : domaines) {
                        String dl = d.toLowerCase();
                        if (titre.contains(dl) || desc.contains(dl) || cat.contains(dl)) {
                            score += 40;
                            points.add("Correspond à votre intérêt : " + d);
                            break;
                        }
                    }

                    String fNiveau = f.getNiveau() != null ? f.getNiveau().toUpperCase() : "DEBUTANT";
                    if (fNiveau.equals(niveau)) {
                        score += 30;
                        points.add("Niveau adapté : " + niveauLabel(niveau));
                    } else if (estNiveauCompatible(niveau, fNiveau)) {
                        score += 15;
                        points.add("Niveau accessible");
                    }

                    if (heures != null && f.getDureeEstimee() != null) {
                        if (f.getDureeEstimee() <= heures * 4) {
                            score += 15;
                            points.add("Compatible avec vos " + heures + "h/semaine");
                        }
                    } else {
                        score += 8;
                    }

                    if (!objectifs.isBlank() && (
                            titre.contains(objectifs.substring(0, Math.min(8, objectifs.length())))
                                    || desc.contains(objectifs.substring(0, Math.min(8, objectifs.length()))))) {
                        score += 15;
                        points.add("Aligné avec vos objectifs");
                    }

                    if (f.getNoteMoyenne() != null && f.getNoteMoyenne() >= 4.0f) {
                        score = Math.min(100, score + 5);
                        points.add("Très bien noté (" + f.getNoteMoyenne() + "/5)");
                    }

                    String raisonStr = points.isEmpty()
                            ? "Formation disponible dans notre catalogue"
                            : "Cette formation " + (points.size() > 1
                            ? "correspond à plusieurs de vos critères"
                            : points.get(0).toLowerCase());

                    return RecommandationIA.builder()
                            .formationId(f.getFormationId()).titre(f.getTitre())
                            .niveau(f.getNiveau()).description(f.getDescription())
                            .imageCouverture(f.getImageCouverture()).categorie(f.getCategorie())
                            .dureeEstimee(f.getDureeEstimee()).noteMoyenne(f.getNoteMoyenne())
                            .nombreInscrits(f.getNombreInscrits())
                            .scoreCompatibilite(Math.min(100, score))
                            .raison(raisonStr).pointsForts(String.join(", ", points)).build();
                })
                .sorted(Comparator.comparingInt(RecommandationIA::getScoreCompatibilite).reversed())
                .limit(TOP_N).collect(Collectors.toList());
    }

    private boolean estNiveauCompatible(String niveauApprenant, String niveauFormation) {
        Map<String, Integer> ordre = Map.of("DEBUTANT", 1, "INTERMEDIAIRE", 2, "AVANCE", 3);
        int a = ordre.getOrDefault(niveauApprenant, 1);
        int f = ordre.getOrDefault(niveauFormation, 1);
        return f <= a + 1;
    }

    private String niveauLabel(String niveau) {
        return switch (niveau) {
            case "DEBUTANT"      -> "Débutant";
            case "INTERMEDIAIRE" -> "Intermédiaire";
            case "AVANCE"        -> "Avancé";
            default -> niveau;
        };
    }
}
