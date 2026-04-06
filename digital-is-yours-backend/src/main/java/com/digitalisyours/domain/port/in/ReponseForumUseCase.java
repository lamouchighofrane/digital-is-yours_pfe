package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.ReponsesForum;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ReponseForumUseCase {

    ReponsesForum repondre(Long questionId, String email, String contenu);

    // ── NOUVEAU : répondre avec fichier joint ──────────────────────
    ReponsesForum repondreAvecFichier(Long questionId, String email,
                                      String contenu, MultipartFile fichier,
                                      String uploadDir);

    ReponsesForum marquerSolution(Long questionId, Long reponseId, String email);
    List<ReponsesForum> getReponses(Long questionId, String email);

    // ── NOUVEAU : like sur réponse ─────────────────────────────────
    Map<String, Object> toggleLikeReponse(Long reponseId, String email);

    // ── NOUVEAU : réactions emoji ──────────────────────────────────
    Map<String, Object> toggleReaction(Long reponseId, String email, String emoji);

    // ── NOUVEAU : is typing ────────────────────────────────────────
    void setTyping(Long questionId, String email);
    boolean isTyping(Long questionId);
}