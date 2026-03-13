package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Question;

import java.util.List;

public interface IAQuizPort {
    /**
     * Génère une liste de questions QCM via l'IA (Groq / simulation).
     *
     * @param coursTitre          Titre du cours
     * @param coursDescription    Description du cours
     * @param coursObjectifs      Objectifs du cours
     * @param coursId             ID du cours (pour extraire les PDF)
     * @param videoUrl            URL YouTube (ou null)
     * @param videoType           "YOUTUBE", "LOCAL" ou null
     * @param nombreQuestions     Nombre de questions à générer (3-10)
     * @param difficulte          "FACILE", "MOYEN" ou "DIFFICILE"
     * @param inclureDefinitions  Inclure des questions sur les définitions
     * @param inclureCasPratiques Inclure des questions sur les cas pratiques
     * @return Liste de questions (avec leurs options)
     */
    List<Question> genererQuestions(
            String coursTitre,
            String coursDescription,
            String coursObjectifs,
            Long coursId,
            String videoUrl,
            String videoType,
            int nombreQuestions,
            String difficulte,
            boolean inclureDefinitions,
            boolean inclureCasPratiques
    );
}
