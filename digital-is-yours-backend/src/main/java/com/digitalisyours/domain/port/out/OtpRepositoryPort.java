package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.OtpCode;

import java.util.Optional;

public interface OtpRepositoryPort {
    OtpCode save(OtpCode otpCode);
    Optional<OtpCode> findLatestValid(String email, OtpCode.OtpType type);
    void markAsUsed(Long id);
    void deleteByEmail(String email);
}
