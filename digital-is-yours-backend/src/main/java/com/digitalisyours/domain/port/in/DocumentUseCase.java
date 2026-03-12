package com.digitalisyours.domain.port.in;


import com.digitalisyours.domain.model.Document;

import java.util.Map;

public interface DocumentUseCase {
    Map<String, Object> getDocuments(Long formationId, Long coursId, String email);
    Document uploadDocument(Long formationId, Long coursId, String email,
                            String originalFilename, String contentType,
                            long fileSize, byte[] fileBytes, String titre);
    Document updateDocument(Long formationId, Long coursId, Long docId, String email, String titre);
    void deleteDocument(Long formationId, Long coursId, Long docId, String email);
    Document getDocumentForDownload(Long coursId, Long docId);
}
