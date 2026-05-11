package com.signalpulse.service;

import com.signalpulse.entity.AppConfig;
import com.signalpulse.repository.AppConfigRepository;
import com.signalpulse.util.EncryptionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private static final String AES_KEY = "AES_KEY";
    private static final String GMAIL_PASSWORD = "GMAIL_PASSWORD";

    private final AppConfigRepository appConfigRepository;

    public List<AppConfig> getAllConfigs() {
        return appConfigRepository.findAll();
    }

    public Optional<String> getConfig(String key) {
        return appConfigRepository.findById(key).map(AppConfig::getConfigValue);
    }

    /**
     * Persist a single config value. Encrypts GMAIL_PASSWORD on the way in.
     * For atomic AES_KEY rotation (re-encrypt password with the new key)
     * use {@link #rotateAesKey}.
     */
    public void setConfig(String key, String value) {
        if (GMAIL_PASSWORD.equals(key) && value != null && !value.isBlank()) {
            String aesKey = getConfig(AES_KEY).orElse("");
            if (!aesKey.isBlank()) {
                value = EncryptionUtils.encrypt(value, aesKey);
            }
        }
        AppConfig config = new AppConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        appConfigRepository.save(config);
    }

    public String getDecryptedConfig(String key) {
        String value = getConfig(key).orElse("");
        if (GMAIL_PASSWORD.equals(key) && !value.isBlank()) {
            String aesKey = getConfig(AES_KEY).orElse("");
            if (!aesKey.isBlank()) {
                try {
                    return EncryptionUtils.decrypt(value, aesKey);
                } catch (Exception e) {
                    log.warn("Failed to decrypt GMAIL_PASSWORD with current AES_KEY — key may have been rotated without re-encrypting the password.");
                    return "";
                }
            }
        }
        return value;
    }

    /**
     * Rotate the AES key. Decrypts the existing GMAIL_PASSWORD with the old key
     * and re-encrypts it with the new one — atomic so we never end up with a
     * password that no key can decrypt.
     *
     * @return true if the key was actually changed
     */
    @Transactional
    public boolean rotateAesKey(String newAesKey) {
        if (newAesKey == null || newAesKey.isBlank()) return false;
        String oldAesKey = getConfig(AES_KEY).orElse("");
        if (newAesKey.equals(oldAesKey)) return false;

        // Re-encrypt any password held under the old key before swapping keys.
        if (!oldAesKey.isBlank()) {
            String encrypted = getConfig(GMAIL_PASSWORD).orElse("");
            if (!encrypted.isBlank()) {
                try {
                    String plain = EncryptionUtils.decrypt(encrypted, oldAesKey);
                    String reEncrypted = EncryptionUtils.encrypt(plain, newAesKey);
                    AppConfig pw = new AppConfig();
                    pw.setConfigKey(GMAIL_PASSWORD);
                    pw.setConfigValue(reEncrypted);
                    appConfigRepository.save(pw);
                } catch (Exception e) {
                    log.error("AES_KEY rotation: failed to re-encrypt GMAIL_PASSWORD with new key. Aborting rotation.", e);
                    throw new IllegalStateException("AES_KEY rotation aborted: existing GMAIL_PASSWORD could not be re-encrypted", e);
                }
            }
        }

        AppConfig key = new AppConfig();
        key.setConfigKey(AES_KEY);
        key.setConfigValue(newAesKey);
        appConfigRepository.save(key);
        return true;
    }
}
