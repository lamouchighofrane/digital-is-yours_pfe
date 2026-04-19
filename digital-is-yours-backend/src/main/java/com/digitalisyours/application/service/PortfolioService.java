package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Apprenant;
import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.model.Competence;
import com.digitalisyours.domain.port.in.CertificatUseCase;
import com.digitalisyours.domain.port.in.CompetenceUseCase;
import com.digitalisyours.domain.port.in.ProfilApprenantUseCase;
import com.digitalisyours.infrastructure.persistence.entity.PortfolioEntity;
import com.digitalisyours.infrastructure.persistence.repository.PortfolioJpaRepository;
import com.digitalisyours.infrastructure.portfolio.GitHubPagesPublisher;
import com.digitalisyours.infrastructure.portfolio.PortfolioHtmlGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final PortfolioJpaRepository  portfolioRepository;
    private final PortfolioHtmlGenerator  htmlGenerator;
    private final GitHubPagesPublisher    githubPublisher;
    private final ProfilApprenantUseCase  profilUseCase;
    private final CertificatUseCase       certificatUseCase;
    private final CompetenceUseCase       competenceUseCase;

    // ──────────────────────────────────────────────────────────────────────
    // Point d'entrée principal — appelé après génération de certificat
    // ──────────────────────────────────────────────────────────────────────
    @Transactional
    public PortfolioEntity genererOuMettreAJour(Long apprenantId) {
        try {
            String email = trouverEmailParId(apprenantId);
            Apprenant apprenant = profilUseCase.getProfil(email);
            List<Certificat> certificats = certificatUseCase.getMesCertificats(apprenantId);

            // Charger les compétences pour chaque formation certifiée
            Map<Long, List<Competence>> competencesParFormation = new HashMap<>();
            for (Certificat cert : certificats) {
                if (cert.getFormationId() != null) {
                    try {
                        List<Competence> comps =
                                competenceUseCase.getCompetencesByFormation(cert.getFormationId());
                        if (comps != null && !comps.isEmpty()) {
                            competencesParFormation.put(cert.getFormationId(), comps);
                        }
                    } catch (Exception e) {
                        log.warn("Compétences introuvables formation {} : {}",
                                cert.getFormationId(), e.getMessage());
                    }
                }
            }

            // Récupérer ou créer l'entité portfolio
            PortfolioEntity portfolio = portfolioRepository
                    .findByApprenantId(apprenantId).orElse(null);

            String slug = portfolio != null
                    ? portfolio.getSlug()
                    : genererSlug(apprenant.getPrenom(), apprenant.getNom(), apprenantId);

            String portfolioUrl = "https://digitalisyours-portfolios.github.io/portfolios/"
                    + slug + ".html";

            // Générer le HTML
            String html = htmlGenerator.generer(
                    apprenant, certificats, competencesParFormation, portfolioUrl);

            // Publier — le publisher gère lui-même le SHA frais
            GitHubPagesPublisher.GitHubPublishResult result =
                    githubPublisher.publierOuMettreAJour(slug, html);

            if (!result.succes()) {
                log.error("Échec publication GitHub — apprenant={} : {}",
                        apprenantId, result.erreur());
            }

            // Sauvegarder en base
            LocalDateTime now = LocalDateTime.now();
            if (portfolio == null) {
                portfolio = PortfolioEntity.builder()
                        .apprenantId(apprenantId)
                        .apprenantEmail(email)
                        .slug(slug)
                        .urlGithubPages(result.succes() ? result.urlPublique() : portfolioUrl)
                        .githubFileSha(result.sha())
                        .dateCreation(now)
                        .derniereMiseAJour(now)
                        .nombreCertificats(certificats.size())
                        .estPublie(result.succes())
                        .build();
            } else {
                portfolio.setGithubFileSha(result.sha());
                portfolio.setDerniereMiseAJour(now);
                portfolio.setNombreCertificats(certificats.size());
                portfolio.setEstPublie(result.succes());
                if (result.succes()) {
                    portfolio.setUrlGithubPages(result.urlPublique());
                }
            }

            PortfolioEntity saved = portfolioRepository.save(portfolio);
            log.info("Portfolio {} — apprenant={} — URL: {}",
                    result.succes() ? "publié ✓" : "erreur ✗",
                    apprenantId, saved.getUrlGithubPages());

            return saved;

        } catch (Exception e) {
            log.error("Erreur génération portfolio apprenant={} : {}",
                    apprenantId, e.getMessage(), e);
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Régénération forcée — supprime l'ancien et recrée
    // ──────────────────────────────────────────────────────────────────────
    @Transactional
    public PortfolioEntity regenerer(Long apprenantId) {
        log.info("Régénération forcée portfolio apprenant={}", apprenantId);

        // Supprimer l'ancien fichier GitHub si présent
        PortfolioEntity existant = portfolioRepository
                .findByApprenantId(apprenantId).orElse(null);

        if (existant != null && existant.getSlug() != null) {
            // Récupérer le SHA frais avant suppression
            String shaFrais = githubPublisher.getShaFichierExistant(existant.getSlug());
            if (shaFrais != null) {
                boolean supprime = githubPublisher.supprimerFichier(existant.getSlug(), shaFrais);
                log.info("Ancien fichier GitHub {} : {}",
                        existant.getSlug(), supprime ? "supprimé ✓" : "erreur suppression");
            }
            // Supprimer l'entrée en base
            portfolioRepository.delete(existant);
            portfolioRepository.flush();
        }

        // Recréer complètement
        return genererOuMettreAJour(apprenantId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Lecture
    // ──────────────────────────────────────────────────────────────────────
    public PortfolioEntity getPortfolio(Long apprenantId) {
        return portfolioRepository.findByApprenantId(apprenantId).orElse(null);
    }

    public PortfolioEntity getPortfolioParSlug(String slug) {
        return portfolioRepository.findBySlug(slug).orElse(null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────
    private String genererSlug(String prenom, String nom, Long id) {
        String base = ((prenom != null ? prenom : "") + "-" + (nom != null ? nom : ""))
                .toLowerCase().trim();
        base = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return base + "-" + id;
    }

    private String trouverEmailParId(Long apprenantId) {
        List<Certificat> certs = certificatUseCase.getMesCertificats(apprenantId);
        if (!certs.isEmpty() && certs.get(0).getApprenantEmail() != null) {
            return certs.get(0).getApprenantEmail();
        }
        throw new RuntimeException("Email introuvable pour apprenant : " + apprenantId);
    }
}