package com.signalpulse.controller;

import com.signalpulse.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConfigController {
    private final ConfigService configService;
    
    @GetMapping
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public Map<String, String> getAllConfigs() {

        // Simple map return for UI
        return configService.getAllConfigs().stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.getConfigKey(), c -> c.getConfigValue()));
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('OP_MANAGE_CONFIG')")
    public void setConfig(@RequestBody Map<String, String> configs) {

        // Save AES_KEY first if it's being updated/provided
        if (configs.containsKey("AES_KEY")) {
            configService.setConfig("AES_KEY", configs.get("AES_KEY"));
        }
        configs.forEach((k, v) -> {
            if (!"AES_KEY".equals(k)) {
                configService.setConfig(k, v);
            }
        });
    }
}
