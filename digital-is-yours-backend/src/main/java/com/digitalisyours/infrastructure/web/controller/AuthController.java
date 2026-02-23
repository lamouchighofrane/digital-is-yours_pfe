package com.digitalisyours.infrastructure.web.controller;


import com.digitalisyours.domain.model.EmailVerificationToken;
import com.digitalisyours.domain.port.in.*;
import com.digitalisyours.infrastructure.persistence.EmailVerificationTokenRepository;
import com.digitalisyours.infrastructure.persistence.entity.UserEntity;
import com.digitalisyours.infrastructure.persistence.repository.UserJpaRepository;
import com.digitalisyours.infrastructure.web.dto.request.*;
import com.digitalisyours.infrastructure.web.dto.response.AuthResponse;
import com.digitalisyours.infrastructure.web.dto.response.MessageResponse;
import com.digitalisyours.infrastructure.web.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class AuthController {
    private final RegisterUserUseCase registerUseCase;
    private final LoginUserUseCase loginUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final ResendOtpUseCase resendOtpUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserJpaRepository userRepository;
    private final EmailService emailService;

    // ── Inscription ──────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(registerUseCase.register(request));
    }

    // ── Connexion ────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        // ⚠️ VÉRIFICATION COMPTE DÉSACTIVÉ avant de générer le token JWT
        UserEntity userEntity = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (userEntity != null && !userEntity.isActive()) {
            log.warn("Tentative connexion compte désactivé: {}", request.getEmail());
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "COMPTE_DESACTIVE"
            ));
        }

        return ResponseEntity.ok(loginUseCase.login(request));
    }

    // ── Vérification OTP (pour reset password) ───────────────────
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(verifyOtpUseCase.verifyOtp(request));
    }

    // ── Renvoyer OTP (pour reset password) ───────────────────────
    @PostMapping("/resend-otp")
    public ResponseEntity<MessageResponse> resendOtp(@RequestParam String email) {
        return ResponseEntity.ok(resendOtpUseCase.resendOtp(email));
    }

    // ── Mot de passe oublié ──────────────────────────────────────
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(forgotPasswordUseCase.forgotPassword(request));
    }

    // ── Réinitialisation mot de passe ────────────────────────────
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(forgotPasswordUseCase.resetPassword(request));
    }

    // ── Vérification email via lien ──────────────────────────────
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            EmailVerificationToken verifToken = tokenRepository
                    .findByToken(token)
                    .orElseThrow(() -> new RuntimeException("Lien invalide"));

            if (verifToken.isExpired()) {
                return ResponseEntity.status(302)
                        .header("Location", "http://localhost:4200/verifyEmail?error=expired")
                        .build();
            }

            if (verifToken.isUsed()) {
                return ResponseEntity.status(302)
                        .header("Location", "http://localhost:4200/verifyEmail?error=already-used")
                        .build();
            }

            userRepository.markEmailVerified(verifToken.getEmail());
            verifToken.setUsed(true);
            tokenRepository.save(verifToken);

            log.info("Compte vérifié: {}", verifToken.getEmail());

            return ResponseEntity.status(302)
                    .header("Location", "http://localhost:4200/login?verified=true")
                    .build();

        } catch (Exception e) {
            log.error("Erreur token: {}", e.getMessage());
            return ResponseEntity.status(302)
                    .header("Location", "http://localhost:4200/verifyEmail?error=invalid")
                    .build();
        }
    }

    // ── Renvoyer le lien de vérification ─────────────────────────
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        try {
            emailService.sendVerificationLink(email);
            return ResponseEntity.ok(Map.of(
                    "message", "Un nouveau lien envoyé à " + email,
                    "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Erreur lors de l'envoi.",
                    "success", false
            ));
        }
    }
}
