package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.CoursFormation;
import com.digitalisyours.domain.model.ListeCoursFormation;
import com.digitalisyours.domain.model.QuizFinalInfo;

import com.digitalisyours.domain.port.in.ConsulterCoursFormationUseCase;
import com.digitalisyours.domain.port.out.ConsulterCoursFormationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsulterCoursFormationService implements ConsulterCoursFormationUseCase {
    private final ConsulterCoursFormationRepositoryPort repositoryPort;

    @Override
    public ListeCoursFormation getCoursDeFormation(Long formationId, String email) {

        // 1. Vérifier que l'apprenant est inscrit et a payé
        boolean inscrit = repositoryPort.estInscritEtPaye(email, formationId);
        if (!inscrit) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vous n'êtes pas inscrit à cette formation."
            );
        }

        // 2. Récupérer les cours publiés triés par ordre
        List<CoursFormation> cours = repositoryPort.findCoursPubiesParFormation(formationId);

        // 3. Récupérer les infos du quiz final (peut être absent)
        QuizFinalInfo quiz = repositoryPort.findQuizFinalInfo(formationId)
                .orElse(QuizFinalInfo.builder()
                        .existe(false)
                        .notePassage(null)
                        .dureeMinutes(null)
                        .nombreTentatives(null)
                        .build());

        // 4. Assembler et retourner la réponse
        return ListeCoursFormation.builder()
                .formationId(formationId)
                .total(cours.size())
                .cours(cours)
                .quiz(quiz)
                .build();
    }
}
