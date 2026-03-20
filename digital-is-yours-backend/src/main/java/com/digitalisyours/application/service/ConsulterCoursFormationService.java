package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.CoursFormation;
import com.digitalisyours.domain.model.ListeCoursFormation;
import com.digitalisyours.domain.model.QuizFinalInfo;

import com.digitalisyours.domain.port.in.ConsulterCoursFormationUseCase;
import com.digitalisyours.domain.port.out.ConsulterCoursFormationRepositoryPort;
import com.digitalisyours.infrastructure.persistence.adapter.ConsulterCoursFormationRepositoryAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsulterCoursFormationService implements ConsulterCoursFormationUseCase {
    private final ConsulterCoursFormationRepositoryPort repositoryPort;
    private final ConsulterCoursFormationRepositoryAdapter repositoryAdapter;

    @Override
    public ListeCoursFormation getCoursDeFormation(Long formationId, String email) {

        // 1. Vérifier inscription
        boolean inscrit = repositoryPort.estInscritEtPaye(email, formationId);
        if (!inscrit) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vous n'êtes pas inscrit à cette formation.");
        }

        // 2. Récupérer les cours avec estTermine calculé depuis progression_cours
        List<CoursFormation> cours = repositoryAdapter
                .findCoursPubiesParFormationAvecProgression(formationId, email);

        // 3. Enrichir chaque cours avec hasQuiz
        cours.forEach(c -> c.setHasQuiz(repositoryPort.existsMiniQuizForCours(c.getId())));

        // 4. Quiz final
        QuizFinalInfo quiz = repositoryPort.findQuizFinalInfo(formationId)
                .orElse(QuizFinalInfo.builder()
                        .existe(false)
                        .notePassage(null)
                        .dureeMinutes(null)
                        .nombreTentatives(null)
                        .build());

        return ListeCoursFormation.builder()
                .formationId(formationId)
                .total(cours.size())
                .cours(cours)
                .quiz(quiz)
                .build();
    }
}
