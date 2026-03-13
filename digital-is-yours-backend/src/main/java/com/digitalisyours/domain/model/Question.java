package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    private Long id;
    private String texte;
    private String explication;
    private Integer ordre;
    private Boolean genereParIA;
    private Long quizId;

    @Builder.Default
    private List<OptionQuestion> options = new ArrayList<>();
}
