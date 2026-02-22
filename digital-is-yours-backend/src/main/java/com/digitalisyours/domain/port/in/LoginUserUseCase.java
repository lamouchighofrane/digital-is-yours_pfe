package com.digitalisyours.domain.port.in;

import com.digitalisyours.infrastructure.web.dto.request.LoginRequest;
import com.digitalisyours.infrastructure.web.dto.response.AuthResponse;

public interface LoginUserUseCase {
    AuthResponse login(LoginRequest request);
}
