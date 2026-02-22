package com.digitalisyours.domain.port.in;

import com.digitalisyours.infrastructure.web.dto.request.OtpVerifyRequest;
import com.digitalisyours.infrastructure.web.dto.response.AuthResponse;

public interface VerifyOtpUseCase {
    AuthResponse verifyOtp(OtpVerifyRequest request);
}
