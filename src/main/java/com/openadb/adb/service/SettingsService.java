package com.openadb.adb.service;

import com.openadb.adb.entity.AppSettings;
import com.openadb.adb.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Long SETTINGS_ID = 1L;
    private static final int DEFAULT_TTL_HOURS = 24;

    private final AppSettingsRepository repository;

    public int getActivationTtlHours() {
        return repository.findById(SETTINGS_ID)
                .map(AppSettings::getActivationTtlHours)
                .orElse(DEFAULT_TTL_HOURS);
    }

    public void setActivationTtlHours(int hours) {
        AppSettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(() -> AppSettings.builder().id(SETTINGS_ID).build());
        settings.setActivationTtlHours(hours);
        repository.save(settings);
    }
}
