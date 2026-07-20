package com.openadb.adb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bot_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotUser {

    @Id
    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "has_access", nullable = false)
    private boolean hasAccess;

    @Column(name = "activations_remaining", nullable = false)
    private int activationsRemaining;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
