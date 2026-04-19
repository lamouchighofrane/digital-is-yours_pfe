package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.SeanceEnLigne;
import java.util.List;
import java.util.Map;

public interface SeanceUseCase {
    SeanceEnLigne creerSeance(String emailFormateur, Map<String, Object> payload);
    List<SeanceEnLigne> getMesSeances(String emailFormateur);
    List<SeanceEnLigne> getSeancesApprenant(String emailApprenant);
    SeanceEnLigne annulerSeance(Long seanceId, String emailFormateur);
    void supprimerSeance(Long seanceId, String emailFormateur);
}