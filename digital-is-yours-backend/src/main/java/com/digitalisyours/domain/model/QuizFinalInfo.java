package com.digitalisyours.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizFinalInfo {
    /** true si un quiz final existe pour cette formation */
    private boolean existe;

    /** Note minimale pour réussir (ex: 70.0) — null si pas de quiz */
    private Float   notePassage;

    /** Durée en minutes — null si pas de quiz */
    private Integer dureeMinutes;

    /** Nombre de tentatives autorisées — null si pas de quiz */
    private Integer nombreTentatives;
}
