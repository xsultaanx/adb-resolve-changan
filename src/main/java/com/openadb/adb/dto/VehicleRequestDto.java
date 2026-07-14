package com.openadb.adb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleRequestDto {
    private String appid;
    private String signVersion;
    private String sdkVersion;
    private String nonce;
    private Long timestamp;
    private String data;
    private String sign;
}
