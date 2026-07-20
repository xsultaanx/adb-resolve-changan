package com.openadb.adb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_records", indexes = {
        @Index(name = "idx_access_vin", columnList = "vin", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vin", nullable = false, unique = true, length = 17)
    private String vin;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "factory_code")
    private String factoryCode;

    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
