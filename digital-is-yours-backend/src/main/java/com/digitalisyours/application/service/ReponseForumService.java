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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReponseForumService implements ReponseForumUseCase {

    private final ReponseForumRepositoryPort reponseRepository;
    private final UserJpaRepository          userRepo;
    private final QuestionForumJpaRepository questionRepo;
    private final NotificationJpaRepository  notifRepo;

    // ─────────────────────────────────────────────────────────────────────
    // Répondre à une question (formateur seulement)
    // Notification : l'apprenant auteur de la question reçoit une notif
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

        // Créer la réponse
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

        // ── Notification à l'apprenant auteur de la question ──────────────
        // ── Notification à l'apprenant auteur de la question ──────────────
// Refetch pour garantir que l'entité est bien chargée
        try {
            UserEntity auteurQuestion = userRepo.findById(question.getAuteur().getId())
                    .orElse(null);

            // NE PAS envoyer si c'est le formateur lui-même qui est auteur
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
    // Modifier une réponse (formateur — uniquement SA réponse)
    // ─────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public ReponsesForum modifierReponse(Long reponseId, String email, String contenu) {
        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (!reponseRepository.isAuteur(reponseId, user.getId()))
            throw new SecurityException("Vous ne pouvez modifier que vos propres réponses.");

        if (contenu == null || contenu.isBlank())
            throw new RuntimeException("Le contenu est obligatoire.");

        ReponsesForum existing = reponseRepository.findById(reponseId)
                .orElseThrow(() -> new RuntimeException("Réponse introuvable."));

        existing.setContenu(contenu.trim());
        return reponseRepository.save(existing);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Marquer une réponse comme solution
    // Seul le formateur peut marquer une solution
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
    // Lister les réponses d'une question
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public List<ReponsesForum> getReponses(Long questionId, String email) {
        return reponseRepository.findByQuestionId(questionId);
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