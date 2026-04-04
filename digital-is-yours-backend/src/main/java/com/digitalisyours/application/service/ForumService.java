package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.QuestionForum;
import com.digitalisyours.domain.port.in.ForumUseCase;
import com.digitalisyours.domain.port.out.ForumRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.NotificationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.NotificationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForumService implements ForumUseCase {

    private final ForumRepositoryPort       forumRepository;
    private final UserJpaRepository         userJpaRepository;
    private final FormationJpaRepository    formationRepo;
    private final NotificationJpaRepository notifRepo;

    // ── LECTURE ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionForum> getQuestions(String search, String formationId,
                                            String statut, Pageable pageable,
                                            String emailConnecte) {
        Long userId = getUserId(emailConnecte);
        Long fid    = parseId(formationId);
        String s    = parseStatut(statut);
        String q    = (search != null && !search.isBlank()) ? search : null;
        return forumRepository.findAll(q, fid, s, pageable, userId);
    }

    @Override
    @Transactional
    public QuestionForum getQuestionById(Long id, String emailConnecte) {
        Long userId = getUserId(emailConnecte);

        QuestionForum q = forumRepository.findById(id, userId)
                .orElseThrow(() -> new RuntimeException("Question introuvable : " + id));

        // Enregistrer la vue uniquement si :
        // 1. L'utilisateur est connecté (userId != null)
        // 2. Ce n'est PAS l'auteur de la question
        // 3. C'est la première fois que cet utilisateur consulte cette question
        //    (géré dans enregistrerVueSiNouvelle via la contrainte UNIQUE de forum_vues)
        if (userId != null && !userId.equals(q.getAuteurId())) {
            forumRepository.enregistrerVueSiNouvelle(id, userId);
        }

        return q;
    }

    // ── POSTER ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public QuestionForum poserQuestion(String email, String titre,
                                       String contenu, Long formationId,
                                       List<String> tags) {
        if (titre == null || titre.isBlank())
            throw new RuntimeException("Le titre est obligatoire.");
        if (contenu == null || contenu.isBlank())
            throw new RuntimeException("Le contenu est obligatoire.");
        if (titre.length() < 10)
            throw new RuntimeException("Le titre doit faire au moins 10 caractères.");

        Long userId = getUserId(email);
        QuestionForum q = QuestionForum.builder()
                .titre(titre.trim())
                .contenu(contenu.trim())
                .auteurId(userId)
                .formationId(formationId)
                .tags(tags)
                .statut("NON_REPONDU")
                .build();

        QuestionForum saved = forumRepository.save(q);
        log.info("Nouvelle question forum #{} par {}", saved.getId(), email);

        // ── Notifier le formateur de la formation ─────────────────────────
        if (formationId != null) {
            try {
                formationRepo.findById(formationId).ifPresent(formation -> {
                    if (formation.getFormateur() != null) {
                        UserEntity auteurUser = userJpaRepository.findByEmail(email).orElse(null);
                        String nomAuteur = auteurUser != null
                                ? auteurUser.getPrenom() + " " + auteurUser.getNom()
                                : "Un apprenant";

                        NotificationEntity notif = NotificationEntity.builder()
                                .user(formation.getFormateur())
                                .type("NOUVELLE_QUESTION_FORUM")
                                .titre("Nouvelle question dans votre formation")
                                .message(nomAuteur + " a posé une question dans \""
                                        + formation.getTitre() + "\" : \""
                                        + titre + "\"")
                                .formationId(formationId)
                                .formationTitre(formation.getTitre())
                                .build();
                        notifRepo.save(notif);
                    }
                });
            } catch (Exception e) {
                log.warn("Impossible d'envoyer la notification formateur : {}",
                        e.getMessage());
            }
        }

        return saved;
    }

    // ── MODIFIER ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public QuestionForum modifierQuestion(Long id, String email,
                                          String titre, String contenu,
                                          List<String> tags) {
        Long userId = getUserId(email);
        if (!forumRepository.isAuteur(id, userId))
            throw new SecurityException("Vous ne pouvez modifier que vos propres questions.");

        QuestionForum existing = forumRepository.findById(id, userId)
                .orElseThrow(() -> new RuntimeException("Question introuvable."));
        existing.setTitre(titre   != null ? titre.trim()   : existing.getTitre());
        existing.setContenu(contenu != null ? contenu.trim() : existing.getContenu());
        existing.setTags(tags != null ? tags : existing.getTags());

        return forumRepository.save(existing);
    }

    // ── SUPPRIMER ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void supprimerQuestion(Long id, String email) {
        Long userId = getUserId(email);
        if (!forumRepository.isAuteur(id, userId))
            throw new SecurityException("Vous ne pouvez supprimer que vos propres questions.");
        forumRepository.deleteById(id);
        log.info("Question forum #{} supprimée par {}", id, email);
    }

    // ── LIKE ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public QuestionForum likerQuestion(Long id, String email) {
        Long userId = getUserId(email);
        forumRepository.toggleLike(id, userId);
        return forumRepository.findById(id, userId)
                .orElseThrow(() -> new RuntimeException("Question introuvable."));
    }

    // ── STATS ────────────────────────────────────────────────────────────

    @Override
    public long countMesQuestions(String email) {
        return forumRepository.countByAuteurEmail(email);
    }

    @Override
    public long countMesQuestionsEnAttente(String email) {
        return forumRepository.countByAuteurEmailAndStatut(email, "NON_REPONDU");
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionForum> getQuestionsPopulaires() {
        return forumRepository.findTopByNombreLikes(5);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getContributeursActifs() {
        return forumRepository.findTopContributeurs(5);
    }

    // ── FORMATEUR ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionForum> getQuestionsFormateur(Long formateurId, String search,
                                                     String formationId, String statut,
                                                     Pageable pageable) {
        Long   fid = parseId(formationId);
        String s   = parseStatut(statut);
        String q   = (search != null && !search.isBlank()) ? search : null;
        return forumRepository.findAllByFormateur(q, fid, s, pageable, formateurId);
    }

    @Override
    public long countQuestionsNonRepondues(Long formateurId) {
        return forumRepository.countNonReponduesByFormateur(formateurId);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────

    private Long getUserId(String email) {
        if (email == null) return null;
        return userJpaRepository.findByEmail(email)
                .map(u -> u.getId())
                .orElse(null);
    }

    private Long parseId(String val) {
        if (val == null || val.isBlank() || "toutes".equalsIgnoreCase(val)) return null;
        try { return Long.valueOf(val); } catch (NumberFormatException e) { return null; }
    }

    private String parseStatut(String val) {
        if (val == null || val.isBlank() || "toutes".equalsIgnoreCase(val)) return null;
        return val;
    }
}