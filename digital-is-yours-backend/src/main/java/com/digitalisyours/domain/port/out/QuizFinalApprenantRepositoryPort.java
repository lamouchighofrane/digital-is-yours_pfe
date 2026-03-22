package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.InfosQuizFinalApprenant;
import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.model.ResultatQuizFinal;

import java.util.Optional;

public interface QuizFinalApprenantRepositoryPort {

    boolean estInscritEtPaye(String email, Long formationId);

    Optional<Quiz> findQuizFinalByFormationId(Long formationId);

    long countTentatives(String email, Long quizId);

    Optional<InfosQuizFinalApprenant.DernierResultat> findDernierResultat(String email, Long quizId);

    ResultatQuizFinal saveResultat(ResultatQuizFinal resultat);

    /**
     * Retrouve l'ID de l'apprenant depuis son email.
     * Nécessaire pour appeler CertificatUseCase.genererCertificat(apprenantId, ...).
     */
    Long findApprenantIdByEmail(String email);
}