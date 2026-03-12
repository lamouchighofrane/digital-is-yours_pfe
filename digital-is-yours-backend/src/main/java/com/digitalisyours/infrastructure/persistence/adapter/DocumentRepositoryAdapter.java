package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Document;
import com.digitalisyours.domain.port.out.DocumentRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CoursEntity;
import com.digitalisyours.infrastructure.persistence.entity.DocumentEntity;
import com.digitalisyours.infrastructure.persistence.repository.CoursJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.DocumentJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepositoryPort {
    private final DocumentJpaRepository documentJpaRepository;
    private final CoursJpaRepository coursJpaRepository;
    private final FormationJpaRepository formationJpaRepository;

    @Override
    @Transactional(readOnly = true)   // ← FIX Bug 3 : no Session
    public List<Document> findByCoursId(Long coursId) {
        return documentJpaRepository.findByCoursId(coursId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findById(Long docId) {
        return documentJpaRepository.findById(docId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findByIdAndCoursId(Long docId, Long coursId) {
        return documentJpaRepository.findById(docId)
                .filter(d -> d.getCours().getId().equals(coursId))
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCoursId(Long coursId) {
        return documentJpaRepository.countByCoursId(coursId);
    }

    @Override
    @Transactional(readOnly = true)
    public long sumTailleByCoursId(Long coursId) {
        return documentJpaRepository.sumTailleByCoursId(coursId);
    }

    @Override
    @Transactional
    public Document save(Document document) {
        CoursEntity cours = coursJpaRepository.findById(document.getCoursId())
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        DocumentEntity entity;
        if (document.getId() != null) {
            entity = documentJpaRepository.findById(document.getId())
                    .orElse(new DocumentEntity());
        } else {
            entity = new DocumentEntity();
        }

        entity.setTitre(document.getTitre());
        entity.setNomFichier(document.getNomFichier());
        entity.setUrl(document.getUrl());
        entity.setTypeFichier(document.getTypeFichier());
        entity.setTaille(document.getTaille());
        entity.setCours(cours);

        return toDomain(documentJpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long docId) {
        documentJpaRepository.deleteById(docId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFormateurOfFormation(Long formationId, String email) {
        return formationJpaRepository.findById(formationId)
                .map(f -> f.getFormateur() != null
                        && f.getFormateur().getEmail().equals(email))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean coursExistsInFormation(Long coursId, Long formationId) {
        return coursJpaRepository.findById(coursId)
                .map(c -> c.getFormation().getId().equals(formationId))
                .orElse(false);
    }

    // ── Mapping ───────────────────────────────────────────────

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
