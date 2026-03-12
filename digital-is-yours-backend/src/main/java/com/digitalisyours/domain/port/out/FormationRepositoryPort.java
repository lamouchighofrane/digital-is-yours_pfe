package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface FormationRepositoryPort {
    List<Formation> findAllWithDetails();
    Optional<Formation> findById(Long id);
    boolean existsById(Long id);
    Formation save(Formation formation);
    void deleteById(Long id);
    long countAll();
    long countPubliees();
    long countBrouillons();
    List<User> findAllFormateursActifs();
    Optional<User> findFormateurById(Long id);
    void saveNotificationAffectation(Long formateurId, String type, String titre, String message, Long formationId, String formationTitre);
}
