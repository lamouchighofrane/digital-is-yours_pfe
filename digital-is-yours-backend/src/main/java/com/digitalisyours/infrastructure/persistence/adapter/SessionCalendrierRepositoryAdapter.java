package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.SessionCalendrier;
import com.digitalisyours.domain.port.out.SessionCalendrierRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.SessionCalendrierEntity;
import com.digitalisyours.infrastructure.persistence.repository.ApprenantJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.FormationJpaRepository;
import com.digitalisyours.infrastructure.persistence.repository.SessionCalendrierJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SessionCalendrierRepositoryAdapter implements SessionCalendrierRepositoryPort {
    private final SessionCalendrierJpaRepository sessionRepo;
    private final ApprenantJpaRepository apprenantRepo;
    private final FormationJpaRepository formationRepo;

    @Override
    public List<SessionCalendrier> findByApprenantEmail(String email) {
        return sessionRepo.findByApprenantEmail(email)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<SessionCalendrier> findRappelsAEnvoyer(
            LocalDateTime from, LocalDateTime to) {
        return sessionRepo.findRappelsAEnvoyer(from, to)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<SessionCalendrier> findById(Long id) {
        return sessionRepo.findById(id).map(this::toDomain);
    }

    @Override
    public SessionCalendrier save(SessionCalendrier session) {
        SessionCalendrierEntity entity = session.getId() != null
                ? sessionRepo.findById(session.getId())
                .orElse(new SessionCalendrierEntity())
                : new SessionCalendrierEntity();

        var apprenant = apprenantRepo.findById(session.getApprenantId())
                .orElseThrow(() -> new RuntimeException("Apprenant non trouvé"));
        entity.setApprenant(apprenant);

        if (session.getFormationId() != null) {
            formationRepo.findById(session.getFormationId())
                    .ifPresent(entity::setFormation);
        } else {
            entity.setFormation(null);
        }

        entity.setTitrePersonnalise(session.getTitrePersonnalise());
        entity.setDateSession(session.getDateSession());
        entity.setDureeMinutes(session.getDureeMinutes());
        entity.setTypeSession(session.getTypeSession());
        entity.setNotes(session.getNotes());
        entity.setRappel24h(session.isRappel24h());
        entity.setRappelEnvoye(session.isRappelEnvoye());
        entity.setTerminee(session.isTerminee());

        return toDomain(sessionRepo.save(entity));
    }

    @Override
    public void deleteById(Long id) {
        sessionRepo.deleteById(id);
    }

    private SessionCalendrier toDomain(SessionCalendrierEntity e) {
        SessionCalendrier s = SessionCalendrier.builder()
                .id(e.getId())
                .dateSession(e.getDateSession())
                .dureeMinutes(e.getDureeMinutes())
                .typeSession(e.getTypeSession())
                .titrePersonnalise(e.getTitrePersonnalise())
                .notes(e.getNotes())
                .rappel24h(e.isRappel24h())
                .rappelEnvoye(e.isRappelEnvoye())
                .isTerminee(e.isTerminee())
                .dateCreation(e.getDateCreation())
                .build();

        try {
            if (e.getApprenant() != null)
                s.setApprenantId(e.getApprenant().getId());
        } catch (Exception ex) {}

        try {
            if (e.getFormation() != null) {
                s.setFormationId(e.getFormation().getId());
                s.setFormationTitre(e.getFormation().getTitre());
            }
        } catch (Exception ex) {}

        return s;
    }
}
