package com.digitalisyours.domain.port.out;

import com.digitalisyours.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface AdminRepositoryPort {
    List<User> findAllNonAdmin();
    Optional<User> findById(Long id);
    boolean existsByEmail(String email);
    User save(User user);
    void deleteById(Long id);
    long countByRole(String role);
    long countEmailNonVerifie();
    long countDesactives();
}
