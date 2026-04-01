package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.ReponsesForum;
import java.util.List;
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
}