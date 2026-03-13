package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Quiz;

import java.util.Map;
import java.util.Optional;

public interface QuizRepositoryPort {
    Optional<Quiz> findByCoursId(Long coursId);
    boolean existsByCoursId(Long coursId);
    Quiz save(Quiz quiz);
    void deleteByCoursId(Long coursId);

    // Récupérer les infos brutes d'un cours
    Map<String, Object> getCoursInfos(Long coursId);

    // Vérification accès formateur
    boolean isFormateurOfFormation(Long formationId, String email);
    boolean coursExistsInFormation(Long coursId, Long formationId);
}
