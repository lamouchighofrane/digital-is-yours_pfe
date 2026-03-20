package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.model.ResultatMiniQuiz;
import com.digitalisyours.domain.model.SoumissionMiniQuiz;

import java.util.Optional;

public interface MiniQuizApprenantUseCase {

    /**
     * Récupère le mini-quiz d'un cours pour un apprenant inscrit.
     * Les options sont retournées SANS le flag estCorrecte (sécurité).
     *
     * @param email       email de l'apprenant (extrait du JWT)
     * @param formationId identifiant de la formation
     * @param coursId     identifiant du cours
     * @return            Optional contenant le quiz s'il existe
     * @throws SecurityException si l'apprenant n'est pas inscrit
     */
    Optional<Quiz> getMiniQuiz(String email, Long formationId, Long coursId);

    /**
     * Soumet les réponses d'un apprenant, corrige automatiquement
     * et retourne le résultat détaillé.
     *
     * @param soumission données de la soumission
     * @return           résultat avec score, corrections et explications
     * @throws SecurityException si l'apprenant n'est pas inscrit
     * @throws RuntimeException  si le quiz n'existe pas
     */
    ResultatMiniQuiz soumettre(SoumissionMiniQuiz soumission);
}
