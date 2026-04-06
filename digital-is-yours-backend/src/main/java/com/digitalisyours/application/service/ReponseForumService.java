package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.ReponsesForum;
import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.port.in.ReponseForumUseCase;
import com.digitalisyours.domain.port.out.ReponseForumRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReponseForumService implements ReponseForumUseCase {

    private final ReponseForumRepositoryPort reponseRepository;
    private final UserJpaRepository          userRepo;
    private final QuestionForumJpaRepository questionRepo;
    private final NotificationJpaRepository  notifRepo;

    // ─────────────────────────────────────────────────────────────────────
    // Répondre à une question (formateur) — INCHANGÉ
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public ReponsesForum repondre(Long questionId, String email, String contenu) {
        if (contenu == null || contenu.isBlank())
            throw new RuntimeException("Le contenu est obligatoire.");
        if (contenu.trim().length() < 10)
            throw new RuntimeException("La réponse doit faire au moins 10 caractères.");

        UserEntity formateur = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        QuestionForumEntity question = questionRepo.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question introuvable"));

        ReponsesForum reponse = ReponsesForum.builder()
                .contenu(contenu.trim())
                .auteurId(formateur.getId())
                .questionId(questionId)
                .build();

        ReponsesForum saved = reponseRepository.save(reponse);

        // Mettre à jour le statut : NON_REPONDU → REPONDU
        if (!"RESOLU".equals(question.getStatut())) {
            reponseRepository.updateStatutQuestion(questionId, "REPONDU");
        }

        // Notification à l'apprenant auteur de la question
        try {
            UserEntity auteurQuestion = userRepo.findById(question.getAuteur().getId())
                    .orElse(null);
            if (auteurQuestion != null && !auteurQuestion.getId().equals(formateur.getId())) {
                String nomFormateur = formateur.getPrenom() + " " + formateur.getNom();
                String nomFormation = question.getFormation() != null
                        ? question.getFormation().getTitre() : "";
                envoyerNotification(
                        auteurQuestion,
                        "REPONSE_FORUM",
                        "Votre question a reçu une réponse",
                        nomFormateur + " a répondu à votre question : \"" +
                                question.getTitre() + "\"",
                        question.getFormation() != null
                                ? question.getFormation().getId() : null,
                        nomFormation
                );
            }
        } catch (Exception e) {
            log.warn("Erreur notification réponse : {}", e.getMessage());
        }

        log.info("Réponse ajoutée à la question #{} par {}", questionId, email);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU : Répondre avec fichier joint
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public ReponsesForum repondreAvecFichier(Long questionId, String email,
                                             String contenu, MultipartFile fichier,
                                             String uploadDir) {
        // 1. Créer la réponse texte normalement
        ReponsesForum saved = repondre(questionId, email, contenu);

        // 2. Si un fichier est fourni, le sauvegarder
        if (fichier != null && !fichier.isEmpty()) {
            try {
                // Valider le type
                String contentType = fichier.getContentType() != null
                        ? fichier.getContentType() : "";
                List<String> allowed = List.of(
                        "application/pdf",
                        "image/jpeg", "image/png", "image/gif", "image/webp"
                );
                if (!allowed.contains(contentType)) {
                    log.warn("Type de fichier non supporté : {}", contentType);
                    return saved; // on retourne quand même la réponse
                }

                // Limiter à 10 Mo
                if (fichier.getSize() > 10L * 1024 * 1024) {
                    log.warn("Fichier trop volumineux : {} octets", fichier.getSize());
                    return saved;
                }

                // Déterminer l'extension
                String origName = fichier.getOriginalFilename() != null
                        ? fichier.getOriginalFilename() : "fichier";
                String ext = "";
                if (origName.contains(".")) {
                    ext = origName.substring(origName.lastIndexOf("."));
                }

                // Créer le répertoire et sauvegarder
                String uuid     = UUID.randomUUID().toString();
                String filename = uuid + ext;
                Path   dir      = Paths.get(uploadDir, "forum", String.valueOf(saved.getId()));
                Files.createDirectories(dir);
                Files.write(dir.resolve(filename), fichier.getBytes());

                // Enregistrer en base
                reponseRepository.saveDocument(
                        saved.getId(),
                        origName,
                        filename,
                        contentType,
                        fichier.getSize()
                );

                log.info("Fichier joint à la réponse #{} : {}", saved.getId(), origName);

            } catch (IOException e) {
                log.warn("Impossible de sauvegarder le fichier : {}", e.getMessage());
            }
        }

        return reponseRepository.findById(saved.getId()).orElse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Marquer une réponse comme solution — INCHANGÉ
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public ReponsesForum marquerSolution(Long questionId, Long reponseId, String email) {
        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        boolean isFormateur = reponseRepository.isFormateurDeLaFormation(questionId, email);
        boolean isAuteurQ   = reponseRepository.isAuteurQuestion(questionId, user.getId());

        if (!isFormateur && !isAuteurQ)
            throw new SecurityException("Action non autorisée.");

        reponseRepository.marquerToutesNonSolution(questionId);

        ReponsesForum reponse = reponseRepository.findById(reponseId)
                .orElseThrow(() -> new RuntimeException("Réponse introuvable"));
        reponse.setEstSolution(true);
        ReponsesForum updated = reponseRepository.save(reponse);

        reponseRepository.updateStatutQuestion(questionId, "RESOLU");

        log.info("Réponse #{} marquée solution par {}", reponseId, email);
        return updated;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lister les réponses — INCHANGÉ
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public List<ReponsesForum> getReponses(Long questionId, String email) {
        return reponseRepository.findByQuestionId(questionId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU : Like sur réponse
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public Map<String, Object> toggleLikeReponse(Long reponseId, String email) {
        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        reponseRepository.toggleLikeReponse(reponseId, user.getId());

        long    nbLikes  = reponseRepository.countLikesReponse(reponseId);
        boolean likeParMoi = reponseRepository.aLikeReponse(reponseId, user.getId());

        return Map.of(
                "nombreLikes", nbLikes,
                "likeParMoi",  likeParMoi
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU : Réactions emoji
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public Map<String, Object> toggleReaction(Long reponseId, String email, String emoji) {
        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        reponseRepository.toggleReaction(reponseId, user.getId(), emoji);

        Map<String, Long> counts      = reponseRepository.getReactionCounts(reponseId);
        List<String>      mesReactions = reponseRepository.getMesReactions(reponseId, user.getId());

        return Map.of(
                "counts",      counts,
                "mesReactions", mesReactions
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOUVEAU : Is typing
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void setTyping(Long questionId, String email) {
        UserEntity user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return;
        reponseRepository.setTyping(questionId, user.getId());
    }

    @Override
    public boolean isTyping(Long questionId) {
        return reponseRepository.isTyping(questionId);
    }

    // ── Helper notification ───────────────────────────────────────────────
    private void envoyerNotification(UserEntity destinataire, String type,
                                     String titre, String message,
                                     Long formationId, String formationTitre) {
        try {
            NotificationEntity notif = NotificationEntity.builder()
                    .user(destinataire)
                    .type(type)
                    .titre(titre)
                    .message(message)
                    .formationId(formationId)
                    .formationTitre(formationTitre)
                    .build();
            notifRepo.save(notif);
        } catch (Exception e) {
            log.warn("Impossible d'envoyer la notification : {}", e.getMessage());
        }
    }
}