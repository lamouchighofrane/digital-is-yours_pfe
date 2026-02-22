package com.digitalisyours.domain.port.in;


import com.digitalisyours.infrastructure.web.dto.request.RegisterRequest;
import com.digitalisyours.infrastructure.web.dto.response.MessageResponse;

public interface RegisterUserUseCase {
    MessageResponse register(RegisterRequest request);
}
