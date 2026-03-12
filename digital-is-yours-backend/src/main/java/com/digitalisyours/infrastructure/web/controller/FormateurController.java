package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.port.in.FormateurUseCase;

import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

import java.util.Map;


@RestController
@RequestMapping("/api/formateur")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class FormateurController {
    private final FormateurUseCase formateurUseCase;
    private final JwtUtil jwtUtil;

    @GetMapping("/mes-formations")
    public ResponseEntity<?> getMesFormations(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        return ResponseEntity.ok(formateurUseCase.getMesFormations(email));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        return ResponseEntity.ok(formateurUseCase.getStats(email));
    }

    @GetMapping("/activites-recentes")
    public ResponseEntity<?> getActivitesRecentes(HttpServletRequest request) {
        return ResponseEntity.ok(Collections.emptyList());
    }

    @GetMapping("/alertes")
    public ResponseEntity<?> getAlertes(HttpServletRequest request) {
        return ResponseEntity.ok(Collections.emptyList());
    }

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) { return null; }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "Non autorisé"));
    }
}
