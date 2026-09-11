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

    private static final String KEY_VIP_UNTIL =
            "vip_until";

    private static final String KEY_PROFILE_SYNCED_AT =
            "profile_synced_at";

    private static final String KEY_DEVICE_ID =
            "device_id";

    private static final long ONE_DAY_MS =
            24L * 60L * 60L * 1000L;

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

    public static long getVipUntil() {
        return prefs().getLong(
                KEY_VIP_UNTIL,
                0L
        );
    }

    public static long getPremiumDaysRemaining() {
        long remaining =
                getVipUntil() - System.currentTimeMillis();

        if (remaining <= 0L) {
            return 0L;
        }

        /*
         * 2 ရက် 1 နာရီကျန်လျှင် P-3Day ပြရန်
         * အပေါ်ဘက်သို့ round တက်ထားသည်။
         */
        return (remaining + ONE_DAY_MS - 1L)
                / ONE_DAY_MS;
    }

    public static String getPremiumLabel() {
        return "P-" +
                getPremiumDaysRemaining() +
                "Day";
    }

    public static boolean isProfileRefreshDue(
            long maxAgeMillis
    ) {
        long lastSync =
                prefs().getLong(
                        KEY_PROFILE_SYNCED_AT,
                        0L
                );

        return lastSync <= 0L ||
                System.currentTimeMillis() - lastSync
                        >= maxAgeMillis;
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
            String email,
            long vipUntil
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
                .putLong(
                        KEY_VIP_UNTIL,
                        Math.max(0L, vipUntil)
                )
                .putLong(
                        KEY_PROFILE_SYNCED_AT,
                        System.currentTimeMillis()
                )
                .apply();
    }

    public static void saveVipState(long vipUntil) {
        prefs()
                .edit()
                .putLong(
                        KEY_VIP_UNTIL,
                        Math.max(0L, vipUntil)
                )
                .putLong(
                        KEY_PROFILE_SYNCED_AT,
                        System.currentTimeMillis()
                )
                .apply();
    }

    /*
     * Account session ပဲရှင်းမယ်။
     * Device ID ကို မရှင်းပါ။
     */
    public static void clear() {
        prefs().edit().clear().apply();
    }
}
