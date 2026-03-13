package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Quiz;

import java.util.Map;
import java.util.Optional;

public interface QuizFormateurUseCase {
    // ── Lecture ─────────────────────────────────────────────────
    Optional<Quiz> getMiniQuiz(Long formationId, Long coursId, String email);

    Map<String, Object> getContexte(Long formationId, Long coursId, String email);

    // ── Génération IA ────────────────────────────────────────────
    Quiz genererQuizIA(Long formationId, Long coursId, String email,
                       int nombreQuestions, String difficulte,
                       boolean inclureDefinitions, boolean inclureCasPratiques,
                       float notePassage, int nombreTentatives);

    // ── Édition questions ────────────────────────────────────────
    Quiz updateQuestion(Long formationId, Long coursId, Long questionId,
                        String email, String texte, String explication);

    Quiz updateOption(Long formationId, Long coursId, Long questionId,
                      Long optionId, String email, String texte);

    Quiz setBonneReponse(Long formationId, Long coursId, Long questionId,
                         Long optionId, String email);

    Quiz addQuestion(Long formationId, Long coursId, String email,
                     String texte, String explication,
                     java.util.List<Map<String, Object>> options);

    Quiz deleteQuestion(Long formationId, Long coursId, Long questionId, String email);

    // ── Suppression quiz ─────────────────────────────────────────
    void deleteMiniQuiz(Long formationId, Long coursId, String email);
}
