package com.openadb.adb.web;

import com.openadb.adb.dto.AuthResponse;
import com.openadb.adb.dto.VehicleRequestDto;
import com.openadb.adb.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tlc/auth/v1")
@RequiredArgsConstructor
public class TlcApi {
    public final AuthService authService;

    @PostMapping("/authQuery")
    public AuthResponse auth(@RequestBody VehicleRequestDto vehicleRequestDto) {
        return authService.getAuthResponse(vehicleRequestDto);
    }
}
