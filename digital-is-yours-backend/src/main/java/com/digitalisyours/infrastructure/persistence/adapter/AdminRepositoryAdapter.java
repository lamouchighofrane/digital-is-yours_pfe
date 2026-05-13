package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.out.AdminRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.FormateurEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminRepositoryAdapter implements AdminRepositoryPort {

    private final UserJpaRepository         userJpaRepository;
    private final NotificationJpaRepository notificationJpaRepository;
    private final InscriptionJpaRepository  inscriptionJpaRepository;
    private final AnalyseRisqueJpaRepository analyseRisqueJpaRepository;
    private final PasswordEncoder           passwordEncoder;
    private final ResultatMiniQuizJpaRepository  miniQuizResultatRepository;
    private final ResultatQuizFinalJpaRepository quizFinalResultatRepository;

    @Override
    public List<User> findAllNonAdmin() {
        return userJpaRepository.findAllByRoleNot(Role.ADMIN)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity;

        if (user.getId() != null) {
            entity = userJpaRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + user.getId()));

            entity.setPrenom(user.getPrenom());
            entity.setNom(user.getNom());
            entity.setEmail(user.getEmail());
            entity.setTelephone(user.getTelephone());
            entity.setRole(user.getRole());
            entity.setEmailVerifie(user.isEmailVerifie());
            entity.setActive(user.isActive());
            entity.setDateInscription(user.getDateInscription());
            entity.setDerniereConnexion(user.getDerniereConnexion());

            if (user.getMotDePasse() != null && !user.getMotDePasse().isBlank()) {
                entity.setMotDePasse(user.getMotDePasse());
            }

        } else {
            if (user.getRole() == Role.FORMATEUR) {
                entity = FormateurEntity.builder()
                        .prenom(user.getPrenom())
                        .nom(user.getNom())
                        .email(user.getEmail())
                        .telephone(user.getTelephone())
                        .motDePasse(user.getMotDePasse())
                        .role(Role.FORMATEUR)
                        .emailVerifie(true)
                        .active(true)
                        .dateInscription(LocalDateTime.now())
                        .build();
            } else {
                entity = UserEntity.builder()
                        .prenom(user.getPrenom())
                        .nom(user.getNom())
                        .email(user.getEmail())
                        .telephone(user.getTelephone())
                        .motDePasse(user.getMotDePasse())
                        .role(user.getRole() != null ? user.getRole() : Role.APPRENANT)
                        .emailVerifie(true)
                        .active(true)
                        .dateInscription(LocalDateTime.now())
                        .build();
            }
        }

        return toDomain(userJpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        notificationJpaRepository.deleteByUserId(id);
        userJpaRepository.deleteById(id);
    }

    @Override
    public long countByRole(String role) {
        return userJpaRepository.countByRole(Role.valueOf(role));
    }

    @Override
    public long countEmailNonVerifie() {
        return userJpaRepository.countByEmailVerifieFalse();
    }

    @Override
    public long countDesactives() {
        return userJpaRepository.countByActiveFalse();
    }

    // ── NOUVEAU ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findApprenantsAvecStatut() {

        // 1. Récupérer toutes les inscriptions PAYÉES avec apprenant + formation
        List<Object[]> rows = inscriptionJpaRepository.findApprenantsAvecInscription();

        // 2. Récupérer toutes les dernières analyses (1 par apprenant+formation)
        //    On les met dans une map pour lookup rapide : "apprenantId_formationId" → analyse
        List<Object[]> analyses = analyseRisqueJpaRepository.findDernieresAnalyses();
        Map<String, Object[]> analyseMap = new HashMap<>();
        for (Object[] a : analyses) {
            Long appId  = (Long)   a[0]; // apprenantId
            Long formId = (Long)   a[1]; // formationId
            analyseMap.put(appId + "_" + formId, a);
        }

        // 3. Construire la liste résultat
        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : rows) {
            // row[0]  = apprenant.id (Long)
            // row[1]  = apprenant.prenom (String)
            // row[2]  = apprenant.nom (String)
            // row[3]  = apprenant.email (String)
            // row[4]  = apprenant.telephone (String)
            // row[5]  = apprenant.active (Boolean)
            // row[6]  = apprenant.dateInscription (LocalDateTime)
            // row[7]  = inscription.formation.id (Long)
            // row[8]  = inscription.formation.titre (String)
            // row[9]  = inscription.progression (Float)
            // row[10] = inscription.statutApprenant (String)
            // row[11] = inscription.dateInscription (LocalDateTime)
            // row[12] = inscription.dernierActivite (LocalDateTime)

            Long    apprenantId   = (Long)          row[0];
            String  prenom        = (String)         row[1];
            String  nom           = (String)         row[2];
            String  email         = (String)         row[3];
            String  telephone     = (String)         row[4];
            Boolean active        = (Boolean)        row[5];
            LocalDateTime dateInscriptionUser = (LocalDateTime) row[6];
            Long    formationId   = (Long)           row[7];
            String  formationTitre = (String)        row[8];
            Float   progression   = row[9] != null ? ((Number) row[9]).floatValue() : 0f;
            String  statutApprenant = (String)       row[10];
            LocalDateTime dateInscriptionForm = (LocalDateTime) row[11];
            LocalDateTime dernierActivite     = (LocalDateTime) row[12];

            // 4. Chercher l'analyse risque correspondante
            String  niveauRisque   = null;
            Float   scoreRisque    = null;
            Integer joursInactivite = null;
            LocalDateTime dateAnalyse = null;

            Object[] analyse = analyseMap.get(apprenantId + "_" + formationId);
            if (analyse != null) {
                niveauRisque    = (String)        analyse[2];
                scoreRisque     = analyse[3] != null ? ((Number) analyse[3]).floatValue() : null;
                joursInactivite = analyse[4] != null ? ((Number) analyse[4]).intValue()   : null;
                dateAnalyse     = (LocalDateTime) analyse[5];
            }

            // 5. Calculer le statut enrichi
            String statutEnrichi = calculerStatutEnrichi(
                    statutApprenant, niveauRisque, progression);

            // 6. Construire la map de réponse
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",                   apprenantId);
            item.put("prenom",               prenom);
            item.put("nom",                  nom);
            item.put("email",                email);
            item.put("telephone",            telephone);
            item.put("active",               active);
            item.put("dateInscription",      dateInscriptionUser);
            item.put("formationId",          formationId);
            item.put("formationTitre",       formationTitre);
            item.put("progression",          progression);
            item.put("statutApprenant",      statutApprenant != null ? statutApprenant : "A_FAIRE");
            item.put("dateInscriptionFormation", dateInscriptionForm);
            item.put("dernierActivite",      dernierActivite);
            item.put("niveauRisque",         niveauRisque);
            item.put("scoreRisque",          scoreRisque);
            item.put("joursInactivite",      joursInactivite);
            item.put("dateAnalyse",          dateAnalyse);
            item.put("statutEnrichi",        statutEnrichi);

            result.add(item);
        }

        return result;
    }

    // ── Calcul statut enrichi ─────────────────────────────────────

    private String calculerStatutEnrichi(String statutApprenant,
                                         String niveauRisque,
                                         Float  progression) {
        if ("CERTIFIE".equals(statutApprenant)) return "CERTIFIE";
        if ("ELEVE".equals(niveauRisque) || "MOYEN".equals(niveauRisque)) return "A_RISQUE";
        if ("TERMINE".equals(statutApprenant))  return "TERMINE";
        if ("EN_COURS".equals(statutApprenant)) return "EN_COURS";
        if (progression != null && progression > 0) return "EN_COURS";
        return "A_FAIRE";
    }
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> findInfractionsStats() {
        // Stats mini-quiz
        long totalMiniSuspects  = miniQuizResultatRepository.countBySuspectFraudeTrue();
        long totalMiniInfractions = miniQuizResultatRepository.sumNbInfractions();

        // Stats quiz final
        long totalFinalSuspects   = quizFinalResultatRepository.countBySuspectFraudeTrue();
        long totalFinalInfractions = quizFinalResultatRepository.sumNbInfractions();

        long totalSuspects    = totalMiniSuspects + totalFinalSuspects;
        long totalInfractions = totalMiniInfractions + totalFinalInfractions;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSuspects",         totalSuspects);
        stats.put("totalInfractions",      totalInfractions);
        stats.put("totalMiniSuspects",     totalMiniSuspects);
        stats.put("totalFinalSuspects",    totalFinalSuspects);
        stats.put("totalMiniInfractions",  totalMiniInfractions);
        stats.put("totalFinalInfractions", totalFinalInfractions);
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> findInfractions(int page, int size,
                                               String search, String type) {
        // Récupérer tous les résultats suspects
        List<Map<String, Object>> allRows = new ArrayList<>();

        // ── Mini-quiz suspects ────────────────────────────────────
        if ("ALL".equals(type) || "MINI_QUIZ".equals(type)) {
            List<Object[]> miniRows = miniQuizResultatRepository.findSuspectsWithDetails();
            for (Object[] row : miniRows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id",               row[0]);
                item.put("apprenantEmail",   row[1]);
                item.put("apprenantPrenom",  row[2]);
                item.put("apprenantNom",     row[3]);
                item.put("formationTitre",   row[4]);
                item.put("coursTitre",       row[5]);
                item.put("typeQuiz",         "Mini Quiz");
                item.put("nbInfractions",    row[6]);
                item.put("scoreBrut",        row[7]);
                item.put("penalite",         row[8]);
                item.put("scoreFinal",       row[9]);
                item.put("datePassage",      row[10]);
                item.put("detailInfractions",row[11]);
                allRows.add(item);
            }
        }

        // ── Quiz final suspects ───────────────────────────────────
        if ("ALL".equals(type) || "QUIZ_FINAL".equals(type)) {
            List<Object[]> finalRows = quizFinalResultatRepository.findSuspectsWithDetails();
            for (Object[] row : finalRows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id",               row[0]);
                item.put("apprenantEmail",   row[1]);
                item.put("apprenantPrenom",  row[2]);
                item.put("apprenantNom",     row[3]);
                item.put("formationTitre",   row[4]);
                item.put("coursTitre",       null);
                item.put("typeQuiz",         "Quiz Final");
                item.put("nbInfractions",    row[5]);
                item.put("scoreBrut",        row[6]);
                item.put("penalite",         row[7]);
                item.put("scoreFinal",       row[8]);
                item.put("datePassage",      row[9]);
                item.put("detailInfractions",row[10]);
                allRows.add(item);
            }
        }

        // ── Filtre recherche ──────────────────────────────────────
        if (search != null && !search.isBlank()) {
            String s = search.toLowerCase();
            allRows = allRows.stream()
                    .filter(r -> {
                        String email    = r.get("apprenantEmail")  != null ? r.get("apprenantEmail").toString().toLowerCase()  : "";
                        String prenom   = r.get("apprenantPrenom") != null ? r.get("apprenantPrenom").toString().toLowerCase() : "";
                        String nom      = r.get("apprenantNom")    != null ? r.get("apprenantNom").toString().toLowerCase()    : "";
                        String formation= r.get("formationTitre")  != null ? r.get("formationTitre").toString().toLowerCase()  : "";
                        return email.contains(s) || prenom.contains(s)
                                || nom.contains(s) || formation.contains(s);
                    })
                    .collect(Collectors.toList());
        }

        // ── Trier par date décroissante ───────────────────────────
        allRows.sort((a, b) -> {
            Object da = a.get("datePassage");
            Object db = b.get("datePassage");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.toString().compareTo(da.toString());
        });

        // ── Pagination ────────────────────────────────────────────
        int total      = allRows.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex  = Math.min(page * size, total);
        int toIndex    = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageData = allRows.subList(fromIndex, toIndex);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("infractions",  pageData);
        result.put("total",        total);
        result.put("totalPages",   totalPages);
        result.put("currentPage",  page);
        return result;
    }

    // ── Mapping ──────────────────────────────────────────────────

    private User toDomain(UserEntity e) {
        return User.builder()
                .id(e.getId())
                .prenom(e.getPrenom())
                .nom(e.getNom())
                .email(e.getEmail())
                .telephone(e.getTelephone())
                .motDePasse(e.getMotDePasse())
                .role(e.getRole())
                .emailVerifie(e.isEmailVerifie())
                .active(e.isActive())
                .dateInscription(e.getDateInscription())
                .derniereConnexion(e.getDerniereConnexion())
                .build();
    }
}