package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.Competence;

import java.util.List;

public interface CompetenceUseCase {
    List<Competence> getAllCompetences();
    List<String> getAllCategories();
    List<Competence> getCompetencesByFormation(Long formationId);
    Competence createCompetence(Competence competence);
    Competence updateCompetence(Long id, Competence competence);
    void deleteCompetence(Long id);
    void associerCompetences(Long formationId, List<Long> competenceIds);
}
