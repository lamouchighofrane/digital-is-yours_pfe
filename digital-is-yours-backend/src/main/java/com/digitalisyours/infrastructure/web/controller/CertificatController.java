package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.port.in.CertificatUseCase;
import com.digitalisyours.domain.port.in.ProfilApprenantUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apprenant/certificats")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CertificatController {

    private final CertificatUseCase      certificatUseCase;
    private final ProfilApprenantUseCase profilUseCase;
    private final JwtUtil                jwtUtil;

    // ── Helpers identiques à ProfilApprenantController ────────────────────────
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

    private Long getApprenantId(String email) {
        return profilUseCase.getProfil(email).getId();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET /api/apprenant/certificats
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping
    public ResponseEntity<?> getMesCertificats(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Long apprenantId = getApprenantId(email);
            List<Map<String, Object>> response = certificatUseCase
                    .getMesCertificats(apprenantId)
                    .stream().map(this::toResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET /api/apprenant/certificats/stats
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Long apprenantId = getApprenantId(email);
            List<Certificat> certs = certificatUseCase.getMesCertificats(apprenantId);
            double scoreMoyen = certs.stream()
                    .filter(c -> c.getNoteFinal() != null)
                    .mapToDouble(Certificat::getNoteFinal)
                    .average().orElse(0);
            return ResponseEntity.ok(Map.of(
                    "total",       certs.size(),
                    "scoreMoyen",  Math.round(scoreMoyen * 10.0) / 10.0,
                    "certificats", certs.stream().map(this::toResponse).collect(Collectors.toList())
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET /api/apprenant/certificats/{id}
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/{id}")
    public ResponseEntity<?> getCertificat(
            @PathVariable Long id, HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Certificat cert = certificatUseCase.getCertificatById(id, getApprenantId(email));
            return ResponseEntity.ok(toResponse(cert));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET /api/apprenant/certificats/{id}/download
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadCertificat(
            @PathVariable Long id, HttpServletRequest request) {
        String email = extractEmail(request);
        if (email == null) return unauthorized();
        try {
            Long apprenantId = getApprenantId(email);
            byte[]     pdfBytes = certificatUseCase.downloadCertificatPDF(id, apprenantId);
            Certificat cert     = certificatUseCase.getCertificatById(id, apprenantId);
            String filename = "certificat_"
                    + (cert.getNumeroCertificat() != null
                    ? cert.getNumeroCertificat().replace("#","").replace("-","_")
                    : id)
                    + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────
    private Map<String, Object> toResponse(Certificat c) {
        return Map.of(
                "id",               c.getId(),
                "titre",            c.getTitre()           != null ? c.getTitre()                   : "",
                "noteFinal",        c.getNoteFinal()        != null ? c.getNoteFinal()                : 0f,
                "dateCreation",     c.getDateCreation()     != null ? c.getDateCreation().toString()  : "",
                "numeroCertificat", c.getNumeroCertificat() != null ? c.getNumeroCertificat()         : "",
                "formationTitre",   c.getFormationTitre()   != null ? c.getFormationTitre()           : "",
                "formationNiveau",  c.getFormationNiveau()  != null ? c.getFormationNiveau()          : "",
                "urlPDF",           c.getUrlPDF()           != null ? c.getUrlPDF()                   : "",
                "estEnvoye",        c.getEstEnvoye()        != null ? c.getEstEnvoye()                : false,
                "contextu",         c.getContextu()         != null ? c.getContextu()                 : ""
        );
    }
}