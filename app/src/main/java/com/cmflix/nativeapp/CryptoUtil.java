package com.cmflix.nativeapp;

import android.util.Base64;

/**
 * Runtime string decryption for values that must not sit in plaintext
 * inside the APK (visible via MT Manager / dex string dumps).
 *
 * <p>Secrets are AES-128-CBC encrypted at build time
 * (see {@code app/build.gradle} {@code cmflixEncrypt}) and only exist
 * as ciphertext in the APK. They are decrypted here into memory on use.
 *
 * <p>The key material and the AES/SHA-256 implementation live in native
 * code ({@code app/src/main/cpp/secrets.cpp}, built as
 * {@code libcmflix_secrets.so}), not in the dex file: dex string dumps
 * and Java decompilers (jadx) cannot see them. Recovering them requires
 * native reverse engineering (IDA/Ghidra + ARM).
 *
 * <p>The native layer also runs an anti-tamper gate before decrypting:
 * if the APK signature does not match the release key (re-signed/cloned
 * copy), or a debugger/Frida is attached, decryption fails closed and
 * {@code dec()} returns "".
 *
 * <p>This raises the bar against casual inspection. It does not stop a
 * determined reverser: anything the app can decrypt, an analyst with the
 * APK can decrypt too. Truly critical secrets must stay server-side.
 */
public final class CryptoUtil {

    private CryptoUtil() {
    }

    static {
        try {
            System.loadLibrary("cmflix_secrets");
        } catch (UnsatisfiedLinkError e) {
            // Native library missing: dec() fails closed to "" below.
        }
    }

    private static native String nativeDec(byte[] ciphertext);

    /**
     * DIAG BUILD ONLY: returns the anti-tamper gate detail ("ok",
     * "debugger", "frida", "sig:context", "sig:fail", "sig:mismatch").
     * Never called by release code.
     */
    public static native String tamperStatus();

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
            byte[] ct = Base64.decode(base64Ciphertext, Base64.DEFAULT);
            String out = nativeDec(ct);
            return out == null ? "" : out;
        } catch (Throwable t) {
            // Includes UnsatisfiedLinkError when the .so is absent.
            return "";
        }
    }
}
