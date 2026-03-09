package com.digitalisyours.infrastructure.ia;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OptionIA {
    private String ordre;
    private String texte;
    private boolean estCorrecte;
}
