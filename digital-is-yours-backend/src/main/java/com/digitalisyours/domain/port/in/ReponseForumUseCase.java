package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.ReponsesForum;
import java.util.List;

public interface ReponseForumUseCase {
    ReponsesForum repondre(Long questionId, String email, String contenu);
    ReponsesForum modifierReponse(Long reponseId, String email, String contenu);
    ReponsesForum marquerSolution(Long questionId, Long reponseId, String email);
    List<ReponsesForum> getReponses(Long questionId, String email);
}