package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Competence;
import com.digitalisyours.domain.port.in.CompetenceUseCase;
import com.digitalisyours.domain.port.out.CompetenceRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompetenceService implements CompetenceUseCase {
    private final CompetenceRepositoryPort competenceRepository;

    @Override
    public List<Competence> getAllCompetences() {
        return competenceRepository.findAllOrderByNom();
    }

    @Override
    public List<String> getAllCategories() {
        return competenceRepository.findAllCategories();
    }

    @Override
    public List<Competence> getCompetencesByFormation(Long formationId) {
        return competenceRepository.findByFormationId(formationId);
    }

    @Override
    public Competence createCompetence(Competence competence) {
        if (competence.getNom() == null || competence.getNom().isBlank()) {
            throw new RuntimeException("Le nom est obligatoire");
        }
        if (competenceRepository.existsByNom(competence.getNom().trim())) {
            throw new RuntimeException("Cette compétence existe déjà");
        }
        competence.setNom(competence.getNom().trim());
        return competenceRepository.save(competence);
    }

    @Override
    public Competence updateCompetence(Long id, Competence competence) {
        Competence existing = competenceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compétence non trouvée"));

        if (competence.getNom() == null || competence.getNom().isBlank()) {
            throw new RuntimeException("Le nom est obligatoire");
        }
        if (!existing.getNom().equals(competence.getNom().trim())
                && competenceRepository.existsByNom(competence.getNom().trim())) {
            throw new RuntimeException("Cette compétence existe déjà");
        }

        competence.setId(id);
        competence.setNom(competence.getNom().trim());
        return competenceRepository.save(competence);
    }

    @Override
    public void deleteCompetence(Long id) {
        if (!competenceRepository.existsById(id)) {
            throw new RuntimeException("Compétence non trouvée");
        }
        competenceRepository.deleteFormationLinks(id);
        competenceRepository.deleteById(id);
    }

    @Override
    public void associerCompetences(Long formationId, List<Long> competenceIds) {
        competenceRepository.associerCompetencesFormation(formationId, competenceIds);
    }
}
