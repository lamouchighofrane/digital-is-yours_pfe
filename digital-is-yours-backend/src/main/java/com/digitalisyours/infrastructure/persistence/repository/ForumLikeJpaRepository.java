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

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ForumLikeEntity l WHERE l.user.id = :userId AND l.question.id = :questionId")
    void deleteByUserIdAndQuestionId(@Param("userId") Long userId, @Param("questionId") Long questionId);

    long countByQuestionId(Long questionId);
}