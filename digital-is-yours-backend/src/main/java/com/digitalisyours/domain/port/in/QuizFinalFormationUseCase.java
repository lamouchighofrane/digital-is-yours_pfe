package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Quiz;

import java.util.Map;
import java.util.Optional;

public interface QuizFinalFormationUseCase {
    // ── Lecture ─────────────────────────────────────────────
    Optional<Quiz> getQuizFinal(Long formationId, String email);

    Map<String, Object> getContexteFormation(Long formationId, String email);

    // ── Génération IA ────────────────────────────────────────
    Quiz genererQuizFinalIA(Long formationId, String email,
                            int nombreQuestions,
                            String difficulte,
                            boolean inclureDefinitions,
                            boolean inclureCasPratiques,
                            float notePassage,
                            int nombreTentatives,
                            int dureeMinutes);

    // ── Édition questions ────────────────────────────────────
    Quiz updateQuestion(Long formationId, Long questionId,
                        String email, String texte, String explication);

    Quiz updateOption(Long formationId, Long questionId,
                      Long optionId, String email, String texte);

    Quiz setBonneReponse(Long formationId, Long questionId,
                         Long optionId, String email);

    Quiz addQuestion(Long formationId, String email,
                     String texte, String explication,
                     java.util.List<Map<String, Object>> options);

    Quiz deleteQuestion(Long formationId, Long questionId, String email);

    // ── Suppression quiz ─────────────────────────────────────
    void deleteQuizFinal(Long formationId, String email);
}
