package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.ReponsesForum;
import com.digitalisyours.domain.port.out.ReponseForumRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.*;
import com.digitalisyours.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReponseForumRepositoryAdapter implements ReponseForumRepositoryPort {

    private final ReponsesForumJpaRepository reponseRepo;
    private final QuestionForumJpaRepository questionRepo;
    private final UserJpaRepository          userRepo;
    private final FormationJpaRepository     formationRepo;

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

        return toDomain(reponseRepo.save(entity));
    }

    @Override
    public Optional<ReponsesForum> findById(Long id) {
        return reponseRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<ReponsesForum> findByQuestionId(Long questionId) {
        return reponseRepo.findByQuestionIdOrderByDate(questionId)
                .stream().map(this::toDomain).collect(Collectors.toList());
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

    private ReponsesForum toDomain(ReponsesForumEntity r) {
        UserEntity a    = r.getAuteur();
        String     role = (a != null && a.getRole() != null)
                ? a.getRole().name() : "APPRENANT";
        return ReponsesForum.builder()
                .id(r.getId())
                .contenu(r.getContenu())
                .auteurId(a != null ? a.getId() : null)
                .auteurPrenom(a != null ? a.getPrenom() : "")
                .auteurNom(a != null ? a.getNom()    : "")
                .auteurPhoto(a != null ? a.getPhoto() : null)
                .auteurRole(role)
                .estSolution(r.isEstSolution())
                .nombreLikes(r.getLikes() != null ? r.getLikes().size() : 0)
                .dateCreation(r.getDateCreation())
                .questionId(r.getQuestion() != null ? r.getQuestion().getId() : null)
                .build();
    }
}