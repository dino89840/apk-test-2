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

    private static final String KEY_VIP_PLAN_MONTHS =
            "vip_plan_months";

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

    public static int getVipPlanMonths() {
        return prefs().getInt(
                KEY_VIP_PLAN_MONTHS,
                0
        );
    }

    public static boolean isVipActive() {
        return getVipUntil() >
                System.currentTimeMillis();
    }

    public static long getPremiumDaysRemaining() {
        long remaining =
                getVipUntil() -
                        System.currentTimeMillis();

        if (remaining <= 0L) {
            return 0L;
        }

        return (remaining + ONE_DAY_MS - 1L)
                / ONE_DAY_MS;
    }

    /*
     * Main screen အပေါ်ညာဘက် button အတွက်။
     * VIP မရှိသေးလျှင် P-0Day မပြဘဲ Buy VIP ပြမည်။
     */
    public static String getPremiumLabel() {
        if (!isVipActive()) {
            return "♛ Buy VIP";
        }

        long days =
                getPremiumDaysRemaining();

        return "P-" + days + "Day";
    }

    /*
     * Profile Current Plan အတွက်။
     */
    public static String getPlanLabel() {
        if (!isVipActive()) {
            return "Free Plan";
        }

        int months =
                getVipPlanMonths();

        if (months <= 0) {
            return "Premium Plan";
        }

        if (months == 1) {
            return "1 Month";
        }

        return months + " Months";
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
            long vipUntil,
            int vipPlanMonths
    ) {
        boolean active =
                vipUntil >
                        System.currentTimeMillis();

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
                .putInt(
                        KEY_VIP_PLAN_MONTHS,
                        active
                                ? Math.max(0, vipPlanMonths)
                                : 0
                )
                .putLong(
                        KEY_PROFILE_SYNCED_AT,
                        System.currentTimeMillis()
                )
                .apply();
    }

    /*
     * အဟောင်း call ကျန်နေလျှင် build မပျက်အောင်ထားသည်။
     */
    public static void saveAuth(
            String csrf,
            String username,
            String email,
            long vipUntil
    ) {
        saveAuth(
                csrf,
                username,
                email,
                vipUntil,
                getVipPlanMonths()
        );
    }

    public static void saveVipState(
            long vipUntil,
            int vipPlanMonths
    ) {
        boolean active =
                vipUntil >
                        System.currentTimeMillis();

        prefs()
                .edit()
                .putLong(
                        KEY_VIP_UNTIL,
                        Math.max(0L, vipUntil)
                )
                .putInt(
                        KEY_VIP_PLAN_MONTHS,
                        active
                                ? Math.max(0, vipPlanMonths)
                                : 0
                )
                .putLong(
                        KEY_PROFILE_SYNCED_AT,
                        System.currentTimeMillis()
                )
                .apply();
    }

    public static void saveVipState(long vipUntil) {
        saveVipState(
                vipUntil,
                getVipPlanMonths()
        );
    }

    /*
     * Account data ပဲရှင်းမည်။
     * Device ID ကို မရှင်းပါ။
     */
    public static void clear() {
        prefs()
                .edit()
                .clear()
                .apply();
    }
}
