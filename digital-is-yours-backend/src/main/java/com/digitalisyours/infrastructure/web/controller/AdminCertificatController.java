package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.application.service.CertificatEmailService;
import com.digitalisyours.domain.model.Certificat;
import com.digitalisyours.domain.port.in.AdminCertificatUseCase;
import com.digitalisyours.infrastructure.persistence.repository.CertificatJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/certificats")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class AdminCertificatController {

    private final AdminCertificatUseCase      adminCertificatUseCase;
    private final CertificatEmailService      certificatEmailService;
    private final CertificatJpaRepository     certificatJpaRepository;

    // ═══════════════════════════════════════════════════════
    // GET /api/admin/certificats/stats
    // ═══════════════════════════════════════════════════════
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(adminCertificatUseCase.getStatsCertificats());
    }

    // ═══════════════════════════════════════════════════════
    // GET /api/admin/certificats  (liste paginée + filtres)
    // ═══════════════════════════════════════════════════════
    @GetMapping
    public ResponseEntity<?> getAllCertificats(
            @RequestParam(required = false) String formation,
            @RequestParam(required = false) String apprenant,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        LocalDate debut = parseDate(dateDebut);
        LocalDate fin   = parseDate(dateFin);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "dateCreation"));

        Page<Certificat> pageResult = adminCertificatUseCase
                .getAllCertificats(formation, apprenant, debut, fin, search, pageable);

        List<Map<String, Object>> content = pageResult.getContent()
                .stream().map(this::toResponse).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("certificats",  content);
        response.put("total",        pageResult.getTotalElements());
        response.put("totalPages",   pageResult.getTotalPages());
        response.put("currentPage",  pageResult.getNumber());
        response.put("size",         pageResult.getSize());

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════
    // POST /api/admin/certificats/{id}/envoyer-email
    // ═══════════════════════════════════════════════════════
    @PostMapping("/{id}/envoyer-email")
    public ResponseEntity<?> envoyerEmail(@PathVariable Long id) {
        try {
            var certOpt = certificatJpaRepository.findById(id);
            if (certOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            var e = certOpt.get();
            Certificat cert = Certificat.builder()
                    .id(e.getId())
                    .titre(e.getTitre())
                    .noteFinal(e.getNoteFinal())
                    .dateCreation(e.getDateCreation())
                    .numeroCertificat(e.getNumeroCertificat())
                    .apprenantEmail(e.getApprenantEmail())
                    .apprenantPrenom(e.getApprenantPrenom())
                    .apprenantNom(e.getApprenantNom())
                    .formationTitre(e.getFormationTitre())
                    .urlPDF(e.getUrlPDF())
                    .build();
            certificatEmailService.envoyerCertificat(cert);
            log.info("Admin — email certificat {} renvoyé", e.getNumeroCertificat());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email envoyé à " + e.getApprenantEmail()
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════
    // GET /api/admin/certificats/export  (CSV)
    // ═══════════════════════════════════════════════════════
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String formation,
            @RequestParam(required = false) String apprenant,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) String search) {

        LocalDate debut = parseDate(dateDebut);
        LocalDate fin   = parseDate(dateFin);

        Pageable all = PageRequest.of(0, Integer.MAX_VALUE,
                Sort.by(Sort.Direction.DESC, "dateCreation"));

        Page<Certificat> all2 = adminCertificatUseCase
                .getAllCertificats(formation, apprenant, debut, fin, search, all);

        StringBuilder sb = new StringBuilder();
        sb.append("Apprenant,Formation,Note (%),Date délivrance,N° Certificat,Envoyé\n");
        for (Certificat c : all2.getContent()) {
            sb.append(csv(c.getApprenantPrenom() + " " + c.getApprenantNom())).append(",");
            sb.append(csv(c.getFormationTitre())).append(",");
            sb.append(c.getNoteFinal() != null ? String.format("%.0f", c.getNoteFinal()) : "").append(",");
            sb.append(c.getDateCreation() != null
                    ? c.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "").append(",");
            sb.append(csv(c.getNumeroCertificat())).append(",");
            sb.append(Boolean.TRUE.equals(c.getEstEnvoye()) ? "Oui" : "Non").append("\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "certificats_" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    // ── Helpers ──────────────────────────────────────────────

    private Map<String, Object> toResponse(Certificat c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",               c.getId());
        m.put("numeroCertificat", c.getNumeroCertificat() != null ? c.getNumeroCertificat() : "");
        m.put("apprenantPrenom",  c.getApprenantPrenom()  != null ? c.getApprenantPrenom()  : "");
        m.put("apprenantNom",     c.getApprenantNom()     != null ? c.getApprenantNom()     : "");
        m.put("apprenantEmail",   c.getApprenantEmail()   != null ? c.getApprenantEmail()   : "");
        m.put("formationTitre",   c.getFormationTitre()   != null ? c.getFormationTitre()   : "");
        m.put("formationNiveau",  c.getFormationNiveau()  != null ? c.getFormationNiveau()  : "");
        m.put("noteFinal",        c.getNoteFinal()        != null ? c.getNoteFinal()        : 0f);
        m.put("notePassage",      c.getNotePassage()      != null ? c.getNotePassage()      : 0f);
        m.put("dateCreation",     c.getDateCreation()     != null ? c.getDateCreation().toString() : "");
        m.put("urlPDF",           c.getUrlPDF()           != null ? c.getUrlPDF()           : "");
        m.put("estEnvoye",        Boolean.TRUE.equals(c.getEstEnvoye()));
        m.put("partageLinkedIn",  Boolean.TRUE.equals(c.getPartageLinkedIn()));
        return m;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { return null; }
    }

    private String csv(String val) {
        if (val == null) return "";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }
}