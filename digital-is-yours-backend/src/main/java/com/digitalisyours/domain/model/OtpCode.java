package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class OtpCode {
    private Long id;
    private String email;
    private String code;
    private OtpType type;
    private LocalDateTime expiresAt;
    private boolean used;

    public enum OtpType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }
}
