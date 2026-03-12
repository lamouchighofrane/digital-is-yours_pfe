package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Cours;

import java.util.List;
import java.util.Map;

public interface CoursFormateurUseCase {
    List<Cours> getCours(Long formationId, String email);
    Cours createCours(Long formationId, String email, Map<String, Object> payload);
    Cours updateCours(Long formationId, Long coursId, String email, Map<String, Object> payload);
    Cours toggleStatut(Long formationId, Long coursId, String email);
    void deleteCours(Long formationId, Long coursId, String email);
    void reordonner(Long formationId, String email, List<Map<String, Object>> ordres);
    Cours ajouterVideoYoutube(Long formationId, Long coursId, String email, String url);
    Cours uploadVideoLocale(Long formationId, Long coursId, String email,
                            String originalFilename, String contentType,
                            long fileSize, byte[] fileBytes);
    Cours supprimerVideo(Long formationId, Long coursId, String email);
}
