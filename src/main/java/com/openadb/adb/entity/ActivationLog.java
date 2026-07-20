package com.openadb.adb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "activation_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "username")
    private String username;

    @Column(name = "vin", length = 17)
    private String vin;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "message", length = 512)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
