package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.Formation;
import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.out.FormationRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.CategorieEntity;
import com.digitalisyours.infrastructure.persistence.entity.FormationEntity;
import com.digitalisyours.infrastructure.persistence.entity.NotificationEntity;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.CategorieJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.NotificationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FormationRepositoryAdapter implements FormationRepositoryPort {
    private final FormationJpaRepository formationJpaRepository;
    private final CategorieJpaRepository categorieJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Formation> findAllWithDetails() {
        return formationJpaRepository.findAllWithCategorie()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Formation> findById(Long id) {
        return formationJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return formationJpaRepository.existsById(id);
    }

    @Override
    @Transactional
    public Formation save(Formation formation) {
        FormationEntity entity = toEntity(formation);
        return toDomain(formationJpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        formationJpaRepository.deleteById(id);
    }

    @Override
    public long countAll() {
        return formationJpaRepository.countAll();
    }

    @Override
    public long countPubliees() {
        return formationJpaRepository.countPubliees();
    }

    @Override
    public long countBrouillons() {
        return formationJpaRepository.countBrouillons();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAllFormateursActifs() {
        return userJpaRepository.findByRoleAndActiveTrue(Role.FORMATEUR)
                .stream().map(this::userToDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findFormateurById(Long id) {
        return userJpaRepository.findById(id).map(this::userToDomain);
    }

    @Override
    @Transactional
    public void saveNotificationAffectation(Long formateurId, String type, String titre,
                                            String message, Long formationId, String formationTitre) {
        userJpaRepository.findById(formateurId).ifPresent(user -> {
            NotificationEntity notif = NotificationEntity.builder()
                    .user(user)
                    .type(type)
                    .titre(titre)
                    .message(message)
                    .formationId(formationId)
                    .formationTitre(formationTitre)
                    .build();
            notificationJpaRepository.save(notif);
        });
    }

    // ── Mapping ──────────────────────────────────────────────────

    private Formation toDomain(FormationEntity e) {
        Formation f = Formation.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .description(e.getDescription())
                .objectifsApprentissage(e.getObjectifsApprentissage())
                .prerequis(e.getPrerequis())
                .pourQui(e.getPourQui())
                .imageCouverture(e.getImageCouverture())
                .dureeEstimee(e.getDureeEstimee())
                .niveau(e.getNiveau())
                .statut(e.getStatut())
                .dateCreation(e.getDateCreation())
                .datePublication(e.getDatePublication())
                .nombreInscrits(e.getNombreInscrits())
                .nombreCertifies(e.getNombreCertifies())
                .noteMoyenne(e.getNoteMoyenne())
                .tauxReussite(e.getTauxReussite())
                .build();

        try {
            if (e.getCategorie() != null) {
                f.setCategorieId(e.getCategorie().getId());
                f.setCategorieNom(e.getCategorie().getNom());
            }
        } catch (Exception ex) {
            // proxy non initialisé — on ignore
        }
        try {
            if (e.getFormateur() != null) {
                f.setFormateurId(e.getFormateur().getId());
                f.setFormateurNom(e.getFormateur().getNom());
                f.setFormateurPrenom(e.getFormateur().getPrenom());
                f.setFormateurEmail(e.getFormateur().getEmail());
            }
        } catch (Exception ex) {
            // proxy non initialisé — on ignore
        }
        return f;
    }

    private FormationEntity toEntity(Formation f) {
        CategorieEntity categorie = f.getCategorieId() != null
                ? categorieJpaRepository.findById(f.getCategorieId()).orElse(null) : null;
        UserEntity formateur = f.getFormateurId() != null
                ? userJpaRepository.findById(f.getFormateurId()).orElse(null) : null;

        return FormationEntity.builder()
                .id(f.getId())
                .titre(f.getTitre())
                .description(f.getDescription())
                .objectifsApprentissage(f.getObjectifsApprentissage())
                .prerequis(f.getPrerequis())
                .pourQui(f.getPourQui())
                .imageCouverture(f.getImageCouverture())
                .dureeEstimee(f.getDureeEstimee())
                .niveau(f.getNiveau())
                .statut(f.getStatut())
                .categorie(categorie)
                .formateur(formateur)
                .dateCreation(f.getDateCreation())
                .datePublication(f.getDatePublication())
                .nombreInscrits(f.getNombreInscrits())
                .nombreCertifies(f.getNombreCertifies())
                .noteMoyenne(f.getNoteMoyenne())
                .tauxReussite(f.getTauxReussite())
                .build();
    }

    private User userToDomain(UserEntity e) {
        return User.builder()
                .id(e.getId())
                .prenom(e.getPrenom())
                .nom(e.getNom())
                .email(e.getEmail())
                .role(e.getRole())
                .active(e.isActive())
                .build();
    }
}
