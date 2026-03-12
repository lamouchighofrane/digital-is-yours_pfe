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
public class Notification {
    private Long id;
    private Long userId;
    private String type;
    private String titre;
    private String message;
    private Long formationId;
    private String formationTitre;
    private boolean lu;
    private LocalDateTime dateCreation;
}
