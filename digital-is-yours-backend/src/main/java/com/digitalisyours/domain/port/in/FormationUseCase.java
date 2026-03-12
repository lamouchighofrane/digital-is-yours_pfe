package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.model.User;

import java.util.List;
import java.util.Map;

public interface FormationUseCase {
    Map<String, Long> getStats();
    List<User> getAllFormateurs();
    List<Formation> getAllFormations();
    Formation getFormationById(Long id);
    Formation createFormation(Formation formation);
    Formation updateFormation(Long id, Formation formation);
    Formation affecterFormateur(Long formationId, Long formateurId);
    Formation retirerFormateur(Long formationId);
    Formation toggleStatut(Long id);
    void deleteFormation(Long id);
}
