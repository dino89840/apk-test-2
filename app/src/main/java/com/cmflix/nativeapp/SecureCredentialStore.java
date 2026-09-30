package com.cmflix.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureCredentialStore {

    private static final String PREFS =
            "cmflix_secure_credentials";

    private static final String KEY_ALIAS =
            "cmflix_remember_password_key";

    private static final String KEY_CIPHER_TEXT =
            "remembered_password_cipher";

    private static final String KEY_IV =
            "remembered_password_iv";

    private static SharedPreferences preferences;

    private SecureCredentialStore() {
    }

    public static synchronized void initialize(
            Context context
    ) {
        if (preferences != null) {
            return;
        }

        preferences =
                context.getApplicationContext()
                        .getSharedPreferences(
                                PREFS,
                                Context.MODE_PRIVATE
                        );
    }

    private static SharedPreferences prefs() {
        if (preferences == null) {
            throw new IllegalStateException(
                    "SecureCredentialStore.initialize() was not called."
            );
        }

        return preferences;
    }

    public static synchronized void savePassword(
            String password
    ) {
        if (
                password == null ||
                password.isEmpty()
        ) {
            clearPassword();
            return;
        }

        try {
            String[] encrypted =
                    encrypt(password);

            prefs()
                    .edit()
                    .putString(
                            KEY_CIPHER_TEXT,
                            encrypted[0]
                    )
                    .putString(
                            KEY_IV,
                            encrypted[1]
                    )
                    .apply();
        } catch (Exception error) {
            clearPassword();
        }
    }

    public static synchronized String getPassword() {
        String plain =
                decrypt(
                        KEY_CIPHER_TEXT,
                        KEY_IV
                );

        if (plain == null) {
            /*
             * Keystore key ပျက်ခြင်း၊ app data restore
             * ပြဿနာရှိခြင်းစသည့်အခြေအနေမှာ
             * invalid encrypted data ကိုရှင်းမည်။
             */
            clearPassword();
            return "";
        }

        return plain;
    }

    public static synchronized void clearPassword() {
        prefs()
                .edit()
                .remove(KEY_CIPHER_TEXT)
                .remove(KEY_IV)
                .apply();
    }

    /*
     * Plain text ကို encrypt လုပ်ပြီး
     * {cipherText, iv} Base64 string နှစ်ခု ပြန်ပေးသည်။
     */
    private static String[] encrypt(String plain)
            throws Exception {
        SecretKey secretKey =
                getOrCreateSecretKey();

        Cipher cipher =
                Cipher.getInstance(
                        "AES/GCM/NoPadding"
                );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey
        );

        byte[] encrypted =
                cipher.doFinal(
                        plain.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        return new String[]{
                Base64.encodeToString(
                        encrypted,
                        Base64.NO_WRAP
                ),
                Base64.encodeToString(
                        cipher.getIV(),
                        Base64.NO_WRAP
                )
        };
    }

    /*
     * Decrypt အောင်မြင်လျှင် plain text၊
     * မအောင်မြင်လျှင် null ပြန်ပေးသည်။
     */
    private static String decrypt(
            String cipherKey,
            String ivKey
    ) {
        String cipherText =
                prefs().getString(
                        cipherKey,
                        ""
                );

        String iv =
                prefs().getString(
                        ivKey,
                        ""
                );

        if (
                cipherText == null ||
                cipherText.isEmpty() ||
                iv == null ||
                iv.isEmpty()
        ) {
            return null;
        }

        try {
            SecretKey secretKey =
                    getOrCreateSecretKey();

            Cipher cipher =
                    Cipher.getInstance(
                            "AES/GCM/NoPadding"
                    );

            GCMParameterSpec spec =
                    new GCMParameterSpec(
                            128,
                            Base64.decode(
                                    iv,
                                    Base64.NO_WRAP
                            )
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    spec
            );

            byte[] decrypted =
                    cipher.doFinal(
                            Base64.decode(
                                    cipherText,
                                    Base64.NO_WRAP
                            )
                    );

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );
        } catch (Exception error) {
            return null;
        }
    }

    private static SecretKey getOrCreateSecretKey()
            throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(
                        "AndroidKeyStore"
                );

        keyStore.load(null);

        KeyStore.Entry existingEntry =
                keyStore.getEntry(
                        KEY_ALIAS,
                        null
                );

        if (
                existingEntry instanceof
                        KeyStore.SecretKeyEntry
        ) {
            return (
                    (KeyStore.SecretKeyEntry)
                            existingEntry
            ).getSecretKey();
        }

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore"
                );

        KeyGenParameterSpec specification =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties
                                        .ENCRYPTION_PADDING_NONE
                        )
                        .setRandomizedEncryptionRequired(
                                true
                        )
                        .build();

        keyGenerator.init(specification);

        return keyGenerator.generateKey();
    }
}
