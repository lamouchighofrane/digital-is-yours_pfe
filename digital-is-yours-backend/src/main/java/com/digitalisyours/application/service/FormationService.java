package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.in.FormationUseCase;
import com.digitalisyours.domain.port.out.FormationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FormationService implements FormationUseCase {
    private final FormationRepositoryPort formationRepository;

    @Override
    public Map<String, Long> getStats() {
        return Map.of(
                "total",      formationRepository.countAll(),
                "publiees",   formationRepository.countPubliees(),
                "brouillons", formationRepository.countBrouillons()
        );
    }

    @Override
    public List<User> getAllFormateurs() {
        return formationRepository.findAllFormateursActifs();
    }

    @Override
    public List<Formation> getAllFormations() {
        return formationRepository.findAllWithDetails();
    }

    @Override
    public Formation getFormationById(Long id) {
        return formationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));
    }

    @Override
    public Formation createFormation(Formation formation) {
        if (formation.getTitre() == null || formation.getTitre().isBlank()) {
            throw new RuntimeException("Le titre est obligatoire");
        }
        formation.setDateCreation(LocalDateTime.now());
        if (formation.getStatut() == null) formation.setStatut("BROUILLON");
        if (formation.getNombreInscrits() == null) formation.setNombreInscrits(0);
        if (formation.getNombreCertifies() == null) formation.setNombreCertifies(0);
        if (formation.getDureeEstimee() == null) formation.setDureeEstimee(1);
        if (formation.getPrix() == null) formation.setPrix(29.99);  // ★ valeur par défaut
        if ("PUBLIE".equals(formation.getStatut())) {
            formation.setDatePublication(LocalDateTime.now());
        }
        return formationRepository.save(formation);
    }

    @Override
    public Formation updateFormation(Long id, Formation formation) {
        Formation existing = formationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        if (formation.getTitre() == null || formation.getTitre().isBlank()) {
            throw new RuntimeException("Le titre est obligatoire");
        }

        boolean wasNotPublie = !"PUBLIE".equals(existing.getStatut());
        String newStatut = formation.getStatut() != null ? formation.getStatut() : existing.getStatut();

        existing.setTitre(formation.getTitre());
        existing.setDescription(formation.getDescription());
        existing.setObjectifsApprentissage(formation.getObjectifsApprentissage());
        existing.setPrerequis(formation.getPrerequis());
        existing.setPourQui(formation.getPourQui());
        existing.setImageCouverture(formation.getImageCouverture());
        existing.setDureeEstimee(formation.getDureeEstimee() != null
                ? formation.getDureeEstimee() : existing.getDureeEstimee());
        existing.setNiveau(formation.getNiveau());
        existing.setStatut(newStatut);
        existing.setCategorieId(formation.getCategorieId());
        existing.setFormateurId(formation.getFormateurId());
        // ★ AJOUT : mise à jour du prix
        existing.setPrix(formation.getPrix() != null ? formation.getPrix() : existing.getPrix());

        if ("PUBLIE".equals(newStatut) && wasNotPublie) {
            existing.setDatePublication(LocalDateTime.now());
        }

        return formationRepository.save(existing);
    }

    @Override
    public Formation affecterFormateur(Long formationId, Long formateurId) {
        Formation formation = formationRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        User formateur = formationRepository.findFormateurById(formateurId)
                .orElseThrow(() -> new RuntimeException("Formateur non trouvé"));

        if (formateur.getRole() != Role.FORMATEUR) {
            throw new RuntimeException("L'utilisateur sélectionné n'est pas un formateur");
        }

        boolean estRemplacement = formation.getFormateurId() != null
                && !formation.getFormateurId().equals(formateurId);

        // Notification au nouveau formateur
        formationRepository.saveNotificationAffectation(
                formateurId, "FORMATION_AFFECTEE",
                "Nouvelle formation assignée",
                "L'administrateur vous a affecté(e) à la formation \""
                        + formation.getTitre() + "\"."
                        + (estRemplacement ? " Vous remplacez un autre formateur." : ""),
                formation.getId(), formation.getTitre()
        );

        // Notification à l'ancien formateur si remplacement
        if (estRemplacement) {
            formationRepository.saveNotificationAffectation(
                    formation.getFormateurId(), "FORMATION_RETIREE",
                    "Formation retirée de votre portfolio",
                    "La formation \"" + formation.getTitre()
                            + "\" a été réaffectée à un autre formateur par l'administrateur.",
                    formation.getId(), formation.getTitre()
            );
        }

        formation.setFormateurId(formateurId);
        formation.setFormateurNom(formateur.getNom());
        formation.setFormateurPrenom(formateur.getPrenom());
        formation.setFormateurEmail(formateur.getEmail());
        return formationRepository.save(formation);
    }

    @Override
    public Formation retirerFormateur(Long formationId) {
        Formation formation = formationRepository.findById(formationId)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        if (formation.getFormateurId() != null) {
            formationRepository.saveNotificationAffectation(
                    formation.getFormateurId(), "FORMATION_RETIREE",
                    "Formation retirée de votre portfolio",
                    "La formation \"" + formation.getTitre()
                            + "\" vous a été retirée par l'administrateur.",
                    formation.getId(), formation.getTitre()
            );
        }

        formation.setFormateurId(null);
        formation.setFormateurNom(null);
        formation.setFormateurPrenom(null);
        formation.setFormateurEmail(null);
        return formationRepository.save(formation);
    }

    @Override
    public Formation toggleStatut(Long id) {
        Formation formation = formationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Formation non trouvée"));

        String newStatut = "PUBLIE".equals(formation.getStatut()) ? "BROUILLON" : "PUBLIE";
        formation.setStatut(newStatut);

        if ("PUBLIE".equals(newStatut) && formation.getDatePublication() == null) {
            formation.setDatePublication(LocalDateTime.now());
        }

        return formationRepository.save(formation);
    }

    @Override
    public void deleteFormation(Long id) {
        if (!formationRepository.existsById(id)) {
            throw new RuntimeException("Formation non trouvée");
        }
        formationRepository.deleteById(id);
    }
}
