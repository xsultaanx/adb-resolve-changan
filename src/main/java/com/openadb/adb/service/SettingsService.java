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
    public static final String DEFAULT_INSTRUCTION = """
            📖 Инструкция

            1. Нажмите кнопку «🔧 Генерация Номера и Активация синего экрана».
            2. Отправьте VIN автомобиля (ровно 17 символов, буквы латинские).
            3. Бот сгенерирует factory-код и активирует «синий экран» для этого VIN.
            4. После активации сервер на запрос authQuery будет отвечать success для этого VIN в течение времени, заданного администратором.
            5. По истечении срока активация истекает — authQuery начнёт возвращать ошибку. Повторная активация того же VIN продлевает срок ещё на тот же период.

            ⚠️ Каждая активация расходует одну попытку из вашего лимита.
            Лимит выдаёт администратор. У администратора попытки не расходуются.
            """;

    private final AppSettingsRepository repository;

    public int getActivationTtlHours() {
        return repository.findById(SETTINGS_ID)
                .map(AppSettings::getActivationTtlHours)
                .orElse(DEFAULT_TTL_HOURS);
    }

    public void setActivationTtlHours(int hours) {
        AppSettings settings = loadOrCreate();
        settings.setActivationTtlHours(hours);
        repository.save(settings);
    }

    public String getInstructionText() {
        return repository.findById(SETTINGS_ID)
                .map(AppSettings::getInstructionText)
                .filter(s -> s != null && !s.isBlank())
                .orElse(DEFAULT_INSTRUCTION);
    }

    public void setInstructionText(String text) {
        AppSettings settings = loadOrCreate();
        settings.setInstructionText(text);
        repository.save(settings);
    }

    private AppSettings loadOrCreate() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> AppSettings.builder()
                .id(SETTINGS_ID)
                .activationTtlHours(DEFAULT_TTL_HOURS)
                .build());
    }
}
