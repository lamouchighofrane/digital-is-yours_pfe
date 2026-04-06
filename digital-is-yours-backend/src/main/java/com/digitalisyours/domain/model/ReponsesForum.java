package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReponsesForum {
    private Long id;
    private String contenu;
    private Long auteurId;
    private String auteurPrenom;
    private String auteurNom;
    private String auteurPhoto;
    private String auteurRole;        // APPRENANT | FORMATEUR
    private boolean estSolution;
    private int nombreLikes;
    private boolean likeParMoi;
    private LocalDateTime dateCreation;
    private Long questionId;

    // ── NOUVEAU : documents joints ────────────────────────────────
    // Chaque map contient : {id, nomFichier, url, typeFichier, taille}
    private List<Map<String, Object>> documents;

    // ── NOUVEAU : réactions emoji ─────────────────────────────────
    // Ex: {"👍": 3, "❤️": 1, "🙏": 2}
    private Map<String, Long> reactionCounts;

    // Emojis sur lesquels l'utilisateur connecté a réagi
    private List<String> mesReactions;
}