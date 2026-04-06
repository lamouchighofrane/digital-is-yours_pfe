package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.ReponsesForum;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReponseForumRepositoryPort {

    ReponsesForum save(ReponsesForum reponse);
    Optional<ReponsesForum> findById(Long id);
    List<ReponsesForum> findByQuestionId(Long questionId);
    boolean isAuteur(Long reponseId, Long userId);
    boolean isAuteurQuestion(Long questionId, Long userId);
    boolean isFormateurDeLaFormation(Long questionId, String email);
    void marquerToutesNonSolution(Long questionId);
    void updateStatutQuestion(Long questionId, String statut);
    long countByQuestionId(Long questionId);

    // ── NOUVEAU : like sur réponse ─────────────────────────────────
    void toggleLikeReponse(Long reponseId, Long userId);
    boolean aLikeReponse(Long reponseId, Long userId);
    long countLikesReponse(Long reponseId);

    // ── NOUVEAU : document joint ───────────────────────────────────
    void saveDocument(Long reponseId, String nomFichier, String url,
                      String typeFichier, Long taille);

    // ── NOUVEAU : réactions emoji ──────────────────────────────────
    void toggleReaction(Long reponseId, Long userId, String emoji);
    Map<String, Long> getReactionCounts(Long reponseId);
    List<String> getMesReactions(Long reponseId, Long userId);

    // ── NOUVEAU : is typing ────────────────────────────────────────
    void setTyping(Long questionId, Long formateurId);
    boolean isTyping(Long questionId);
}