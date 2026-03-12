package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.Document;

import java.util.List;
import java.util.Optional;

public interface DocumentRepositoryPort {
    List<Document> findByCoursId(Long coursId);
    Optional<Document> findById(Long docId);
    Optional<Document> findByIdAndCoursId(Long docId, Long coursId);
    long countByCoursId(Long coursId);
    long sumTailleByCoursId(Long coursId);
    Document save(Document document);
    void deleteById(Long docId);
    boolean isFormateurOfFormation(Long formationId, String email);
    boolean coursExistsInFormation(Long coursId, Long formationId);
}
