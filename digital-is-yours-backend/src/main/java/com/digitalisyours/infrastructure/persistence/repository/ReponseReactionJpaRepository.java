package com.digitalisyours.infrastructure.persistence.repository;

import com.digitalisyours.infrastructure.persistence.entity.ReponseReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ReponseReactionJpaRepository extends JpaRepository<ReponseReactionEntity, Long> {

    List<ReponseReactionEntity> findByReponseId(Long reponseId);

    boolean existsByUserIdAndReponseIdAndEmoji(Long userId, Long reponseId, String emoji);

    @Modifying
    @Transactional
    @Query("DELETE FROM ReponseReactionEntity r " +
            "WHERE r.user.id = :userId AND r.reponse.id = :reponseId AND r.emoji = :emoji")
    void deleteByUserIdAndReponseIdAndEmoji(
            @Param("userId")    Long userId,
            @Param("reponseId") Long reponseId,
            @Param("emoji")     String emoji);

    @Query("SELECT r.emoji, COUNT(r) FROM ReponseReactionEntity r " +
            "WHERE r.reponse.id = :reponseId GROUP BY r.emoji")
    List<Object[]> countByReponseIdGroupByEmoji(@Param("reponseId") Long reponseId);
}