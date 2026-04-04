package com.digitalisyours.domain.port.out;


import com.digitalisyours.domain.model.QuestionForum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface ForumRepositoryPort {
    Page<QuestionForum> findAll(String search, Long formationId,
                                String statut, Pageable pageable, Long userId);
    Optional<QuestionForum> findById(Long id, Long userId);
    QuestionForum save(QuestionForum question);
    void deleteById(Long id);
    boolean existsById(Long id);
    boolean isAuteur(Long questionId, Long userId);
    void incrementerVues(Long questionId);

    /** Enregistre une vue si l'utilisateur n'a pas encore vu cette question */
    boolean enregistrerVueSiNouvelle(Long questionId, Long userId);

    void toggleLike(Long questionId, Long userId);
    boolean aLike(Long questionId, Long userId);
    long countLikes(Long questionId);
    long countByAuteurEmail(String email);
    long countByAuteurEmailAndStatut(String email, String statut);
    List<QuestionForum> findTopByNombreLikes(int limit);
    List<String> findTopContributeurs(int limit);

    // Formateur
    Page<QuestionForum> findAllByFormateur(String search, Long formationId,
                                           String statut, Pageable pageable,
                                           Long formateurId);
    long countNonReponduesByFormateur(Long formateurId);
}