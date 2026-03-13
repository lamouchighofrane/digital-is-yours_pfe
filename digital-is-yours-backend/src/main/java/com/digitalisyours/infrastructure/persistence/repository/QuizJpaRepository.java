package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface QuizJpaRepository extends JpaRepository<QuizEntity, Long> {
    // Charge uniquement les questions (pour save)
    @Query("SELECT DISTINCT q FROM QuizEntity q " +
            "LEFT JOIN FETCH q.questions " +
            "WHERE q.cours.id = :coursId")
    Optional<QuizEntity> findByCoursIdWithQuestions(@Param("coursId") Long coursId);

    // Charge questions + options (pour lecture complète)
    @Query("SELECT DISTINCT q FROM QuizEntity q " +
            "LEFT JOIN FETCH q.questions qs " +
            "LEFT JOIN FETCH qs.options " +
            "WHERE q.cours.id = :coursId")
    Optional<QuizEntity> findByCoursIdWithQuestionsAndOptions(@Param("coursId") Long coursId);

    boolean existsByCoursId(Long coursId);
    @Modifying
    @Transactional
    void deleteByCoursId(Long coursId);
}
