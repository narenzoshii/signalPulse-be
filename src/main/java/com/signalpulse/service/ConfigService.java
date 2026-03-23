package com.signalpulse.service;

import com.signalpulse.entity.AppConfig;
import com.signalpulse.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfigService {
    private final AppConfigRepository appConfigRepository;
    
    public List<AppConfig> getAllConfigs() {
        return appConfigRepository.findAll();
    }

    public java.util.Optional<String> getConfig(String key) {
        return appConfigRepository.findById(key).map(AppConfig::getConfigValue);
    }
    
    public void setConfig(String key, String value) {
        if ("GMAIL_PASSWORD".equals(key) && value != null && !value.isBlank()) {
            String aesKey = getConfig("AES_KEY").orElse("");
            if (!aesKey.isBlank()) {
                value = com.signalpulse.util.EncryptionUtils.encrypt(value, aesKey);
            }
        }
        AppConfig config = new AppConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        appConfigRepository.save(config);
    }

    public String getDecryptedConfig(String key) {
        String value = getConfig(key).orElse("");
        if ("GMAIL_PASSWORD".equals(key) && !value.isBlank()) {
            String aesKey = getConfig("AES_KEY").orElse("");
            if (!aesKey.isBlank()) {
                return com.signalpulse.util.EncryptionUtils.decrypt(value, aesKey);
            }
        }
        return value;
    }
}
