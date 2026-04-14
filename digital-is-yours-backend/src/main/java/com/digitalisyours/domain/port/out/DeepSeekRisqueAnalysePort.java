package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.AnalyseRisque;

public interface DeepSeekRisqueAnalysePort {
    /**
     * Appelle DeepSeek pour analyser le risque d'abandon.
     * Retourne une analyse enrichie avec explication + recommandation.
     */
    AnalyseRisque analyserRisque(AnalyseRisque contexte);
}