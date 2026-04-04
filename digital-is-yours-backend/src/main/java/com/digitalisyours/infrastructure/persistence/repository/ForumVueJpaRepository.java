package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.ForumVueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ForumVueJpaRepository extends JpaRepository<ForumVueEntity, Long> {

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);
}
