package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.port.out.FormateurRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FormateurRepositoryAdapter implements FormateurRepositoryPort {

    private final FormationJpaRepository         formationJpaRepository;
    private final InscriptionJpaRepository       inscriptionJpaRepository;
    private final AnalyseRisqueJpaRepository     analyseRisqueJpaRepository;
    private final ResultatMiniQuizJpaRepository  miniQuizResultatRepository;
    private final ResultatQuizFinalJpaRepository quizFinalResultatRepository;

    @Override
    public List<Formation> findFormationsByFormateurEmail(String email) {
        return formationJpaRepository.findAllWithCategorie().stream()
                .filter(f -> f.getFormateur() != null
                        && f.getFormateur().getEmail().equals(email)
                        && "PUBLIE".equals(f.getStatut()))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findApprenantsAvecStatut(List<Long> formationIds) {
        if (formationIds == null || formationIds.isEmpty()) return Collections.emptyList();

        List<Object[]> rows = inscriptionJpaRepository
                .findApprenantsAvecInscriptionByFormations(formationIds);

        List<Object[]> analyses = analyseRisqueJpaRepository
                .findDernieresAnalysesForFormations(formationIds);
        Map<String, Object[]> analyseMap = new HashMap<>();
        for (Object[] a : analyses) {
            analyseMap.put(a[0] + "_" + a[1], a);
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long    apprenantId      = (Long)          row[0];
            String  prenom           = (String)         row[1];
            String  nom              = (String)         row[2];
            String  email            = (String)         row[3];
            String  telephone        = (String)         row[4];
            Long    formationId      = (Long)           row[7];
            String  formationTitre   = (String)         row[8];
            // ✅ Lire directement depuis inscriptions comme l'admin
            Float   progression      = row[9] != null ? ((Number) row[9]).floatValue() : 0f;
            String  statutApprenant  = (String)         row[10];
            LocalDateTime dateInscriptionForm = (LocalDateTime) row[11];
            LocalDateTime dernierActivite     = (LocalDateTime) row[12];

            String  niveauRisque    = null;
            Float   scoreRisque     = null;
            Integer joursInactivite = null;

            Object[] analyse = analyseMap.get(apprenantId + "_" + formationId);
            if (analyse != null) {
                niveauRisque    = (String) analyse[2];
                scoreRisque     = analyse[3] != null ? ((Number) analyse[3]).floatValue() : null;
                joursInactivite = analyse[4] != null ? ((Number) analyse[4]).intValue()   : null;
            }

            String statutEnrichi = calculerStatutEnrichi(
                    statutApprenant, niveauRisque, progression);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",                      apprenantId);
            item.put("prenom",                  prenom);
            item.put("nom",                     nom);
            item.put("email",                   email);
            item.put("telephone",               telephone);
            item.put("formationId",             formationId);
            item.put("formationTitre",          formationTitre);
            item.put("progression",             progression);
            item.put("statutApprenant",         statutApprenant != null ? statutApprenant : "A_FAIRE");
            item.put("dateInscriptionFormation",dateInscriptionForm);
            item.put("dernierActivite",         dernierActivite);
            item.put("niveauRisque",            niveauRisque);
            item.put("scoreRisque",             scoreRisque);
            item.put("joursInactivite",         joursInactivite);
            item.put("statutEnrichi",           statutEnrichi);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findInfractions(List<Long> formationIds) {
        if (formationIds == null || formationIds.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();

        List<Object[]> miniRows = miniQuizResultatRepository
                .findSuspectsForFormations(formationIds);
        for (Object[] row : miniRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",               row[0]);
            item.put("apprenantEmail",   row[1]);
            item.put("apprenantPrenom",  row[2]);
            item.put("apprenantNom",     row[3]);
            item.put("formationId",      row[4]);
            item.put("formationTitre",   row[5]);
            item.put("coursTitre",       row[6]);
            item.put("typeQuiz",         "MINI_QUIZ");
            item.put("nbInfractions",    row[7]);
            item.put("scoreBrut",        row[8]);
            item.put("penalite",         row[9]);
            item.put("scoreFinal",       row[10]);
            item.put("datePassage",      row[11]);
            item.put("detailInfractions",row[12]);
            result.add(item);
        }

        List<Object[]> finalRows = quizFinalResultatRepository
                .findSuspectsForFormations(formationIds);
        for (Object[] row : finalRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",               row[0]);
            item.put("apprenantEmail",   row[1]);
            item.put("apprenantPrenom",  row[2]);
            item.put("apprenantNom",     row[3]);
            item.put("formationId",      row[4]);
            item.put("formationTitre",   row[5]);
            item.put("coursTitre",       null);
            item.put("typeQuiz",         "QUIZ_FINAL");
            item.put("nbInfractions",    row[6]);
            item.put("scoreBrut",        row[7]);
            item.put("penalite",         row[8]);
            item.put("scoreFinal",       row[9]);
            item.put("datePassage",      row[10]);
            item.put("detailInfractions",row[11]);
            result.add(item);
        }

        result.sort((a, b) -> {
            Object da = a.get("datePassage");
            Object db = b.get("datePassage");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.toString().compareTo(da.toString());
        });

        return result;
    }

    private String calculerStatutEnrichi(String statut, String risque, Float prog) {
        if ("CERTIFIE".equals(statut)) return "CERTIFIE";
        if ("ELEVE".equals(risque) || "MOYEN".equals(risque)) return "A_RISQUE";
        if ("TERMINE".equals(statut)) return "TERMINE";
        if ("EN_COURS".equals(statut)) return "EN_COURS";
        if (prog != null && prog > 0) return "EN_COURS";
        return "A_FAIRE";
    }

    private Formation toDomain(FormationEntity e) {
        Formation f = Formation.builder()
                .id(e.getId()).titre(e.getTitre()).description(e.getDescription())
                .imageCouverture(e.getImageCouverture()).dureeEstimee(e.getDureeEstimee())
                .niveau(e.getNiveau()).statut(e.getStatut()).dateCreation(e.getDateCreation())
                .nombreInscrits(e.getNombreInscrits()).nombreCertifies(e.getNombreCertifies())
                .noteMoyenne(e.getNoteMoyenne()).tauxReussite(e.getTauxReussite()).build();
        if (e.getCategorie() != null) {
            f.setCategorieId(e.getCategorie().getId());
            f.setCategorieNom(e.getCategorie().getNom());
        }
        return f;
    }
}