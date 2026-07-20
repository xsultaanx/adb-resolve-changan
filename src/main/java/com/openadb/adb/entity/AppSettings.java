package com.openadb.adb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettings {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "activation_ttl_hours", nullable = false)
    private int activationTtlHours;

    @Column(name = "instruction_text", columnDefinition = "TEXT")
    private String instructionText;
}
