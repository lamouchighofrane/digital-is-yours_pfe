package com.digitalisyours.domain.port.in;

import com.digitalisyours.infrastructure.web.dto.request.ForgotPasswordRequest;
import com.digitalisyours.infrastructure.web.dto.request.ResetPasswordRequest;
import com.digitalisyours.infrastructure.web.dto.response.MessageResponse;

public interface ForgotPasswordUseCase {
    MessageResponse forgotPassword(ForgotPasswordRequest request);
    MessageResponse resetPassword(ResetPasswordRequest request);
}
