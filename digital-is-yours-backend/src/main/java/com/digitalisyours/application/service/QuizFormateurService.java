package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.OptionQuestion;
import com.digitalisyours.domain.model.Question;
import com.digitalisyours.domain.model.Quiz;
import com.digitalisyours.domain.port.in.QuizFormateurUseCase;
import com.digitalisyours.domain.port.out.IAQuizPort;
import com.digitalisyours.domain.port.out.QuizRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizFormateurService implements QuizFormateurUseCase {
    private final QuizRepositoryPort quizRepository;
    private final IAQuizPort iaQuizPort;

    // ══════════════════════════════════════════════════════════
    // LECTURE
    // ══════════════════════════════════════════════════════════

    @Override
    public Optional<Quiz> getMiniQuiz(Long formationId, Long coursId, String email) {
        checkAcces(formationId, email);
        checkCoursExists(coursId, formationId);
        return quizRepository.findByCoursId(coursId);
    }

    @Override
    public Map<String, Object> getContexte(Long formationId, Long coursId, String email) {
        checkAcces(formationId, email);
        checkCoursExists(coursId, formationId);

        Map<String, Object> cours = quizRepository.getCoursInfos(coursId);
        String videoType = (String) cours.get("videoType");

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("coursId",          coursId);
        ctx.put("coursTitre",       cours.get("titre"));
        ctx.put("coursDescription", cours.get("description"));
        ctx.put("coursObjectifs",   cours.get("objectifs"));
        ctx.put("videoType",        videoType != null ? videoType : "");
        ctx.put("videoUrl",         cours.get("videoUrl") != null ? cours.get("videoUrl") : "");
        ctx.put("hasVideo",         videoType != null);
        ctx.put("quizExistant",     quizRepository.existsByCoursId(coursId));
        ctx.put("documents",        List.of());
        return ctx;
    }

    // ══════════════════════════════════════════════════════════
    // GÉNÉRATION IA
    // ══════════════════════════════════════════════════════════

    @Override
    public Quiz genererQuizIA(Long formationId, Long coursId, String email,
                              int nombreQuestions, String difficulte,
                              boolean inclureDefinitions, boolean inclureCasPratiques,
                              float notePassage, int nombreTentatives) {

        checkAcces(formationId, email);
        checkCoursExists(coursId, formationId);

        // Lire les infos directement depuis le cours
        Map<String, Object> cours = quizRepository.getCoursInfos(coursId);
        String coursTitre       = (String) cours.get("titre");
        String coursDescription = (String) cours.get("description");
        String coursObjectifs   = (String) cours.get("objectifs");
        String videoUrl         = (String) cours.get("videoUrl");
        String videoType        = (String) cours.get("videoType");



        // Appel IA via le port
        List<Question> questionsIA = iaQuizPort.genererQuestions(
                coursTitre, coursDescription, coursObjectifs,
                coursId, videoUrl, videoType,
                nombreQuestions, difficulte,
                inclureDefinitions, inclureCasPratiques
        );

        if (questionsIA == null || questionsIA.isEmpty()) {
            throw new RuntimeException("Échec de la génération IA : aucune question générée.");
        }

        Quiz quiz = Quiz.builder()
                .type("MiniQuiz")
                .notePassage(notePassage)
                .nombreTentatives(nombreTentatives)
                .genereParIA(true)
                .niveauDifficulte(difficulte)
                .inclureDefinitions(inclureDefinitions)
                .inclureCasPratiques(inclureCasPratiques)
                .dateCreation(LocalDateTime.now())
                .coursId(coursId)
                .questions(questionsIA)
                .build();

        Quiz saved = quizRepository.save(quiz);
        log.info("Quiz IA généré : {} questions pour le cours {}", questionsIA.size(), coursId);
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // ÉDITION QUESTIONS
    // ══════════════════════════════════════════════════════════

    @Override
    public Quiz updateQuestion(Long formationId, Long coursId, Long questionId,
                               String email, String texte, String explication) {
        checkAcces(formationId, email);

        Quiz quiz = getQuizOrThrow(coursId);
        Question q = findQuestionOrThrow(quiz, questionId);

        if (texte == null || texte.isBlank())
            throw new RuntimeException("Le texte de la question est obligatoire.");

        q.setTexte(texte.trim());
        if (explication != null) q.setExplication(explication.trim());

        return quizRepository.save(quiz);
    }

    @Override
    public Quiz updateOption(Long formationId, Long coursId, Long questionId,
                             Long optionId, String email, String texte) {
        checkAcces(formationId, email);

        Quiz quiz = getQuizOrThrow(coursId);
        Question q = findQuestionOrThrow(quiz, questionId);
        OptionQuestion opt = findOptionOrThrow(q, optionId);

        if (texte == null || texte.isBlank())
            throw new RuntimeException("Le texte de l'option est obligatoire.");

        opt.setTexte(texte.trim());
        return quizRepository.save(quiz);
    }

    @Override
    public Quiz setBonneReponse(Long formationId, Long coursId, Long questionId,
                                Long optionId, String email) {
        checkAcces(formationId, email);

        Quiz quiz = getQuizOrThrow(coursId);
        Question q = findQuestionOrThrow(quiz, questionId);

        boolean optExiste = q.getOptions().stream().anyMatch(o -> o.getId().equals(optionId));
        if (!optExiste)
            throw new RuntimeException("Option introuvable pour cette question.");

        q.getOptions().forEach(o -> o.setEstCorrecte(o.getId().equals(optionId)));
        return quizRepository.save(quiz);
    }

    @Override
    public Quiz addQuestion(Long formationId, Long coursId, String email,
                            String texte, String explication,
                            List<Map<String, Object>> optionsPayload) {
        checkAcces(formationId, email);

        Quiz quiz = getQuizOrThrow(coursId);

        if (texte == null || texte.isBlank())
            throw new RuntimeException("Le texte de la question est obligatoire.");
        if (optionsPayload == null || optionsPayload.size() < 2)
            throw new RuntimeException("Au moins 2 options sont requises.");

        long nbCorrects = optionsPayload.stream()
                .filter(o -> Boolean.TRUE.equals(o.get("estCorrecte"))).count();
        if (nbCorrects != 1)
            throw new RuntimeException("Exactement 1 bonne réponse est requise.");

        for (Map<String, Object> o : optionsPayload) {
            String ot = (String) o.get("texte");
            if (ot == null || ot.isBlank())
                throw new RuntimeException("Toutes les options doivent avoir un texte.");
        }

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
        return quizRepository.save(quiz);
    }

    @Override
    public Quiz deleteQuestion(Long formationId, Long coursId, Long questionId, String email) {
        checkAcces(formationId, email);

        Quiz quiz = getQuizOrThrow(coursId);
        boolean removed = quiz.getQuestions().removeIf(q -> q.getId().equals(questionId));
        if (!removed) throw new RuntimeException("Question non trouvée.");

        for (int i = 0; i < quiz.getQuestions().size(); i++)
            quiz.getQuestions().get(i).setOrdre(i + 1);

        return quizRepository.save(quiz);
    }

    // ══════════════════════════════════════════════════════════
    // SUPPRESSION QUIZ
    // ══════════════════════════════════════════════════════════

    @Override
    public void deleteMiniQuiz(Long formationId, Long coursId, String email) {
        checkAcces(formationId, email);
        if (!quizRepository.existsByCoursId(coursId))
            throw new RuntimeException("Aucun quiz trouvé pour ce cours.");
        quizRepository.deleteByCoursId(coursId);
        log.info("Quiz supprimé pour le cours {}", coursId);
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════

    private void checkAcces(Long formationId, String email) {
        if (!quizRepository.isFormateurOfFormation(formationId, email))
            throw new SecurityException("Accès interdit à cette formation.");
    }

    private void checkCoursExists(Long coursId, Long formationId) {
        if (!quizRepository.coursExistsInFormation(coursId, formationId))
            throw new RuntimeException("Cours non trouvé dans cette formation.");
    }

    private Quiz getQuizOrThrow(Long coursId) {
        return quizRepository.findByCoursId(coursId)
                .orElseThrow(() -> new RuntimeException("Aucun quiz trouvé pour ce cours."));
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
