package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.OtpCode;
import com.digitalisyours.domain.port.out.OtpRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final OtpRepositoryPort otpRepository;

    @Value("${otp.expiration-minutes:5}")
    private int expirationMinutes;

    private final SecureRandom random = new SecureRandom();

    public String generateAndSave(String email, OtpCode.OtpType type) {
        // Supprimer les anciens OTPs
        otpRepository.deleteByEmail(email);

        String code = String.format("%06d", random.nextInt(999999));

        OtpCode otp = OtpCode.builder()
                .email(email)
                .code(code)
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .used(false)
                .build();

        otpRepository.save(otp);
        return code;
    }

    public boolean verify(String email, String code, OtpCode.OtpType type) {
        Optional<OtpCode> optOtp = otpRepository.findLatestValid(email, type);
        if (optOtp.isEmpty()) return false;

        OtpCode otp = optOtp.get();
        if (!otp.getCode().equals(code)) return false;

        otpRepository.markAsUsed(otp.getId());
        return true;
    }
}
