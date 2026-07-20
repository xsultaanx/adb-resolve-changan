package com.openadb.adb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {
    private String username;
    private String token;
    private String admins = "";

    public Set<Long> getAdminIds() {
        if (admins == null || admins.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new HashSet<>();
        for (String part : admins.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    ids.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids;
    }
}
