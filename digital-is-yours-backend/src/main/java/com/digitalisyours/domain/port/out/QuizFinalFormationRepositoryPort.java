package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Quiz;

import java.util.Map;
import java.util.Optional;

public interface QuizFinalFormationRepositoryPort {
    Optional<Quiz> findByFormationId(Long formationId);
    boolean existsByFormationId(Long formationId);
    Quiz save(Quiz quiz);
    void deleteByFormationId(Long formationId);

    // Infos de la formation (titre, description, niveau, tous les cours avec documents)
    Map<String, Object> getFormationInfos(Long formationId);

    // Vérification accès formateur
    boolean isFormateurOfFormation(Long formationId, String email);
}
