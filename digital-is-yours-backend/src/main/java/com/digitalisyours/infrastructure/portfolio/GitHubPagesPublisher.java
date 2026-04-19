package com.digitalisyours.infrastructure.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class GitHubPagesPublisher {

    @Value("${github.token}")
    private String githubToken;

    @Value("${github.owner}")
    private String owner;

    @Value("${github.repo}")
    private String repo;

    @Value("${github.portfolios.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GITHUB_API  = "https://api.github.com";

    /**
     * Publie ou met à jour un fichier HTML.
     * Récupère TOUJOURS le SHA frais depuis GitHub avant d'écrire.
     */
    public GitHubPublishResult publierOuMettreAJour(String slug, String htmlContent) {

        String filePath = slug + ".html";
        String apiUrl   = GITHUB_API + "/repos/" + owner + "/" + repo
                + "/contents/" + filePath;

        // ── 1. Récupérer le SHA actuel (null si nouveau fichier) ──────
        String currentSha = getShaFichierExistant(slug);
        log.info("SHA actuel pour {} : {}", slug, currentSha != null ? currentSha : "NOUVEAU");

        // ── 2. Encoder le contenu en Base64 ───────────────────────────
        String contentBase64 = Base64.getEncoder()
                .encodeToString(htmlContent.getBytes(StandardCharsets.UTF_8));

        // ── 3. Construire le body ─────────────────────────────────────
        Map<String, Object> body = new HashMap<>();
        body.put("message", currentSha == null
                ? "✨ Création portfolio : " + slug
                : "🔄 Mise à jour portfolio : " + slug);
        body.put("content", contentBase64);
        body.put("branch", "main");

        if (currentSha != null) {
            body.put("sha", currentSha);
        }

        // ── 4. Appel API GitHub ───────────────────────────────────────
        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.PUT, request, Map.class);

            String newSha    = extractSha(response.getBody());
            String publicUrl = baseUrl + "/" + slug + ".html";

            log.info("Portfolio {} sur GitHub Pages → {}", slug, publicUrl);
            return new GitHubPublishResult(publicUrl, newSha, true, null);

        } catch (HttpClientErrorException e) {
            log.error("Erreur GitHub API [{}] slug={} : {}",
                    e.getStatusCode(), slug, e.getResponseBodyAsString());
            return new GitHubPublishResult(null, null, false,
                    "GitHub API error " + e.getStatusCode() + ": " + e.getMessage());
        } catch (Exception e) {
            log.error("Erreur inattendue publication portfolio {} : {}", slug, e.getMessage(), e);
            return new GitHubPublishResult(null, null, false, e.getMessage());
        }
    }

    /**
     * Récupère le SHA d'un fichier existant.
     * Retourne null si le fichier n'existe pas encore.
     */
    public String getShaFichierExistant(String slug) {
        String filePath = slug + ".html";
        String apiUrl   = GITHUB_API + "/repos/" + owner + "/" + repo
                + "/contents/" + filePath;
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, request, Map.class);
            String sha = extractShaDirect(response.getBody());
            log.debug("SHA trouvé pour {} : {}", slug, sha);
            return sha;
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("Fichier {} inexistant sur GitHub (nouveau portfolio)", slug);
            return null;
        } catch (Exception e) {
            log.warn("Impossible de récupérer SHA pour {} : {}", slug, e.getMessage());
            return null;
        }
    }

    /**
     * Supprime un fichier sur GitHub (utile pour régénération propre).
     */
    public boolean supprimerFichier(String slug, String sha) {
        if (sha == null || sha.isBlank()) {
            log.warn("Impossible de supprimer {} : SHA null", slug);
            return false;
        }
        String filePath = slug + ".html";
        String apiUrl   = GITHUB_API + "/repos/" + owner + "/" + repo
                + "/contents/" + filePath;

        Map<String, Object> body = new HashMap<>();
        body.put("message", "🗑️ Suppression portfolio : " + slug);
        body.put("sha", sha);
        body.put("branch", "main");

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(apiUrl, HttpMethod.DELETE, request, Map.class);
            log.info("Fichier {} supprimé de GitHub", slug);
            return true;
        } catch (Exception e) {
            log.error("Erreur suppression fichier {} : {}", slug, e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "token " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "DigitalIsYours-Portfolio-Publisher");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String extractSha(Map<?, ?> body) {
        if (body == null) return null;
        // Réponse PUT : { "content": { "sha": "..." } }
        Object content = body.get("content");
        if (content instanceof Map<?, ?> contentMap) {
            Object sha = contentMap.get("sha");
            return sha != null ? sha.toString() : null;
        }
        return null;
    }

    private String extractShaDirect(Map<?, ?> body) {
        if (body == null) return null;
        // Réponse GET : { "sha": "..." }
        Object sha = body.get("sha");
        return sha != null ? sha.toString() : null;
    }

    public record GitHubPublishResult(
            String urlPublique,
            String sha,
            boolean succes,
            String erreur
    ) {}
}