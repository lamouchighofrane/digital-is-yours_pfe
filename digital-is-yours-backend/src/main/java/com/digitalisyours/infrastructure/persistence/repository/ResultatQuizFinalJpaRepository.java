package com.digitalisyours.infrastructure.persistence.repository;
import com.digitalisyours.infrastructure.persistence.entity.ResultatQuizFinalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResultatQuizFinalJpaRepository
        extends JpaRepository<ResultatQuizFinalEntity, Long> {

    /**
     * Compte le nombre de tentatives utilisées par un apprenant pour un quiz donné.
     */
    @Query("SELECT COUNT(r) FROM ResultatQuizFinalEntity r " +
            "WHERE r.apprenantEmail = :email AND r.quizId = :quizId")
    long countByApprenantEmailAndQuizId(
            @Param("email")  String email,
            @Param("quizId") Long   quizId);

    /**
     * Retourne le dernier résultat (le plus récent) d'un apprenant pour un quiz.
     */
    Optional<ResultatQuizFinalEntity>
    findTopByApprenantEmailAndQuizIdOrderByDatePassageDesc(
            String apprenantEmail, Long quizId);
    // ── NOUVEAU — pour dashboard infractions ─────────────────────
    @Query("SELECT COUNT(r) FROM ResultatQuizFinalEntity r WHERE r.suspectFraude = true")
    long countBySuspectFraudeTrue();

    @Query("SELECT COALESCE(SUM(r.nbInfractions), 0) FROM ResultatQuizFinalEntity r")
    long sumNbInfractions();

    @Query("SELECT r.id, r.apprenantEmail, " +
            "u.prenom, u.nom, " +
            "f.titre, " +
            "r.nbInfractions, r.scoreBrut, r.penaliteAppliquee, r.score, " +
            "r.datePassage, r.detailInfractions " +
            "FROM ResultatQuizFinalEntity r " +
            "LEFT JOIN ApprenantEntity a ON a.email = r.apprenantEmail " +
            "LEFT JOIN UserEntity u ON u.id = a.id " +
            "LEFT JOIN FormationEntity f ON f.id = r.formationId " +
            "WHERE r.suspectFraude = true " +
            "ORDER BY r.datePassage DESC")
    List<Object[]> findSuspectsWithDetails();
    // ── NOUVEAU — pour dashboard formateur ───────────────────────
    @Query("SELECT r.id, r.apprenantEmail, " +
            "u.prenom, u.nom, " +
            "f.id, f.titre, " +
            "r.nbInfractions, r.scoreBrut, r.penaliteAppliquee, r.score, " +
            "r.datePassage, r.detailInfractions " +
            "FROM ResultatQuizFinalEntity r " +
            "LEFT JOIN ApprenantEntity a ON a.email = r.apprenantEmail " +
            "LEFT JOIN UserEntity u ON u.id = a.id " +
            "LEFT JOIN FormationEntity f ON f.id = r.formationId " +
            "WHERE r.suspectFraude = true " +
            "AND r.formationId IN :formationIds " +
            "ORDER BY r.datePassage DESC")
    List<Object[]> findSuspectsForFormations(
            @Param("formationIds") List<Long> formationIds);
}
