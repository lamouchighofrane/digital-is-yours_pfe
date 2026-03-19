package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeCoursFormation {
    private Long               formationId;
    private int                total;
    private List<CoursFormation> cours;
    private QuizFinalInfo      quiz;
}
