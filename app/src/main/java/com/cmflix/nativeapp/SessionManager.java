package com.cmflix.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class SessionManager {

    private static final String PREFS =
            "cmflix_session";

    private static final String DEVICE_PREFS =
            "cmflix_device";

    private static final String KEY_COOKIE =
            "cookie";

    private static final String KEY_CSRF =
            "csrf";

    private static final String KEY_USERNAME =
            "username";

    private static final String KEY_EMAIL =
            "email";

    private static final String KEY_DEVICE_ID =
            "device_id";

    private static SharedPreferences preferences;
    private static SharedPreferences devicePreferences;

    private SessionManager() {
    }

    public static void initialize(Context context) {
        if (preferences == null) {
            preferences = context
                    .getApplicationContext()
                    .getSharedPreferences(
                            PREFS,
                            Context.MODE_PRIVATE
                    );
        }

        if (devicePreferences == null) {
            devicePreferences = context
                    .getApplicationContext()
                    .getSharedPreferences(
                            DEVICE_PREFS,
                            Context.MODE_PRIVATE
                    );
        }

        ensureDeviceId();
    }

    private static SharedPreferences prefs() {
        if (preferences == null) {
            throw new IllegalStateException(
                    "SessionManager.initialize() must be called first."
            );
        }

        return preferences;
    }

    private static SharedPreferences devicePrefs() {
        if (devicePreferences == null) {
            throw new IllegalStateException(
                    "SessionManager.initialize() must be called first."
            );
        }

        return devicePreferences;
    }

    private static void ensureDeviceId() {
        String current =
                devicePrefs().getString(
                        KEY_DEVICE_ID,
                        ""
                );

        if (current == null || current.isEmpty()) {
            devicePrefs()
                    .edit()
                    .putString(
                            KEY_DEVICE_ID,
                            UUID.randomUUID().toString()
                    )
                    .apply();
        }
    }

    public static String getDeviceId() {
        ensureDeviceId();

        return devicePrefs().getString(
                KEY_DEVICE_ID,
                ""
        );
    }

    public static boolean isLoggedIn() {
        return !getCookie().isEmpty();
    }

    public static String getCookie() {
        return prefs().getString(
                KEY_COOKIE,
                ""
        );
    }

    public static String getCsrf() {
        return prefs().getString(
                KEY_CSRF,
                ""
        );
    }

    public static String getUsername() {
        return prefs().getString(
                KEY_USERNAME,
                ""
        );
    }

    public static String getEmail() {
        return prefs().getString(
                KEY_EMAIL,
                ""
        );
    }

    public static void saveCookie(String cookie) {
        prefs()
                .edit()
                .putString(
                        KEY_COOKIE,
                        cookie == null ? "" : cookie
                )
                .apply();
    }

    public static void saveAuth(
            String csrf,
            String username,
            String email
    ) {
        prefs()
                .edit()
                .putString(
                        KEY_CSRF,
                        csrf == null ? "" : csrf
                )
                .putString(
                        KEY_USERNAME,
                        username == null ? "" : username
                )
                .putString(
                        KEY_EMAIL,
                        email == null ? "" : email
                )
                .apply();
    }

    /*
     * Logout လုပ်လျှင် session ကိုသာရှင်းမယ်။
     * Device ID ကိုမဖျက်ရ။
     */
    public static void clear() {
        prefs().edit().clear().apply();
    }
}
