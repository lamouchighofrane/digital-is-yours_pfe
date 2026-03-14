package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.OptionQuestion;
import com.digitalisyours.domain.model.Question;
import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.port.in.QuizFinalFormationUseCase;
import com.digitalisyours.domain.port.out.IAQuizPort;
import com.digitalisyours.domain.port.out.QuizFinalFormationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizFinalFormationService implements QuizFinalFormationUseCase {
    private final QuizFinalFormationRepositoryPort quizFinalRepository;
    private final IAQuizPort iaQuizPort;

    // ══════════════════════════════════════════════════════════
    // LECTURE
    // ══════════════════════════════════════════════════════════

    @Override
    public Optional<Quiz> getQuizFinal(Long formationId, String email) {
        checkAcces(formationId, email);
        return quizFinalRepository.findByFormationId(formationId);
    }

    @Override
    public Map<String, Object> getContexteFormation(Long formationId, String email) {
        checkAcces(formationId, email);
        return quizFinalRepository.getFormationInfos(formationId);
    }

    // ══════════════════════════════════════════════════════════
    // GÉNÉRATION IA — Quiz Final
    // ══════════════════════════════════════════════════════════

    @Override
    public Quiz genererQuizFinalIA(Long formationId, String email,
                                   int nombreQuestions,
                                   String difficulte,
                                   boolean inclureDefinitions,
                                   boolean inclureCasPratiques,
                                   float notePassage,
                                   int nombreTentatives,
                                   int dureeMinutes) {
        checkAcces(formationId, email);

        // Récupérer les infos de la formation
        Map<String, Object> formationInfos = quizFinalRepository.getFormationInfos(formationId);
        String formationTitre       = (String) formationInfos.get("titre");
        String formationDescription = (String) formationInfos.get("description");
        String formationObjectifs   = (String) formationInfos.get("objectifsApprentissage");

        // Quiz Final : on passe formationId et coursId = null
        List<Question> questionsIA = iaQuizPort.genererQuestions(
                formationTitre,
                formationDescription,
                formationObjectifs,
                null,         // ← pas de cours (c'est un Quiz Final)
                formationId,  // ← ID de la formation
                null,         // videoUrl : non applicable au niveau formation
                null,         // videoType : non applicable au niveau formation
                nombreQuestions,
                difficulte,
                inclureDefinitions,
                inclureCasPratiques
        );

        if (questionsIA == null || questionsIA.isEmpty()) {
            throw new RuntimeException("Échec de la génération IA : aucune question générée.");
        }

        Quiz quiz = Quiz.builder()
                .type("QuizFinal")
                .notePassage(notePassage)
                .nombreTentatives(nombreTentatives)
                .genereParIA(true)
                .niveauDifficulte(difficulte)
                .inclureDefinitions(inclureDefinitions)
                .inclureCasPratiques(inclureCasPratiques)
                .dateCreation(LocalDateTime.now())
                .formationId(formationId)
                .dureeMinutes(dureeMinutes)
                .questions(questionsIA)
                .build();

        Quiz saved = quizFinalRepository.save(quiz);
        log.info("Quiz Final IA généré : {} questions pour la formation {}", questionsIA.size(), formationId);
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // ÉDITION QUESTIONS
    // ══════════════════════════════════════════════════════════

    @Override
    public Quiz updateQuestion(Long formationId, Long questionId,
                               String email, String texte, String explication) {
        checkAcces(formationId, email);
        Quiz quiz = getQuizOrThrow(formationId);
        Question q = findQuestionOrThrow(quiz, questionId);
        if (texte == null || texte.isBlank())
            throw new RuntimeException("Le texte de la question est obligatoire.");
        q.setTexte(texte.trim());
        if (explication != null) q.setExplication(explication.trim());
        return quizFinalRepository.save(quiz);
    }

    @Override
    public Quiz updateOption(Long formationId, Long questionId,
                             Long optionId, String email, String texte) {
        checkAcces(formationId, email);
        Quiz quiz = getQuizOrThrow(formationId);
        Question q = findQuestionOrThrow(quiz, questionId);
        OptionQuestion opt = findOptionOrThrow(q, optionId);
        if (texte == null || texte.isBlank())
            throw new RuntimeException("Le texte de l'option est obligatoire.");
        opt.setTexte(texte.trim());
        return quizFinalRepository.save(quiz);
    }

    @Override
    public Quiz setBonneReponse(Long formationId, Long questionId,
                                Long optionId, String email) {
        checkAcces(formationId, email);
        Quiz quiz = getQuizOrThrow(formationId);
        Question q = findQuestionOrThrow(quiz, questionId);
        boolean optExiste = q.getOptions().stream().anyMatch(o -> o.getId().equals(optionId));
        if (!optExiste) throw new RuntimeException("Option introuvable.");
        q.getOptions().forEach(o -> o.setEstCorrecte(o.getId().equals(optionId)));
        return quizFinalRepository.save(quiz);
    }

    @Override
    public Quiz addQuestion(Long formationId, String email,
                            String texte, String explication,
                            List<Map<String, Object>> optionsPayload) {
        checkAcces(formationId, email);
        Quiz quiz = getQuizOrThrow(formationId);

        if (texte == null || texte.isBlank())
            throw new RuntimeException("Le texte de la question est obligatoire.");
        if (optionsPayload == null || optionsPayload.size() < 2)
            throw new RuntimeException("Au moins 2 options sont requises.");

        long nbCorrects = optionsPayload.stream()
                .filter(o -> Boolean.TRUE.equals(o.get("estCorrecte"))).count();
        if (nbCorrects != 1)
            throw new RuntimeException("Exactement 1 bonne réponse est requise.");

        int nextOrdre = quiz.getQuestions().stream()
                .mapToInt(q -> q.getOrdre() != null ? q.getOrdre() : 0).max().orElse(0) + 1;

        String[] letters = {"A", "B", "C", "D"};
        List<OptionQuestion> options = new ArrayList<>();
        for (int i = 0; i < optionsPayload.size(); i++) {
            Map<String, Object> om = optionsPayload.get(i);
            String ordre = om.get("ordre") != null ? om.get("ordre").toString()
                    : (i < letters.length ? letters[i] : String.valueOf((char) ('A' + i)));
            options.add(OptionQuestion.builder()
                    .texte(om.get("texte").toString().trim())
                    .estCorrecte(Boolean.TRUE.equals(om.get("estCorrecte")))
                    .ordre(ordre)
                    .build());
        }

        Question newQ = Question.builder()
                .texte(texte.trim())
                .explication(explication != null ? explication.trim() : null)
                .ordre(nextOrdre)
                .genereParIA(false)
                .options(options)
                .build();

        quiz.getQuestions().add(newQ);
        return quizFinalRepository.save(quiz);
    }

    @Override
    public Quiz deleteQuestion(Long formationId, Long questionId, String email) {
        checkAcces(formationId, email);
        Quiz quiz = getQuizOrThrow(formationId);
        boolean removed = quiz.getQuestions().removeIf(q -> q.getId().equals(questionId));
        if (!removed) throw new RuntimeException("Question non trouvée.");
        for (int i = 0; i < quiz.getQuestions().size(); i++)
            quiz.getQuestions().get(i).setOrdre(i + 1);
        return quizFinalRepository.save(quiz);
    }

    // ══════════════════════════════════════════════════════════
    // SUPPRESSION
    // ══════════════════════════════════════════════════════════

    @Override
    public void deleteQuizFinal(Long formationId, String email) {
        checkAcces(formationId, email);
        if (!quizFinalRepository.existsByFormationId(formationId))
            throw new RuntimeException("Aucun quiz final trouvé pour cette formation.");
        quizFinalRepository.deleteByFormationId(formationId);
        log.info("Quiz Final supprimé pour la formation {}", formationId);
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════

    private void checkAcces(Long formationId, String email) {
        if (!quizFinalRepository.isFormateurOfFormation(formationId, email))
            throw new SecurityException("Accès interdit à cette formation.");
    }

    private Quiz getQuizOrThrow(Long formationId) {
        return quizFinalRepository.findByFormationId(formationId)
                .orElseThrow(() -> new RuntimeException("Aucun quiz final trouvé."));
    }

    private Question findQuestionOrThrow(Quiz quiz, Long questionId) {
        return quiz.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Question non trouvée."));
    }

    private OptionQuestion findOptionOrThrow(Question question, Long optionId) {
        return question.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Option non trouvée."));
    }
}
