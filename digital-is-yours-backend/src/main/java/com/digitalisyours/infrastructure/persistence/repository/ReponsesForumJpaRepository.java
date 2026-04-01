package com.digitalisyours.infrastructure.persistence.repository;



import com.digitalisyours.infrastructure.persistence.entity.ReponsesForumEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface ReponsesForumJpaRepository
        extends JpaRepository<ReponsesForumEntity, Long> {

    @Query("SELECT r FROM ReponsesForumEntity r " +
            "LEFT JOIN FETCH r.auteur " +
            "WHERE r.question.id = :questionId " +
            "ORDER BY r.dateCreation ASC")
    List<ReponsesForumEntity> findByQuestionIdOrderByDate(
            @Param("questionId") Long questionId);

    @Modifying
    @Transactional
    @Query("UPDATE ReponsesForumEntity r " +
            "SET r.estSolution = false " +
            "WHERE r.question.id = :questionId")
    void marquerToutesNonSolution(@Param("questionId") Long questionId);

    long countByQuestionId(Long questionId);
}