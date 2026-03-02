package com.digitalisyours.infrastructure.persistence.entity;


import com.digitalisyours.domain.model.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)  // ✅ AJOUTÉ pour l'héritage
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private String nom;

    @Column(unique = true, nullable = false)
    private String email;

    private String telephone;

    @Column(nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean emailVerifie = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime dateInscription;

    private LocalDateTime derniereConnexion;

    @Column(columnDefinition = "LONGTEXT")
    private String photo;

    /**
     * Correspond à +token : String (nullable) du diagramme.
     */
    private String token;

    @PrePersist
    public void prePersist() {
        if (this.dateInscription == null) {
            this.dateInscription = LocalDateTime.now();
        }
    }

    // ══ UserDetails — Spring Security ════════════════════════════

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return motDePasse;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // ⚠️ Compte bloqué si désactivé par l'admin
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // ⚠️ CRITIQUE : Spring Security refuse la connexion si false
        return active && emailVerifie;
    }
}
