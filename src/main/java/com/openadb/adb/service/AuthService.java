package com.openadb.adb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openadb.adb.dto.AuthResponse;
import com.openadb.adb.dto.VehicleDataDto;
import com.openadb.adb.dto.VehicleRequestDto;
import com.openadb.adb.entity.ActivationLog;
import com.openadb.adb.repository.AccessRecordRepository;
import com.openadb.adb.repository.ActivationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${changan.keys.appid}")
    private String appid;
    @Value("${changan.keys.sign}")
    private String sign;

    private final ObjectMapper objectMapper;
    private final AccessRecordRepository accessRecordRepository;
    private final ActivationLogRepository activationLogRepository;

    public AuthResponse getAuthResponse(VehicleRequestDto vehicleRequest) {
        VehicleDataDto vehicleDataDto;
        try {
            vehicleDataDto = objectMapper.readValue(vehicleRequest.getData(), VehicleDataDto.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse vehicle data: {}", e.getMessage());
            return getVinNotValidAuthResponse();
        }

        String vin = vehicleDataDto.getVin();
        LocalDateTime now = LocalDateTime.now();

        boolean valid = false;
        String reason;
        if (!checkVin(vin)) {
            reason = "invalid vin format";
        } else {
            var record = accessRecordRepository.findByVin(vin).orElse(null);
            if (record == null) {
                reason = "vin not registered";
            } else if (record.getExpiresAt() == null || record.getExpiresAt().isBefore(now)) {
                reason = "vin expired at " + record.getExpiresAt();
            } else {
                valid = true;
                reason = "authorized (expires at " + record.getExpiresAt() + ")";
            }
        }

        activationLogRepository.save(ActivationLog.builder()
                .telegramId(0L)
                .vin(vin)
                .action("AUTH_QUERY")
                .success(valid)
                .message(reason)
                .createdAt(now)
                .build());

        return valid ? getSuccessAuthResponse() : getVinNotValidAuthResponse();
    }

    private boolean checkVin(String vin) {
        return vin != null && vin.length() == 17;
    }

    private AuthResponse getSuccessAuthResponse() {
        return AuthResponse.builder()
                .code("0000")
                .message("success")
                .data(null)
                .build();
    }

    private AuthResponse getVinNotValidAuthResponse() {
        return AuthResponse.builder()
                .code("E120001")
                .message("VIN not registered")
                .data(null)
                .build();
    }

    public static String vinToFactoryCode(String vin) {
        if (vin == null || vin.length() != 17) {
            return null;
        }

        Map<Character, Character> map = new HashMap<>();
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits  = "01234567899876543210012345";
        for (int i = 0; i < letters.length(); i++) {
            map.put(letters.charAt(i), digits.charAt(i));
            map.put(Character.toLowerCase(letters.charAt(i)), digits.charAt(i));
        }

        StringBuilder tail = new StringBuilder();
        for (int i = 9; i < 17; i++) {
            char c = vin.charAt(i);
            if (c >= '0' && c <= '9') {
                tail.append(c);
            } else {
                Character mapped = map.get(c);
                if (mapped == null) return null;
                tail.append(mapped);
            }
        }

        StringBuilder p1 = new StringBuilder();
        StringBuilder p2 = new StringBuilder();
        StringBuilder p3 = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            switch (i % 3) {
                case 0: p1.append(c); break;
                case 1: p2.append(c); break;
                default: p3.append(c);
            }
        }

        return "*#" + p3.toString() + p2.toString() + p1.toString() + "#*";
    }
}
