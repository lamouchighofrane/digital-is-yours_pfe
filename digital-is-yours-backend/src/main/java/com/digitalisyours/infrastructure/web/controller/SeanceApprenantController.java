package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.port.in.SeanceUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/apprenant/seances")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class SeanceApprenantController {

    private final SeanceUseCase seanceUseCase;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getMesSeances(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
        return ResponseEntity.ok(seanceUseCase.getSeancesApprenant(email)
                .stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("titre", s.getTitre());
                    m.put("formationId", s.getFormationId());
                    m.put("formationTitre", s.getFormationTitre() != null ? s.getFormationTitre() : "");
                    m.put("formateurNom", s.getFormateurNom() != null ? s.getFormateurNom() : "");
                    m.put("formateurPrenom", s.getFormateurPrenom() != null ? s.getFormateurPrenom() : "");
                    m.put("dateSeance", s.getDateSeance() != null ? s.getDateSeance().toString() : "");
                    m.put("dureeMinutes", s.getDureeMinutes());
                    m.put("description", s.getDescription() != null ? s.getDescription() : "");
                    m.put("lienJitsi", s.getLienJitsi());
                    m.put("statut", s.getStatut());
                    return m;
                }).toList());
    }

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }
}