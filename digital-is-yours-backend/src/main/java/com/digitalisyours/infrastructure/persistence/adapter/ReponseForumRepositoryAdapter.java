package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.ReponsesForum;
import com.digitalisyours.domain.port.out.ReponseForumRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReponseForumRepositoryAdapter implements ReponseForumRepositoryPort {

    private final ReponsesForumJpaRepository     reponseRepo;
    private final QuestionForumJpaRepository     questionRepo;
    private final UserJpaRepository              userRepo;
    private final FormationJpaRepository         formationRepo;
    private final ForumLikeJpaRepository         likeRepo;

    // ── NOUVEAU : repositories injectés ───────────────────────────
    private final ReponseDocumentJpaRepository   docRepo;
    private final ReponseReactionJpaRepository   reactionRepo;

    /**
     * Map en mémoire pour le "is typing".
     * Clé = questionId, valeur = timestamp de la dernière frappe (ms).
     * Simple et efficace sans Redis pour un usage classique.
     */
    private static final Map<Long, Long> TYPING_MAP = new ConcurrentHashMap<>();
    private static final long TYPING_TIMEOUT_MS = 5000L; // 5 secondes

    // ════════════════════════════════════════════════════════════════
    // CRUD de base
    // ════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ReponsesForum save(ReponsesForum reponse) {
        ReponsesForumEntity entity;

        if (reponse.getId() != null) {
            // Modification
            entity = reponseRepo.findById(reponse.getId())
                    .orElseThrow(() -> new RuntimeException("Réponse introuvable"));
            entity.setContenu(reponse.getContenu());
            entity.setEstSolution(reponse.isEstSolution());
        } else {
            // Création
            UserEntity auteur = userRepo.findById(reponse.getAuteurId())
                    .orElseThrow(() -> new RuntimeException("Auteur introuvable"));
            QuestionForumEntity question = questionRepo.findById(reponse.getQuestionId())
                    .orElseThrow(() -> new RuntimeException("Question introuvable"));

            entity = ReponsesForumEntity.builder()
                    .contenu(reponse.getContenu())
                    .auteur(auteur)
                    .question(question)
                    .estSolution(false)
                    .build();
        }

        return toDomain(reponseRepo.save(entity), null);
    }

    @Override
    public Optional<ReponsesForum> findById(Long id) {
        return reponseRepo.findById(id).map(r -> toDomain(r, null));
    }

    @Override
    public List<ReponsesForum> findByQuestionId(Long questionId) {
        return reponseRepo.findByQuestionIdOrderByDate(questionId)
                .stream()
                .map(r -> toDomain(r, null))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAuteur(Long reponseId, Long userId) {
        return reponseRepo.findById(reponseId)
                .map(r -> r.getAuteur().getId().equals(userId))
                .orElse(false);
    }

    @Override
    public boolean isAuteurQuestion(Long questionId, Long userId) {
        return questionRepo.findById(questionId)
                .map(q -> q.getAuteur().getId().equals(userId))
                .orElse(false);
    }

    @Override
    public boolean isFormateurDeLaFormation(Long questionId, String email) {
        return questionRepo.findById(questionId)
                .map(q -> {
                    if (q.getFormation() == null) return false;
                    return formationRepo.findById(q.getFormation().getId())
                            .map(f -> f.getFormateur() != null &&
                                    f.getFormateur().getEmail().equals(email))
                            .orElse(false);
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void marquerToutesNonSolution(Long questionId) {
        reponseRepo.marquerToutesNonSolution(questionId);
    }

    @Override
    @Transactional
    public void updateStatutQuestion(Long questionId, String statut) {
        questionRepo.findById(questionId).ifPresent(q -> {
            q.setStatut(statut);
            questionRepo.save(q);
        });
    }

    @Override
    public long countByQuestionId(Long questionId) {
        return reponseRepo.countByQuestionId(questionId);
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : Like sur réponse
    // ════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void toggleLikeReponse(Long reponseId, Long userId) {
        if (likeRepo.existsByUserIdAndReponseId(userId, reponseId)) {
            likeRepo.deleteByUserIdAndReponseId(userId, reponseId);
        } else {
            UserEntity user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User introuvable"));
            ReponsesForumEntity rep = reponseRepo.findById(reponseId)
                    .orElseThrow(() -> new RuntimeException("Réponse introuvable"));
            ForumLikeEntity like = ForumLikeEntity.builder()
                    .user(user)
                    .reponse(rep)
                    .build();
            likeRepo.save(like);
        }
    }

    @Override
    public boolean aLikeReponse(Long reponseId, Long userId) {
        if (userId == null) return false;
        return likeRepo.existsByUserIdAndReponseId(userId, reponseId);
    }

    @Override
    public long countLikesReponse(Long reponseId) {
        return likeRepo.countByReponseId(reponseId);
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : Document joint
    // ════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void saveDocument(Long reponseId, String nomFichier, String url,
                             String typeFichier, Long taille) {
        ReponsesForumEntity rep = reponseRepo.findById(reponseId)
                .orElseThrow(() -> new RuntimeException("Réponse introuvable"));
        ReponseDocumentEntity doc = ReponseDocumentEntity.builder()
                .reponse(rep)
                .nomFichier(nomFichier)
                .url(url)
                .typeFichier(typeFichier)
                .taille(taille)
                .build();
        docRepo.save(doc);
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : Réactions emoji
    // ════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void toggleReaction(Long reponseId, Long userId, String emoji) {
        List<String> allowed = List.of("👍", "❤️", "🙏");
        if (!allowed.contains(emoji)) {
            throw new RuntimeException("Emoji non autorisé : " + emoji);
        }

        if (reactionRepo.existsByUserIdAndReponseIdAndEmoji(userId, reponseId, emoji)) {
            reactionRepo.deleteByUserIdAndReponseIdAndEmoji(userId, reponseId, emoji);
        } else {
            UserEntity user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User introuvable"));
            ReponsesForumEntity rep = reponseRepo.findById(reponseId)
                    .orElseThrow(() -> new RuntimeException("Réponse introuvable"));
            reactionRepo.save(ReponseReactionEntity.builder()
                    .user(user)
                    .reponse(rep)
                    .emoji(emoji)
                    .build());
        }
    }

    @Override
    public Map<String, Long> getReactionCounts(Long reponseId) {
        Map<String, Long> result = new LinkedHashMap<>();
        // Initialiser à 0 pour les 3 emojis supportés
        result.put("👍", 0L);
        result.put("❤️", 0L);
        result.put("🙏", 0L);
        reactionRepo.countByReponseIdGroupByEmoji(reponseId)
                .forEach(row -> result.put((String) row[0], (Long) row[1]));
        return result;
    }

    @Override
    public List<String> getMesReactions(Long reponseId, Long userId) {
        if (userId == null) return List.of();
        return reactionRepo.findByReponseId(reponseId).stream()
                .filter(r -> r.getUser().getId().equals(userId))
                .map(ReponseReactionEntity::getEmoji)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════
    // NOUVEAU : Is typing
    // ════════════════════════════════════════════════════════════════

    @Override
    public void setTyping(Long questionId, Long formateurId) {
        TYPING_MAP.put(questionId, System.currentTimeMillis());
    }

    @Override
    public boolean isTyping(Long questionId) {
        Long ts = TYPING_MAP.get(questionId);
        if (ts == null) return false;
        boolean still = (System.currentTimeMillis() - ts) < TYPING_TIMEOUT_MS;
        if (!still) TYPING_MAP.remove(questionId);
        return still;
    }

    // ════════════════════════════════════════════════════════════════
    // MAPPING : entity → domain
    // ════════════════════════════════════════════════════════════════

    /**
     * @param r      entité réponse
     * @param userId userId de l'utilisateur connecté (peut être null)
     */
    private ReponsesForum toDomain(ReponsesForumEntity r, Long userId) {
        UserEntity a    = r.getAuteur();
        String     role = (a != null && a.getRole() != null)
                ? a.getRole().name() : "APPRENANT";

        // Likes
        long    nbLikes  = likeRepo.countByReponseId(r.getId());
        boolean aLikeRep = userId != null &&
                likeRepo.existsByUserIdAndReponseId(userId, r.getId());

        // Documents joints
        List<Map<String, Object>> docs = docRepo.findByReponseId(r.getId())
                .stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",          d.getId());
                    m.put("nomFichier",  d.getNomFichier());
                    m.put("url",         d.getUrl());
                    m.put("typeFichier", d.getTypeFichier());
                    m.put("taille",      d.getTaille());
                    return m;
                })
                .collect(Collectors.toList());

        // Réactions
        Map<String, Long> reactionCounts = getReactionCounts(r.getId());
        List<String>      mesReactions   = getMesReactions(r.getId(), userId);

        return ReponsesForum.builder()
                .id(r.getId())
                .contenu(r.getContenu())
                .auteurId(a != null ? a.getId() : null)
                .auteurPrenom(a != null ? a.getPrenom() : "")
                .auteurNom(a != null ? a.getNom()    : "")
                .auteurPhoto(a != null ? a.getPhoto() : null)
                .auteurRole(role)
                .estSolution(r.isEstSolution())
                .nombreLikes((int) nbLikes)
                .likeParMoi(aLikeRep)
                .dateCreation(r.getDateCreation())
                .questionId(r.getQuestion() != null ? r.getQuestion().getId() : null)
                .documents(docs)
                .reactionCounts(reactionCounts)
                .mesReactions(mesReactions)
                .build();
    }
}