package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.EmailVerificationToken;
import com.digitalisyours.domain.model.OtpCode;
import com.digitalisyours.domain.model.Role;
import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.in.*;
import com.digitalisyours.domain.port.out.EmailSenderPort;
import com.digitalisyours.domain.port.out.UserRepositoryPort;
import com.digitalisyours.infrastructure.persistence.EmailVerificationTokenRepository;
import com.digitalisyours.infrastructure.web.dto.request.*;
import com.digitalisyours.infrastructure.web.dto.response.AuthResponse;
import com.digitalisyours.infrastructure.web.dto.response.MessageResponse;
import com.digitalisyours.infrastructure.web.security.JwtUtil;
import com.digitalisyours.infrastructure.web.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements RegisterUserUseCase, LoginUserUseCase,
        VerifyOtpUseCase, ResendOtpUseCase, ForgotPasswordUseCase {
    private final UserRepositoryPort userRepository;
    private final OtpService otpService;
    private final EmailSenderPort emailSender;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository tokenRepository;

    // ─── REGISTER ─────────────────────────────────────────────────────────────
    @Override
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        User user = User.builder()
                .prenom(request.getPrenom())
                .nom(request.getNom())
                .email(request.getEmail())
                .telephone(request.getTelephone())
                .motDePasse(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .emailVerifie(false)
                .build();

        userRepository.save(user);
        log.info("Nouvel utilisateur créé : {}", request.getEmail());

        // ← MODIFIÉ : envoyer un lien de vérification au lieu d'un OTP
        emailService.sendVerificationLink(request.getEmail());

        return MessageResponse.ok("Compte créé ! Vérifiez votre email et cliquez sur le lien d'activation.");
    }

    // ─── VERIFY OTP (gardé uniquement pour reset password) ────────────────────
    @Override
    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        boolean valid = otpService.verify(request.getEmail(), request.getCode(),
                OtpCode.OtpType.EMAIL_VERIFICATION);

        if (!valid) {
            throw new RuntimeException("Code OTP invalide ou expiré");
        }

        userRepository.markEmailVerified(request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        emailSender.sendWelcome(user.getEmail(), user.getPrenom(), user.getRole().name());

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .prenom(user.getPrenom())
                .nom(user.getNom())
                .role(user.getRole().name())
                .message("Email vérifié avec succès. Bienvenue !")
                .build();
    }

    // ─── LOGIN ────────────────────────────────────────────────────────────────
    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email ou mot de passe incorrect"));

        if (!passwordEncoder.matches(request.getPassword(), user.getMotDePasse())) {
            throw new RuntimeException("Email ou mot de passe incorrect");
        }

        if (user.getRole() != request.getRole()) {
            throw new RuntimeException("Ce compte n'est pas un compte " + request.getRole().name().toLowerCase());
        }

        // ADMIN n'a pas besoin de vérification email
        if (user.getRole() != Role.ADMIN && !user.isEmailVerifie()) {
            throw new RuntimeException("Veuillez vérifier votre email avant de vous connecter");
        }

        userRepository.updateLastLogin(user.getEmail());
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .prenom(user.getPrenom())
                .nom(user.getNom())
                .role(user.getRole().name())
                .message("Connexion réussie")
                .build();
    }

    // ─── RESEND OTP ───────────────────────────────────────────────────────────
    @Override
    public MessageResponse resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email non trouvé"));

        if (user.isEmailVerifie()) {
            throw new RuntimeException("Cet email est déjà vérifié");
        }

        // ← Renvoyer le lien de vérification au lieu d'un OTP
        emailService.sendVerificationLink(email);

        return MessageResponse.ok("Lien de vérification renvoyé à " + email);
    }

    // ─── FORGOT PASSWORD ──────────────────────────────────────────────────────
    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Aucun compte avec cet email"));

        // Le reset password garde le code OTP (comportement inchangé)
        String code = otpService.generateAndSave(request.getEmail(), OtpCode.OtpType.PASSWORD_RESET);
        emailSender.sendPasswordReset(request.getEmail(), user.getPrenom(), code);

        return MessageResponse.ok("Un code de réinitialisation a été envoyé à votre email");
    }

    // ─── RESET PASSWORD ───────────────────────────────────────────────────────
    @Override
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        boolean valid = otpService.verify(request.getEmail(), request.getCode(),
                OtpCode.OtpType.PASSWORD_RESET);

        if (!valid) {
            throw new RuntimeException("Code OTP invalide ou expiré");
        }

        String encoded = passwordEncoder.encode(request.getNewPassword());
        userRepository.updatePassword(request.getEmail(), encoded);

        return MessageResponse.ok("Mot de passe réinitialisé avec succès");
    }
}
