package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    void updatePassword(String email, String newPassword);
    void markEmailVerified(String email);
    void updateLastLogin(String email);
}
