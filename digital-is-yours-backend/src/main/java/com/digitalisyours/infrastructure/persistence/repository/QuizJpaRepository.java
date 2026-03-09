package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizJpaRepository extends JpaRepository<QuizEntity, Long> {
    @Query("SELECT q FROM QuizEntity q LEFT JOIN FETCH q.questions qst LEFT JOIN FETCH qst.options WHERE q.cours.id = :coursId")
    Optional<QuizEntity> findByCoursIdWithQuestions(@Param("coursId") Long coursId);

    boolean existsByCoursId(Long coursId);

    void deleteByCoursId(Long coursId);
}
