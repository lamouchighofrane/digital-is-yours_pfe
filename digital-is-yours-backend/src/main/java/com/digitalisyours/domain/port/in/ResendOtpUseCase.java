package com.digitalisyours.domain.port.in;

import com.digitalisyours.infrastructure.web.dto.response.MessageResponse;

public interface ResendOtpUseCase {
    MessageResponse resendOtp(String email);
}
