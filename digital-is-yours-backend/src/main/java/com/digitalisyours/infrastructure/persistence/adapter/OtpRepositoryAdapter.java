package com.digitalisyours.infrastructure.persistence.adapter;

import com.digitalisyours.domain.model.OtpCode;
import com.digitalisyours.domain.port.out.OtpRepositoryPort;
import com.digitalisyours.infrastructure.persistence.entity.OtpEntity;
import com.digitalisyours.infrastructure.persistence.repository.OtpJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OtpRepositoryAdapter implements OtpRepositoryPort {
    private final OtpJpaRepository jpaRepository;

    @Override
    public OtpCode save(OtpCode otpCode) {
        OtpEntity entity = toEntity(otpCode);
        OtpEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<OtpCode> findLatestValid(String email, OtpCode.OtpType type) {
        return jpaRepository.findLatestValid(email, type, LocalDateTime.now())
                .map(this::toDomain);
    }

    @Override
    public void markAsUsed(Long id) {
        jpaRepository.markAsUsed(id);
    }

    @Override
    public void deleteByEmail(String email) {
        jpaRepository.deleteByEmail(email);
    }

    private OtpEntity toEntity(OtpCode otp) {
        return OtpEntity.builder()
                .id(otp.getId())
                .email(otp.getEmail())
                .code(otp.getCode())
                .type(otp.getType())
                .expiresAt(otp.getExpiresAt())
                .used(otp.isUsed())
                .build();
    }

    private OtpCode toDomain(OtpEntity entity) {
        return OtpCode.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .code(entity.getCode())
                .type(entity.getType())
                .expiresAt(entity.getExpiresAt())
                .used(entity.isUsed())
                .build();
    }
}
