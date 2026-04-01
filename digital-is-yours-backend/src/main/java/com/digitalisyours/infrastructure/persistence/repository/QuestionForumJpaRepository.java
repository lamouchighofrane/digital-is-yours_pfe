package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.QuestionForumEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface QuestionForumJpaRepository
        extends JpaRepository<QuestionForumEntity, Long> {

    // Apprenant — toutes les formations
    @Query("SELECT q FROM QuestionForumEntity q " +
            "LEFT JOIN FETCH q.auteur " +
            "LEFT JOIN FETCH q.formation " +
            "WHERE (:search IS NULL OR " +
            "       LOWER(q.titre)   LIKE LOWER(CONCAT('%',:search,'%')) OR " +
            "       LOWER(q.contenu) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:formationId IS NULL OR q.formation.id = :formationId) " +
            "AND (:statut IS NULL OR q.statut = :statut) " +
            "ORDER BY q.dateCreation DESC")
    Page<QuestionForumEntity> findWithFilters(
            @Param("search")      String search,
            @Param("formationId") Long   formationId,
            @Param("statut")      String statut,
            Pageable pageable);

    // Formateur — filtré par ses formations
    @Query("SELECT q FROM QuestionForumEntity q " +
            "LEFT JOIN FETCH q.auteur " +
            "LEFT JOIN FETCH q.formation f " +
            "WHERE f.formateur.id = :formateurId " +
            "AND (:search IS NULL OR " +
            "     LOWER(q.titre)   LIKE LOWER(CONCAT('%',:search,'%')) OR " +
            "     LOWER(q.contenu) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:formationId IS NULL OR f.id = :formationId) " +
            "AND (:statut IS NULL OR q.statut = :statut) " +
            "ORDER BY q.dateCreation DESC")
    Page<QuestionForumEntity> findByFormateur(
            @Param("formateurId") Long   formateurId,
            @Param("search")      String search,
            @Param("formationId") Long   formationId,
            @Param("statut")      String statut,
            Pageable pageable);

    @Query("SELECT COUNT(q) FROM QuestionForumEntity q " +
            "LEFT JOIN q.formation f " +
            "WHERE f.formateur.id = :formateurId " +
            "AND q.statut = 'NON_REPONDU'")
    long countNonReponduesByFormateur(@Param("formateurId") Long formateurId);

    @Modifying
    @Transactional
    @Query("UPDATE QuestionForumEntity q " +
            "SET q.nombreVues = q.nombreVues + 1 WHERE q.id = :id")
    void incrementerVues(@Param("id") Long id);

    @Query("SELECT q FROM QuestionForumEntity q " +
            "LEFT JOIN q.likes l " +
            "GROUP BY q.id " +
            "ORDER BY COUNT(l) DESC, q.dateCreation DESC")
    List<QuestionForumEntity> findTopByLikes(Pageable pageable);

    @Query("SELECT COUNT(q) FROM QuestionForumEntity q " +
            "WHERE q.auteur.email = :email")
    long countByAuteurEmail(@Param("email") String email);

    @Query("SELECT COUNT(q) FROM QuestionForumEntity q " +
            "WHERE q.auteur.email = :email AND q.statut = :statut")
    long countByAuteurEmailAndStatut(@Param("email") String email,
                                     @Param("statut") String statut);

    @Query("SELECT q.auteur.prenom, q.auteur.nom, COUNT(q) as cnt " +
            "FROM QuestionForumEntity q " +
            "GROUP BY q.auteur.id, q.auteur.prenom, q.auteur.nom " +
            "ORDER BY cnt DESC")
    List<Object[]> findTopContributeurs(Pageable pageable);
}