package com.digitalisyours.infrastructure.web.controller;

import com.digitalisyours.domain.model.ListeCoursFormation;
import com.digitalisyours.domain.port.in.ConsulterCoursFormationUseCase;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/apprenant/formations/{formationId}")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ConsulterCoursFormationController {
    private final ConsulterCoursFormationUseCase consulterCoursFormationUseCase;
    private final JwtUtil jwtUtil;

    @GetMapping("/cours")
    public ResponseEntity<?> getCours(
            @PathVariable Long formationId,
            HttpServletRequest request) {

        String email = extractEmail(request);
        if (email == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Non autorisé"));
        }

        ListeCoursFormation result =
                consulterCoursFormationUseCase.getCoursDeFormation(formationId, email);

        return ResponseEntity.ok(result);
    }

    // ── Helper JWT ────────────────────────────────────────────────────────

    private String extractEmail(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token = auth.substring(7);
            return jwtUtil.isValid(token) ? jwtUtil.extractEmail(token) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
