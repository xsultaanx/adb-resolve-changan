package com.openadb.adb.web;

import com.openadb.adb.entity.BotUser;
import com.openadb.adb.repository.AccessRecordRepository;
import com.openadb.adb.repository.ActivationLogRepository;
import com.openadb.adb.repository.BotUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminApi {

    private final BotUserRepository userRepository;
    private final AccessRecordRepository accessRepository;
    private final ActivationLogRepository logRepository;

    @GetMapping("/users")
    public List<BotUser> users() {
        return userRepository.findAll();
    }

    @PostMapping("/users/{telegramId}/access")
    public Map<String, Object> setAccess(@PathVariable Long telegramId,
                                         @RequestParam boolean granted) {
        return userRepository.findById(telegramId)
                .map(u -> {
                    u.setHasAccess(granted);
                    userRepository.save(u);
                    return Map.<String, Object>of("telegramId", telegramId, "hasAccess", granted);
                })
                .orElse(Map.of("error", "user not found", "telegramId", telegramId));
    }

    @GetMapping("/access")
    public Object accessList() {
        return accessRepository.findAll();
    }

    @GetMapping("/logs")
    public Object logs() {
        return logRepository.findAll();
    }
}
