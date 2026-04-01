package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.QuestionForum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ForumUseCase {
    // Apprenant
    Page<QuestionForum> getQuestions(String search, String formationId,
                                     String statut, Pageable pageable, String emailConnecte);
    QuestionForum getQuestionById(Long id, String emailConnecte);
    QuestionForum poserQuestion(String email, String titre,
                                String contenu, Long formationId, List<String> tags);
    QuestionForum modifierQuestion(Long id, String email,
                                   String titre, String contenu, List<String> tags);
    void supprimerQuestion(Long id, String email);
    QuestionForum likerQuestion(Long id, String email);
    long countMesQuestions(String email);
    long countMesQuestionsEnAttente(String email);
    List<QuestionForum> getQuestionsPopulaires();
    List<String> getContributeursActifs();

    // Formateur
    Page<QuestionForum> getQuestionsFormateur(Long formateurId, String search,
                                              String formationId, String statut,
                                              Pageable pageable);
    long countQuestionsNonRepondues(Long formateurId);
}