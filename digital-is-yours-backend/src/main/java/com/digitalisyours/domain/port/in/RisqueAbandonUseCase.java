package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.AnalyseRisque;
import java.util.List;

public interface RisqueAbandonUseCase {
    /** Lance l'analyse pour tous les apprenants inscrits */
    void analyserTousLesApprenants();

    /** Analyse un apprenant spécifique pour une formation */
    AnalyseRisque analyserApprenant(Long apprenantId, Long formationId);

    /** Récupère les analyses d'un apprenant */
    List<AnalyseRisque> getMesAnalyses(String email);

    /** Récupère la dernière analyse pour une formation */
    AnalyseRisque getDerniereAnalyse(String email, Long formationId);
}