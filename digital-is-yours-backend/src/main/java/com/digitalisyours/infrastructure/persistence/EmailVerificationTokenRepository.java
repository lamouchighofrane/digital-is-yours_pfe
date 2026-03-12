package com.digitalisyours.infrastructure.persistence;



import com.digitalisyours.infrastructure.persistence.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, Long> {
    //                     ↑ Entity, pas le domain model

    Optional<EmailVerificationTokenEntity> findByToken(String token);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationTokenEntity e WHERE e.email = :email")
        //                   ↑ nom de la classe Entity
    void deleteByEmail(String email);
}
