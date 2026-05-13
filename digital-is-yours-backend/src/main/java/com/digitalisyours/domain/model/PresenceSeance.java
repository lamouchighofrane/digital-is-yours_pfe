package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceSeance {
    private Long id;
    private Long seanceId;
    private String apprenantEmail;
    private String apprenantNom;
    private String apprenantPrenom;
    private LocalDateTime dateRejoindre;
    private LocalDateTime dateQuitter;
    private boolean present;
    private Integer dureeMinutes;
}