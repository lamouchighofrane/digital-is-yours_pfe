package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Document;
import com.digitalisyours.domain.port.in.DocumentUseCase;
import com.digitalisyours.domain.port.out.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService implements DocumentUseCase {
    private final DocumentRepositoryPort documentRepository;

    @Value("${app.upload.dir:uploads/videos}")
    private String uploadDir;

    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "image/png", "image/jpeg", "image/gif", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    @Override
    public Map<String, Object> getDocuments(Long formationId, Long coursId, String email) {
        checkAcces(formationId, email);
        checkCoursExists(coursId, formationId);

        List<Document> documents = documentRepository.findByCoursId(coursId);
        long totalTaille = documentRepository.sumTailleByCoursId(coursId);

        return Map.of(
                "documents", documents,
                "total", documents.size(),
                "totalTaille", totalTaille
        );
    }

    @Override
    public Document uploadDocument(Long formationId, Long coursId, String email,
                                   String originalFilename, String contentType,
                                   long fileSize, byte[] fileBytes, String titre) {
        checkAcces(formationId, email);
        checkCoursExists(coursId, formationId);

        if (fileBytes == null || fileBytes.length == 0)
            throw new RuntimeException("Le fichier est vide.");
        if (!ALLOWED_TYPES.contains(contentType))
            throw new RuntimeException("Format non supporté. Formats acceptés : PDF, Word, PowerPoint, Excel, images, texte");
        if (fileSize > MAX_FILE_SIZE)
            throw new RuntimeException("Fichier trop volumineux. Maximum : 50 MB");
        if (documentRepository.countByCoursId(coursId) >= 10)
            throw new RuntimeException("Maximum 10 documents par cours.");

        String extension = getExtension(originalFilename);
        String nomFichier = UUID.randomUUID().toString() + extension;
        String titreFinal = (titre != null && !titre.isBlank())
                ? titre.trim() : getNameWithoutExtension(originalFilename);

        try {
            Path uploadPath = Paths.get(uploadDir, "documents", coursId.toString());
            Files.createDirectories(uploadPath);
            Files.write(uploadPath.resolve(nomFichier), fileBytes);
        } catch (IOException e) {
            log.error("Erreur upload document cours {}: {}", coursId, e.getMessage());
            throw new RuntimeException("Erreur lors de l'enregistrement du fichier.");
        }

        Document doc = Document.builder()
                .titre(titreFinal)
                .nomFichier(originalFilename)
                .url(nomFichier)
                .typeFichier(contentType)
                .taille(fileSize)
                .coursId(coursId)
                .build();

        return documentRepository.save(doc);
    }

    @Override
    public Document updateDocument(Long formationId, Long coursId, Long docId,
                                   String email, String titre) {
        checkAcces(formationId, email);

        if (titre == null || titre.isBlank())
            throw new RuntimeException("Le titre est obligatoire");

        Document doc = documentRepository.findByIdAndCoursId(docId, coursId)
                .orElseThrow(() -> new RuntimeException("Document non trouvé"));

        doc.setTitre(titre.trim());
        return documentRepository.save(doc);
    }

    @Override
    public void deleteDocument(Long formationId, Long coursId, Long docId, String email) {
        checkAcces(formationId, email);

        Document doc = documentRepository.findByIdAndCoursId(docId, coursId)
                .orElseThrow(() -> new RuntimeException("Document non trouvé"));

        try {
            Path filePath = Paths.get(uploadDir, "documents", coursId.toString(), doc.getUrl());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier document {}: {}", doc.getUrl(), e.getMessage());
        }

        documentRepository.deleteById(docId);
    }

    @Override
    public Document getDocumentForDownload(Long coursId, Long docId) {
        return documentRepository.findByIdAndCoursId(docId, coursId)
                .orElseThrow(() -> new RuntimeException("Document non trouvé"));
    }

    // ── Helpers ───────────────────────────────────────────────

    private void checkAcces(Long formationId, String email) {
        if (!documentRepository.isFormateurOfFormation(formationId, email))
            throw new SecurityException("Accès interdit à cette formation");
    }

    private void checkCoursExists(Long coursId, Long formationId) {
        if (!documentRepository.coursExistsInFormation(coursId, formationId))
            throw new RuntimeException("Cours non trouvé");
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    private String getNameWithoutExtension(String filename) {
        if (filename == null) return "Document";
        int dot = filename.lastIndexOf(".");
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
