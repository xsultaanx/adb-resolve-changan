package com.openadb.adb.web;

import com.openadb.adb.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/code")
@RequiredArgsConstructor
public class FactoryCodeApi {

    @GetMapping
    public String vinToFactoryCode(@RequestParam String vin) {
        return AuthService.vinToFactoryCode(vin);
    }
}
