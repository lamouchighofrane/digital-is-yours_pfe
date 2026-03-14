package com.digitalisyours.infrastructure.ia;

import com.digitalisyours.domain.model.OptionQuestion;
import com.digitalisyours.domain.model.Question;
import com.digitalisyours.domain.port.out.IAQuizPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class IAQuizAdapter implements IAQuizPort {
    private final IAQuizService iaQuizService;

    @Override
    public List<Question> genererQuestions(
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
            boolean inclureCasPratiques) {

        CoursInfoIA coursInfo = new CoursInfoIA(coursTitre, coursDescription, coursObjectifs);

        List<QuizQuestionIA> questionsIA = iaQuizService.genererQuiz(
                coursInfo,
                coursId,
                formationId,
                videoUrl,
                videoType,
                nombreQuestions,
                difficulte,
                inclureDefinitions,
                inclureCasPratiques
        );

        return toDomainQuestions(questionsIA);
    }

    // ── Conversion QuizQuestionIA → domain Question ──────────────

    private List<Question> toDomainQuestions(List<QuizQuestionIA> questionsIA) {
        if (questionsIA == null) return new ArrayList<>();

        List<Question> result = new ArrayList<>();
        for (int i = 0; i < questionsIA.size(); i++) {
            QuizQuestionIA qIA = questionsIA.get(i);

            List<OptionQuestion> options = new ArrayList<>();
            if (qIA.getOptions() != null) {
                for (OptionIA oIA : qIA.getOptions()) {
                    options.add(OptionQuestion.builder()
                            .texte(oIA.getTexte())
                            .estCorrecte(oIA.isEstCorrecte())
                            .ordre(oIA.getOrdre())
                            .build());
                }
            }

            result.add(Question.builder()
                    .texte(qIA.getTexte())
                    .explication(qIA.getExplication())
                    .ordre(i + 1)
                    .genereParIA(true)
                    .options(options)
                    .build());
        }
        return result;
    }
}
