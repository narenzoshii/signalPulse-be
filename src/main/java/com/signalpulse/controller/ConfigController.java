package com.signalpulse.controller;

import com.signalpulse.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {
    private static final String MASK = "********";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "AES_KEY", "GMAIL_PASSWORD", "JWT_SECRET"
    );

    private final ConfigService configService;

    @GetMapping
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public Map<String, String> getAllConfigs() {
        return configService.getAllConfigs().stream()
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.getConfigKey(),
                        c -> isSensitive(c.getConfigKey()) && c.getConfigValue() != null && !c.getConfigValue().isEmpty()
                                ? MASK
                                : c.getConfigValue()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OP_MANAGE_CONFIG')")
    public void setConfig(@RequestBody Map<String, String> configs) {
        // 1. Rotate AES_KEY first via the atomic path so any existing
        //    GMAIL_PASSWORD gets re-encrypted with the new key in the same tx.
        String newAesKey = configs.get("AES_KEY");
        if (newAesKey != null && !MASK.equals(newAesKey) && !newAesKey.isBlank()) {
            configService.rotateAesKey(newAesKey);
        }

        // 2. Then everything else. Sensitive values left at the mask placeholder
        //    are skipped so the UI can resubmit the whole form without erasing secrets.
        configs.forEach((k, v) -> {
            if ("AES_KEY".equals(k)) return;
            if (isSensitive(k) && (MASK.equals(v) || v == null || v.isBlank())) return;
            configService.setConfig(k, v);
        });
    }

    private boolean isSensitive(String key) {
        if (key == null) return false;
        if (SENSITIVE_KEYS.contains(key)) return true;
        String upper = key.toUpperCase();
        return upper.endsWith("_PASSWORD") || upper.endsWith("_SECRET") || upper.endsWith("_KEY");
    }
}
