package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.QuestionForum;
import com.digitalisyours.domain.model.ReponsesForum;
import com.digitalisyours.domain.port.out.ForumRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForumRepositoryAdapter implements ForumRepositoryPort {

    private final QuestionForumJpaRepository questionRepo;
    private final ForumLikeJpaRepository     likeRepo;
    private final ForumVueJpaRepository      vueRepo;
    private final UserJpaRepository          userRepo;
    private final FormationJpaRepository     formationRepo;

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── LECTURE PAGINÉE ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionForum> findAll(String search, Long formationId,
                                       String statut, Pageable pageable,
                                       Long userId) {
        Page<QuestionForumEntity> page = questionRepo.findWithFilters(
                search, formationId, statut, pageable);

        List<QuestionForum> list = page.getContent().stream()
                .map(e -> toDomain(e, userId))
                .collect(Collectors.toList());

        return new PageImpl<>(list, pageable, page.getTotalElements());
    }

    // ── LECTURE PAR ID ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<QuestionForum> findById(Long id, Long userId) {
        return questionRepo.findById(id).map(e -> {
            em.createQuery(
                            "SELECT DISTINCT r FROM ReponsesForumEntity r " +
                                    "LEFT JOIN FETCH r.auteur " +
                                    "WHERE r.question.id = :id " +
                                    "ORDER BY r.dateCreation ASC",
                            ReponsesForumEntity.class)
                    .setParameter("id", id)
                    .getResultList();
            return toDomainWithReponses(e, userId);
        });
    }

    // ── SAUVEGARDE ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public QuestionForum save(QuestionForum q) {
        QuestionForumEntity entity;

        if (q.getId() != null) {
            entity = questionRepo.findById(q.getId())
                    .orElseThrow(() -> new RuntimeException(
                            "Question introuvable : " + q.getId()));
            entity.setTitre(q.getTitre());
            entity.setContenu(q.getContenu());
            entity.setStatut(q.getStatut() != null ? q.getStatut() : entity.getStatut());
            entity.setTags(tagsToJson(q.getTags()));
        } else {
            UserEntity auteur = userRepo.findById(q.getAuteurId())
                    .orElseThrow(() -> new RuntimeException("Auteur introuvable"));
            FormationEntity formation = q.getFormationId() != null
                    ? formationRepo.findById(q.getFormationId()).orElse(null) : null;

            entity = QuestionForumEntity.builder()
                    .titre(q.getTitre())
                    .contenu(q.getContenu())
                    .auteur(auteur)
                    .formation(formation)
                    .statut("NON_REPONDU")
                    .tags(tagsToJson(q.getTags()))
                    .build();
        }

        QuestionForumEntity saved = questionRepo.save(entity);
        return toDomain(saved, q.getAuteurId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        questionRepo.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return questionRepo.existsById(id);
    }

    @Override
    public boolean isAuteur(Long questionId, Long userId) {
        return questionRepo.findById(questionId)
                .map(q -> q.getAuteur().getId().equals(userId))
                .orElse(false);
    }

    // ── VUES ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void incrementerVues(Long questionId) {
        questionRepo.incrementerVues(questionId);
    }

    /**
     * Enregistre une vue uniquement si cet utilisateur n'a jamais vu
     * cette question. Retourne true si la vue a été enregistrée (nouvelle vue),
     * false si l'utilisateur avait déjà vu la question.
     */
    @Override
    @Transactional
    public boolean enregistrerVueSiNouvelle(Long questionId, Long userId) {
        if (userId == null) return false;

        // Vérifier si l'utilisateur a déjà vu cette question
        if (vueRepo.existsByUserIdAndQuestionId(userId, questionId)) {
            return false; // déjà vu, on n'incrémente pas
        }

        // Enregistrer la vue
        UserEntity user = userRepo.findById(userId).orElse(null);
        QuestionForumEntity question = questionRepo.findById(questionId).orElse(null);

        if (user == null || question == null) return false;

        ForumVueEntity vue = ForumVueEntity.builder()
                .user(user)
                .question(question)
                .build();
        vueRepo.save(vue);

        // Incrémenter le compteur
        questionRepo.incrementerVues(questionId);
        return true;
    }

    // ── LIKES ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void toggleLike(Long questionId, Long userId) {
        if (likeRepo.existsByUserIdAndQuestionId(userId, questionId)) {
            likeRepo.deleteByUserIdAndQuestionId(userId, questionId);
        } else {
            UserEntity user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User introuvable"));
            QuestionForumEntity q = questionRepo.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question introuvable"));
            ForumLikeEntity like = ForumLikeEntity.builder()
                    .user(user).question(q).build();
            likeRepo.save(like);
        }
    }

    @Override
    public boolean aLike(Long questionId, Long userId) {
        return likeRepo.existsByUserIdAndQuestionId(userId, questionId);
    }

    @Override
    public long countLikes(Long questionId) {
        return likeRepo.countByQuestionId(questionId);
    }

    // ── STATS ────────────────────────────────────────────────────────────

    @Override
    public long countByAuteurEmail(String email) {
        return questionRepo.countByAuteurEmail(email);
    }

    @Override
    public long countByAuteurEmailAndStatut(String email, String statut) {
        return questionRepo.countByAuteurEmailAndStatut(email, statut);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionForum> findTopByNombreLikes(int limit) {
        return questionRepo.findTopByLikes(PageRequest.of(0, limit))
                .stream().map(e -> toDomain(e, null)).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findTopContributeurs(int limit) {
        return questionRepo.findTopContributeurs(PageRequest.of(0, limit))
                .stream()
                .map(row -> row[0] + " " + row[1])
                .collect(Collectors.toList());
    }

    // ── FORMATEUR ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionForum> findAllByFormateur(String search, Long formationId,
                                                  String statut, Pageable pageable,
                                                  Long formateurId) {
        Page<QuestionForumEntity> page = questionRepo.findByFormateur(
                formateurId, search, formationId, statut, pageable);

        List<QuestionForum> list = page.getContent().stream()
                .map(e -> toDomain(e, null))
                .collect(Collectors.toList());

        return new PageImpl<>(list, pageable, page.getTotalElements());
    }

    @Override
    public long countNonReponduesByFormateur(Long formateurId) {
        return questionRepo.countNonReponduesByFormateur(formateurId);
    }

    // ── MAPPING ───────────────────────────────────────────────────────────

    private QuestionForum toDomain(QuestionForumEntity e, Long userId) {
        UserEntity auteur = e.getAuteur();
        long    likes = likeRepo.countByQuestionId(e.getId());
        boolean aLike = userId != null &&
                likeRepo.existsByUserIdAndQuestionId(userId, e.getId());

        return QuestionForum.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .contenu(e.getContenu())
                .auteurId(auteur != null ? auteur.getId() : null)
                .auteurPrenom(auteur != null ? auteur.getPrenom() : "")
                .auteurNom(auteur != null ? auteur.getNom() : "")
                .auteurPhoto(auteur != null ? auteur.getPhoto() : null)
                .formationId(e.getFormation() != null ? e.getFormation().getId() : null)
                .formationTitre(e.getFormation() != null ? e.getFormation().getTitre() : null)
                .statut(e.getStatut())
                .nombreReponses(e.getReponses() != null ? e.getReponses().size() : 0)
                .nombreVues(e.getNombreVues())
                .nombreLikes((int) likes)
                .likeParMoi(aLike)
                .dateCreation(e.getDateCreation())
                .tags(parseTagsJson(e.getTags()))
                .build();
    }

    private QuestionForum toDomainWithReponses(QuestionForumEntity e, Long userId) {
        QuestionForum q = toDomain(e, userId);

        List<ReponsesForum> reponses = e.getReponses() == null
                ? new ArrayList<>()
                : e.getReponses().stream().map(r -> {
            UserEntity ra = r.getAuteur();
            return ReponsesForum.builder()
                    .id(r.getId())
                    .contenu(r.getContenu())
                    .auteurId(ra != null ? ra.getId() : null)
                    .auteurPrenom(ra != null ? ra.getPrenom() : "")
                    .auteurNom(ra != null ? ra.getNom() : "")
                    .auteurPhoto(ra != null ? ra.getPhoto() : null)
                    .auteurRole(ra != null && ra.getRole() != null
                            ? ra.getRole().name() : "APPRENANT")
                    .estSolution(r.isEstSolution())
                    .nombreLikes(r.getLikes() != null ? r.getLikes().size() : 0)
                    .dateCreation(r.getDateCreation())
                    .questionId(e.getId())
                    .build();
        }).collect(Collectors.toList());

        q.setReponses(reponses);
        return q;
    }

    private String tagsToJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "[]";
        try { return objectMapper.writeValueAsString(tags); }
        catch (Exception e) { return "[]"; }
    }

    private List<String> parseTagsJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }
}