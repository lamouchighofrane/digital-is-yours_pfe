package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.RecommandationIA;

import java.util.List;

public interface RecommandationUseCase {
    /**
     * Retourne le Top 5 des formations recommandées pour un apprenant.
     * Résultat mis en cache 30 minutes par apprenant.
     */
    List<RecommandationIA> getRecommandations(String emailApprenant);

    /**
     * Invalide le cache pour forcer un recalcul immédiat.
     */
    void invaliderCache(String emailApprenant);
}
