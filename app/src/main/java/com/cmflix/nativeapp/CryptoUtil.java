package com.cmflix.nativeapp;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Runtime string decryption for values that must not sit in plaintext
 * inside the APK (visible via MT Manager / dex string dumps).
 *
 * <p>Secrets are AES-128-CBC encrypted at build time
 * (see {@code app/build.gradle} {@code cmflixEncrypt}) and only exist
 * as ciphertext in the APK. They are decrypted here into memory on use.
 *
 * <p>This raises the bar against casual inspection. It does not stop a
 * determined reverser: anything the app can decrypt, an analyst with the
 * APK can decrypt too. Truly critical secrets must stay server-side.
 */
public final class CryptoUtil {

    private CryptoUtil() {
    }

    /*
     * Obfuscated key parts. They are NOT the key: they are assembled in
     * scrambled order below, then run through SHA-256 to derive the real
     * AES key and IV. No single literal here is usable on its own.
     */
    private static final String PART_0 = "zoTOB7AidP";
    private static final String PART_1 = "qxM31r3CDt";
    private static final String PART_2 = "NEuiMngXfX";
    private static final String PART_3 = "fZUl8eyyDD";

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] derivedBytes(String salt) {
        // Assembly order must match app/build.gradle (cmflixAssembled).
        String assembled = PART_2 + PART_0 + PART_3 + PART_1;
        byte[] hash = sha256(
                (assembled + salt).getBytes(StandardCharsets.UTF_8));
        if (hash == null) {
            return null;
        }
        return Arrays.copyOfRange(hash, 0, 16);
    }

    /**
     * Decrypts a base64 ciphertext produced by the build-time encryptor.
     * Returns "" on any failure so callers fail closed (same as a missing
     * value).
     */
    public static String dec(String base64Ciphertext) {
        try {
            if (base64Ciphertext == null || base64Ciphertext.isEmpty()) {
                return "";
            }
            byte[] keyBytes = derivedBytes("|k1");
            byte[] ivBytes = derivedBytes("|i1");
            if (keyBytes == null || ivBytes == null) {
                return "";
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new IvParameterSpec(ivBytes));
            byte[] plain = cipher.doFinal(
                    Base64.decode(base64Ciphertext, Base64.DEFAULT));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
