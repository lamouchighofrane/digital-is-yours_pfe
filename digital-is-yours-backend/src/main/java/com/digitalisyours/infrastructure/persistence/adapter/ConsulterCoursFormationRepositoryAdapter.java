package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.CoursFormation;
import com.digitalisyours.domain.model.QuizFinalInfo;
import com.digitalisyours.domain.port.out.ConsulterCoursFormationRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.QuizEntity;
import com.digitalisyours.infrastructure.persistence.repository.ConsulterCoursFormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.QuizJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConsulterCoursFormationRepositoryAdapter implements ConsulterCoursFormationRepositoryPort {
    private final ConsulterCoursFormationJpaRepository coursJpaRepository;
    private final InscriptionJpaRepository inscriptionJpaRepository;
    private final QuizJpaRepository quizJpaRepository;

    // ── Port/out : estInscritEtPaye ───────────────────────────────────────

    @Override
    public boolean estInscritEtPaye(String email, Long formationId) {
        return inscriptionJpaRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(
                        email, formationId, "PAYE");
    }

    // ── Port/out : findCoursPubiesParFormation ────────────────────────────

    @Override
    public List<CoursFormation> findCoursPubiesParFormation(Long formationId) {
        return coursJpaRepository
                .findCoursPubiesByFormationId(formationId)
                .stream()
                .map(this::toCoursFormation)
                .collect(Collectors.toList());
    }

    // ── Port/out : findQuizFinalInfo ──────────────────────────────────────

    @Override
    public Optional<QuizFinalInfo> findQuizFinalInfo(Long formationId) {
        return quizJpaRepository.findQuizFinalByFormationId(formationId)
                .map(this::toQuizFinalInfo);
    }

    // ── Mapping entity → domain ───────────────────────────────────────────

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
                // URL exposée : YouTube → URL publique, LOCAL → null (sécurité)
                .videoUrl("YOUTUBE".equals(c.getVideoType()) ? c.getVideoUrl() : null)
                .hasVideo(c.getVideoType() != null)
                .build();
    }

    private QuizFinalInfo toQuizFinalInfo(QuizEntity q) {
        return QuizFinalInfo.builder()
                .existe(true)
                .notePassage(q.getNotePassage()      != null ? q.getNotePassage()      : 70f)
                .dureeMinutes(q.getDureeMinutes()    != null ? q.getDureeMinutes()     : 45)
                .nombreTentatives(q.getNombreTentatives() != null ? q.getNombreTentatives() : 3)
                .build();
    }
}
