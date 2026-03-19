package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Document;
import com.digitalisyours.domain.port.out.SuivreCoursRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.DocumentEntity;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.DocumentJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.InscriptionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SuivreCoursRepositoryAdapter implements SuivreCoursRepositoryPort {
    private final InscriptionJpaRepository inscriptionJpaRepository;
    private final CoursJpaRepository coursJpaRepository;
    private final DocumentJpaRepository documentJpaRepository;

    // ── Vérification inscription ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean estInscritEtPaye(String email, Long formationId) {
        return inscriptionJpaRepository
                .existsByApprenantEmailAndFormationIdAndStatutPaiement(
                        email, formationId, "PAYE");
    }

    // ── Vérification appartenance cours ──────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean coursAppartientAFormation(Long coursId, Long formationId) {
        return coursJpaRepository.findById(coursId)
                .map(c -> c.getFormation() != null
                        && formationId.equals(c.getFormation().getId()))
                .orElse(false);
    }

    // ── Documents ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Document> findDocumentsByCoursId(Long coursId) {
        return documentJpaRepository.findByCoursId(coursId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // ── Mapping entity → domain ───────────────────────────────────────────

    private Document toDomain(DocumentEntity e) {
        return Document.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .nomFichier(e.getNomFichier())
                .url(e.getUrl())
                .typeFichier(e.getTypeFichier())
                .taille(e.getTaille())
                .dateAjout(e.getDateAjout())
                .coursId(e.getCours() != null ? e.getCours().getId() : null)
                .build();
    }
}
