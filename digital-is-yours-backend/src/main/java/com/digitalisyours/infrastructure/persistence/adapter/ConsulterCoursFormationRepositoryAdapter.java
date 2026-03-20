package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.CoursFormation;
import com.digitalisyours.domain.model.QuizFinalInfo;
import com.digitalisyours.domain.port.out.ConsulterCoursFormationRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.ProgressionCoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import com.digitalisyours.infrastructure.persistence.repository.ConsulterCoursFormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.ProgressionCoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.QuizJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConsulterCoursFormationRepositoryAdapter implements ConsulterCoursFormationRepositoryPort {

    private final ConsulterCoursFormationJpaRepository coursJpaRepository;
    private final InscriptionJpaRepository inscriptionJpaRepository;
    private final QuizJpaRepository quizJpaRepository;
    private final ProgressionCoursJpaRepository progressionRepository;

    @Override
    public boolean estInscritEtPaye(String email, Long formationId) {
        return inscriptionJpaRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(email, formationId, "PAYE");
    }

    @Override
    public List<CoursFormation> findCoursPubiesParFormation(Long formationId) {
        return coursJpaRepository.findCoursPubiesByFormationId(formationId)
                .stream().map(this::toCoursFormation).collect(Collectors.toList());
    }

    /**
     * Enrichit chaque cours avec les états de progression :
     * videoVue, documentOuvert, quizPasse, estTermine, statutProgression
     */
    public List<CoursFormation> findCoursPubiesParFormationAvecProgression(
            Long formationId, String email) {

        // Récupérer toutes les progressions de cet apprenant pour cette formation
        Map<Long, ProgressionCoursEntity> progressionMap = progressionRepository
                .findByEmailAndFormationId(email, formationId)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getCours().getId(),
                        p -> p
                ));

        return coursJpaRepository.findCoursPubiesByFormationId(formationId)
                .stream()
                .map(c -> toCoursFormationAvecProgression(c, progressionMap.get(c.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<QuizFinalInfo> findQuizFinalInfo(Long formationId) {
        return quizJpaRepository.findQuizFinalByFormationId(formationId)
                .map(this::toQuizFinalInfo);
    }

    @Override
    public boolean existsMiniQuizForCours(Long coursId) {
        return quizJpaRepository.existsByCoursIdAndType(coursId, "MiniQuiz");
    }

    // ── Mappings ──────────────────────────────────────────────────────────────

    private CoursFormation toCoursFormation(CoursEntity c) {
        return CoursFormation.builder()
                .id(c.getId())
                .titre(c.getTitre())
                .description(c.getDescription())
                .objectifs(c.getObjectifs())
                .dureeEstimee(c.getDureeEstimee())
                .ordre(c.getOrdre())
                .statut(c.getStatut())
                .videoType(c.getVideoType())
                .videoUrl("YOUTUBE".equals(c.getVideoType()) ? c.getVideoUrl() : null)
                .hasVideo(c.getVideoType() != null)
                .hasQuiz(false)
                .estTermine(false)
                .videoVue(false)
                .documentOuvert(false)
                .quizPasse(false)
                .statutProgression("A_FAIRE")
                .build();
    }

    private CoursFormation toCoursFormationAvecProgression(
            CoursEntity c, ProgressionCoursEntity progression) {

        boolean videoVue       = progression != null && progression.isVideoVue();
        boolean documentOuvert = progression != null && progression.isDocumentOuvert();
        boolean quizPasse      = progression != null && progression.isQuizPasse();
        boolean estTermine     = progression != null && "TERMINE".equals(progression.getStatut());
        String  statut         = progression != null ? progression.getStatut() : "A_FAIRE";

        return CoursFormation.builder()
                .id(c.getId())
                .titre(c.getTitre())
                .description(c.getDescription())
                .objectifs(c.getObjectifs())
                .dureeEstimee(c.getDureeEstimee())
                .ordre(c.getOrdre())
                .statut(c.getStatut())
                .videoType(c.getVideoType())
                .videoUrl("YOUTUBE".equals(c.getVideoType()) ? c.getVideoUrl() : null)
                .hasVideo(c.getVideoType() != null)
                .hasQuiz(false)
                .estTermine(estTermine)
                .videoVue(videoVue)
                .documentOuvert(documentOuvert)
                .quizPasse(quizPasse)
                .statutProgression(statut)
                .build();
    }

    private QuizFinalInfo toQuizFinalInfo(QuizEntity q) {
        return QuizFinalInfo.builder()
                .existe(true)
                .notePassage(q.getNotePassage()     != null ? q.getNotePassage()     : 70f)
                .dureeMinutes(q.getDureeMinutes()   != null ? q.getDureeMinutes()    : 45)
                .nombreTentatives(q.getNombreTentatives() != null ? q.getNombreTentatives() : 3)
                .build();
    }
}
