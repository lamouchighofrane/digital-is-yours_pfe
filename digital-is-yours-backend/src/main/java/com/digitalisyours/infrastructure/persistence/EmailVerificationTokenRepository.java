package com.digitalisyours.infrastructure.persistence;


import com.digitalisyours.domain.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken e WHERE e.email = :email")

    void deleteByEmail(String email);
}
