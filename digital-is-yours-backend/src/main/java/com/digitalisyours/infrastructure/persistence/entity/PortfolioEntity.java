package com.digitalisyours.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolios",
        indexes = {
                @Index(name = "idx_portfolio_apprenant", columnList = "apprenant_id"),
                @Index(name = "idx_portfolio_slug", columnList = "slug", unique = true)
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PortfolioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apprenant_id", nullable = false, unique = true)
    private Long apprenantId;

    @Column(name = "apprenant_email", nullable = false)
    private String apprenantEmail;

    @Column(name = "slug", unique = true, nullable = false, length = 200)
    private String slug;

    @Column(name = "url_github_pages", length = 500)
    private String urlGithubPages;

    @Column(name = "github_file_sha", length = 100)
    private String githubFileSha;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "derniere_mise_a_jour")
    private LocalDateTime derniereMiseAJour;

    @Column(name = "nombre_certificats")
    private Integer nombreCertificats;

    @Column(name = "est_publie", nullable = false)
    @Builder.Default
    private Boolean estPublie = false;
}