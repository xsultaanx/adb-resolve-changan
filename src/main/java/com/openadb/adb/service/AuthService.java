package com.openadb.adb.service;

import com.openadb.adb.dto.AuthResponse;
import com.openadb.adb.dto.VehicleDataDto;
import com.openadb.adb.dto.VehicleRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${changan.keys.appid}")
    private String appid;
    @Value("${changan.keys.appid}")
    private String sign;

    private final ObjectMapper objectMapper;


    public AuthResponse getAuthResponse(VehicleRequestDto vehicleRequest) {

        VehicleDataDto vehicleDataDto = objectMapper
                .readValue(vehicleRequest.getData(), VehicleDataDto.class);

        if (checkVin(vehicleDataDto.getVin())) {
            return getSuccessAuthResponse();
        }
        return getVinNotValidAuthResponse();
    }

    private boolean checkVin(String vin) {
        if (vin == null) {
            return false;
        }
        else return vin.length() == 17;
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
