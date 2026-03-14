package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Question;

import java.util.List;

public interface IAQuizPort {
    /**
     * Génère une liste de questions QCM via l'IA (Groq / simulation).
     *
     * Pour un MiniQuiz   : coursId = ID du cours,    formationId = null
     * Pour un Quiz Final : coursId = null,            formationId = ID de la formation
     *
     * @param coursTitre          Titre du cours ou de la formation
     * @param coursDescription    Description du cours ou de la formation
     * @param coursObjectifs      Objectifs du cours ou de la formation
     * @param coursId             ID du cours (MiniQuiz) — null si Quiz Final
     * @param formationId         ID de la formation (Quiz Final) — null si MiniQuiz
     * @param videoUrl            URL YouTube (ou null)
     * @param videoType           "YOUTUBE", "LOCAL" ou null
     * @param nombreQuestions     Nombre de questions à générer
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
            Long formationId,
            String videoUrl,
            String videoType,
            int nombreQuestions,
            String difficulte,
            boolean inclureDefinitions,
            boolean inclureCasPratiques
    );
}
