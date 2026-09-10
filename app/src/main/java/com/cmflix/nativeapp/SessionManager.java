package com.cmflix.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionManager {

    private static final String PREFS = "cmflix_session";
    private static final String KEY_COOKIE = "cookie";
    private static final String KEY_CSRF = "csrf";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";

    private static SharedPreferences preferences;

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
    }

    private static SharedPreferences prefs() {
        if (preferences == null) {
            throw new IllegalStateException(
                    "SessionManager.initialize() must be called first."
            );
        }

        return preferences;
    }

    public static boolean isLoggedIn() {
        return !getCookie().isEmpty();
    }

    public static String getCookie() {
        return prefs().getString(KEY_COOKIE, "");
    }

    public static String getCsrf() {
        return prefs().getString(KEY_CSRF, "");
    }

    public static String getUsername() {
        return prefs().getString(KEY_USERNAME, "");
    }

    public static String getEmail() {
        return prefs().getString(KEY_EMAIL, "");
    }

    public static void saveCookie(String cookie) {
        prefs()
                .edit()
                .putString(KEY_COOKIE, cookie == null ? "" : cookie)
                .apply();
    }

    public static void saveAuth(
            String csrf,
            String username,
            String email
    ) {
        prefs()
                .edit()
                .putString(KEY_CSRF, csrf == null ? "" : csrf)
                .putString(KEY_USERNAME, username == null ? "" : username)
                .putString(KEY_EMAIL, email == null ? "" : email)
                .apply();
    }

    public static void clear() {
        prefs().edit().clear().apply();
    }
}
