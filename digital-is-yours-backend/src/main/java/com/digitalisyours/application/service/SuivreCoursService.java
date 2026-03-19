package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Document;
import com.digitalisyours.domain.port.in.SuivreCoursUseCase;
import com.digitalisyours.domain.port.out.SuivreCoursRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuivreCoursService implements SuivreCoursUseCase {
    private final SuivreCoursRepositoryPort suivreCoursRepository;

    // ── Récupérer les documents d'un cours ───────────────────────────────

    @Override
    public List<Document> getDocumentsDuCours(String email, Long formationId, Long coursId) {
        verifierAccesCours(email, formationId, coursId);
        return suivreCoursRepository.findDocumentsByCoursId(coursId);
    }

    // ── Vérifier l'accès (réutilisé par le Controller vidéo) ────────────

    @Override
    public void verifierAccesCours(String email, Long formationId, Long coursId) {
        if (!suivreCoursRepository.estInscritEtPaye(email, formationId)) {
            throw new SecurityException("Accès refusé : vous n'êtes pas inscrit à cette formation ou le paiement est en attente.");
        }
        if (!suivreCoursRepository.coursAppartientAFormation(coursId, formationId)) {
            throw new RuntimeException("Cours non trouvé dans cette formation.");
        }
    }
}
