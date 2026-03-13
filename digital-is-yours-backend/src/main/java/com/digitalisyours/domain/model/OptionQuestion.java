package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionQuestion {
    private Long id;
    private String texte;
    private Boolean estCorrecte;
    private String ordre;       // "A", "B", "C", "D"
    private Long questionId;
}
