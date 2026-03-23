package com.signalpulse.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptionUtils {
    private static final String ALGORITHM = "AES";

    public static String encrypt(String strToEncrypt, String secret) {
        if (strToEncrypt == null || secret == null || secret.isBlank()) return strToEncrypt;
        try {
            SecretKeySpec secretKey = getSecretKey(secret);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return strToEncrypt; // Fallback to plain if key is invalid
        }
    }

    public static String decrypt(String strToDecrypt, String secret) {
        if (strToDecrypt == null || secret == null || secret.isBlank()) return strToDecrypt;
        try {
            SecretKeySpec secretKey = getSecretKey(secret);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return strToDecrypt; // Prob already plain
        }
    }

    private static SecretKeySpec getSecretKey(String secret) {
        byte[] key = new byte[32];
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, key, 0, Math.min(secretBytes.length, 32));
        return new SecretKeySpec(key, ALGORITHM);
    }
}
