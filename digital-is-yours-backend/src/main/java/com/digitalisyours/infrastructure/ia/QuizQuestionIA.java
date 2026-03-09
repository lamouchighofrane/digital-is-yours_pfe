package com.digitalisyours.infrastructure.ia;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class QuizQuestionIA {
    private String texte;
    private String explication;
    private List<OptionIA> options;
}
