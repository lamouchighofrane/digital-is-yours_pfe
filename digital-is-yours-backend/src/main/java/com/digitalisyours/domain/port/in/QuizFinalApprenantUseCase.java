package com.digitalisyours.domain.port.in;
import com.digitalisyours.domain.model.InfosQuizFinalApprenant;
import com.digitalisyours.domain.model.ResultatQuizFinal;
import com.digitalisyours.domain.model.SoumissionQuizFinal;

public interface QuizFinalApprenantUseCase {

    /**
     * Récupère les informations du quiz final de la formation pour un apprenant.
     * Les options sont retournées SANS le flag estCorrecte (sécurité).
     *
     * @param email       email de l'apprenant (extrait du JWT)
     * @param formationId identifiant de la formation
     * @return            InfosQuizFinalApprenant contenant le quiz et les métadonnées
     * @throws SecurityException si l'apprenant n'est pas inscrit et n'a pas payé
     */
    InfosQuizFinalApprenant getInfosQuizFinal(String email, Long formationId);

    /**
     * Soumet les réponses de l'apprenant, corrige automatiquement
     * et retourne le résultat détaillé avec la correction complète.
     *
     * @param soumission données de la soumission (réponses + temps)
     * @return           résultat avec score, corrections et explications
     * @throws SecurityException si l'apprenant n'est pas inscrit ou tentatives épuisées
     * @throws RuntimeException  si le quiz n'existe pas
     */
    ResultatQuizFinal soumettre(SoumissionQuizFinal soumission);
}
