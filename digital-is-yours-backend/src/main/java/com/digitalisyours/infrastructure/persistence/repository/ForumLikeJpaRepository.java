package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.ForumLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface ForumLikeJpaRepository extends JpaRepository<ForumLikeEntity, Long> {

    // ── Like sur QUESTION (existant) ──────────────────────────────────────
    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ForumLikeEntity l WHERE l.user.id = :userId AND l.question.id = :questionId")
    void deleteByUserIdAndQuestionId(@Param("userId") Long userId,
                                     @Param("questionId") Long questionId);

    long countByQuestionId(Long questionId);

    // ── NOUVEAU : Like sur RÉPONSE ────────────────────────────────────────
    boolean existsByUserIdAndReponseId(Long userId, Long reponseId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ForumLikeEntity l WHERE l.user.id = :userId AND l.reponse.id = :reponseId")
    void deleteByUserIdAndReponseId(@Param("userId")    Long userId,
                                    @Param("reponseId") Long reponseId);

    @Query("SELECT COUNT(l) FROM ForumLikeEntity l WHERE l.reponse.id = :reponseId")
    long countByReponseId(@Param("reponseId") Long reponseId);
}