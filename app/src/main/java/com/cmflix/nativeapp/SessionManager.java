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

/*
 * Continue Watching, Recent, Download History စတာတွေကို
 * account တစ်ခုချင်းစီအလိုက် ခွဲသိမ်းရန် server user ID
 * ကို local session ထဲ သိမ်းထားမည်။
 */
private static final String KEY_USER_ID =
        "user_id";

private static final String KEY_USERNAME =
        "username";


    private static final String KEY_EMAIL =
            "email";

    private static final String KEY_VIP_UNTIL =
            "vip_until";

    private static final String KEY_VIP_PLAN_MONTHS =
            "vip_plan_months";

    private static final String KEY_VIP_PLAN_TYPE =
            "vip_plan_type";

    private static final String KEY_PROFILE_SYNCED_AT =
            "profile_synced_at";

    private static final String KEY_DEVICE_ID =
            "device_id";

    private static final String KEY_REMEMBER_LOGIN =
            "remember_login";

    private static final String KEY_REMEMBERED_IDENTITY =
            "remembered_identity";

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

    SecureCredentialStore.initialize(context);
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

        if (current == null || current.trim().isEmpty()) {
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

        String value =
                devicePrefs().getString(
                        KEY_DEVICE_ID,
                        ""
                );

        return value == null ? "" : value;
    }
public static boolean isRememberLoginEnabled() {
    return prefs().getBoolean(
            KEY_REMEMBER_LOGIN,
            false
    );
}

public static String getRememberedIdentity() {
    String value =
            prefs().getString(
                    KEY_REMEMBERED_IDENTITY,
                    ""
            );

    return value == null ? "" : value;
}

public static String getRememberedPassword() {
    if (!isRememberLoginEnabled()) {
        return "";
    }

    return SecureCredentialStore.getPassword();
}

public static void saveRememberedLogin(
        boolean remember,
        String identity,
        String password
) {
    SharedPreferences.Editor editor =
            prefs().edit();

    editor.putBoolean(
            KEY_REMEMBER_LOGIN,
            remember
    );

    if (remember) {
        editor.putString(
                KEY_REMEMBERED_IDENTITY,
                identity == null
                        ? ""
                        : identity.trim()
        );

        SecureCredentialStore.savePassword(
                password == null
                        ? ""
                        : password
        );
    } else {
        editor.remove(
                KEY_REMEMBERED_IDENTITY
        );

        SecureCredentialStore.clearPassword();
    }

    editor.apply();
}

/*
 * Profile မှာ password ပြောင်းပြီးသောအခါ
 * Remember me ဖွင့်ထားလျှင် saved password ကိုပါ
 * password အသစ်နဲ့ update လုပ်မည်။
 */
public static void updateRememberedPassword(
        String newPassword
) {
    if (!isRememberLoginEnabled()) {
        return;
    }

    SecureCredentialStore.savePassword(
            newPassword == null
                    ? ""
                    : newPassword
    );
}


    public static boolean isLoggedIn() {
        return !getCookie().isEmpty();
    }

    public static String getCookie() {
        String value =
                prefs().getString(
                        KEY_COOKIE,
                        ""
                );

        return value == null ? "" : value;
    }

    public static String getCsrf() {
        String value =
                prefs().getString(
                        KEY_CSRF,
                        ""
                );

        return value == null ? "" : value;
    }
public static String getUserId() {
    String value =
            prefs().getString(
                    KEY_USER_ID,
                    ""
            );

    return value == null
            ? ""
            : value.trim();
}

    public static String getUsername() {
        String value =
                prefs().getString(
                        KEY_USERNAME,
                        ""
                );

        return value == null ? "" : value;
    }

    public static String getEmail() {
        String value =
                prefs().getString(
                        KEY_EMAIL,
                        ""
                );

        return value == null ? "" : value;
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

    public static String getVipPlanType() {
        String value =
                prefs().getString(
                        KEY_VIP_PLAN_TYPE,
                        ""
                );

        if ("trial".equals(value)) {
            return "trial";
        }

        if ("premium".equals(value)) {
            return "premium";
        }

        /*
         * App version အဟောင်းမှာ plan type မသိမ်းထားဘဲ
         * vipUntil ပဲသိမ်းထားခဲ့နိုင်တာကြောင့်
         * active VIP ဖြစ်နေရင် premium အဖြစ် fallback လုပ်မယ်။
         */
        if (isVipActive()) {
            return "premium";
        }

        return "free";
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

    public static String getPremiumLabel() {
        if (!isVipActive()) {
            return "Buy VIP";
        }

        long days =
                getPremiumDaysRemaining();

        return "P-" + days + "Day";
    }

    public static String getPlanLabel() {
        if (!isVipActive()) {
            return "Free Plan";
        }

        if ("trial".equals(getVipPlanType())) {
            return "Trial";
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

    /*
 * Server user ID ပါသော main saveAuth method။
 *
 * User ID ကို local history namespace အတွက် အသုံးပြုမည်။
 */
public static void saveAuth(
        String userId,
        String csrf,
        String username,
        String email,
        long vipUntil,
        int vipPlanMonths,
        String vipPlanType
) {
    boolean active =
            vipUntil >
                    System.currentTimeMillis();

    String normalizedPlanType =
            normalizePlanType(
                    vipPlanType,
                    active
            );

    prefs()
            .edit()
            .putString(
                    KEY_USER_ID,
                    userId == null
                            ? ""
                            : userId.trim()
            )
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
            .putString(
                    KEY_VIP_PLAN_TYPE,
                    normalizedPlanType
            )
            .putLong(
                    KEY_PROFILE_SYNCED_AT,
                    System.currentTimeMillis()
            )
            .apply();
}

/*
 * အရင် code တွေ compile မပျက်စေရန် backward-compatible
 * overload ကိုထားမည်။
 */
public static void saveAuth(
        String csrf,
        String username,
        String email,
        long vipUntil,
        int vipPlanMonths,
        String vipPlanType
) {
    saveAuth(
            getUserId(),
            csrf,
            username,
            email,
            vipUntil,
            vipPlanMonths,
            vipPlanType
    );
}


    /*
     * အဟောင်း 5-parameter calls တွေ build မပျက်ရန်။
     */
    public static void saveAuth(
            String csrf,
            String username,
            String email,
            long vipUntil,
            int vipPlanMonths
    ) {
        saveAuth(
                csrf,
                username,
                email,
                vipUntil,
                vipPlanMonths,
                defaultPlanType(vipUntil)
        );
    }

    /*
     * အဟောင်း 4-parameter calls တွေ build မပျက်ရန်။
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
                getVipPlanMonths(),
                defaultPlanType(vipUntil)
        );
    }

    public static void saveVipState(
            long vipUntil,
            int vipPlanMonths,
            String vipPlanType
    ) {
        boolean active =
                vipUntil >
                        System.currentTimeMillis();

        String normalizedPlanType =
                normalizePlanType(
                        vipPlanType,
                        active
                );

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
                .putString(
                        KEY_VIP_PLAN_TYPE,
                        normalizedPlanType
                )
                .putLong(
                        KEY_PROFILE_SYNCED_AT,
                        System.currentTimeMillis()
                )
                .apply();
    }

    /*
     * အဟောင်း 2-parameter calls တွေ build မပျက်ရန်။
     */
    public static void saveVipState(
            long vipUntil,
            int vipPlanMonths
    ) {
        saveVipState(
                vipUntil,
                vipPlanMonths,
                defaultPlanType(vipUntil)
        );
    }

    /*
     * DetailActivity ရဲ့ saveVipState(0L)
     * call အတွက် overload။
     */
    public static void saveVipState(
            long vipUntil
    ) {
        saveVipState(
                vipUntil,
                vipUntil > System.currentTimeMillis()
                        ? getVipPlanMonths()
                        : 0,
                defaultPlanType(vipUntil)
        );
    }

    private static String defaultPlanType(
            long vipUntil
    ) {
        if (vipUntil <= System.currentTimeMillis()) {
            return "free";
        }

        String current =
                prefs().getString(
                        KEY_VIP_PLAN_TYPE,
                        ""
                );

        if ("trial".equals(current)) {
            return "trial";
        }

        return "premium";
    }

    private static String normalizePlanType(
            String planType,
            boolean active
    ) {
        if (!active) {
            return "free";
        }

        if ("trial".equals(planType)) {
            return "trial";
        }

        return "premium";
    }

    /*
     * Account session ကိုရှင်းမယ်။
     * Device ID ကိုတော့ DEVICE_PREFS ထဲမှာထားလို့
     * logout လုပ်ချိန် မပျက်ပါ။
     */
    public static void clear() {
    boolean remember =
            isRememberLoginEnabled();

    String rememberedIdentity =
            getRememberedIdentity();

    SharedPreferences.Editor editor =
            prefs().edit();

    editor.clear();

    /*
     * Logout သို့မဟုတ် session expire ဖြစ်ပေမယ့်
     * Remember me ရွေးထားလျှင် username/email ကို
     * login screen မှာ ဆက်ပြပေးပါမယ်။
     *
     * Password ကို local storage ထဲ မသိမ်းပါ။
     */
    if (remember) {
        editor.putBoolean(
                KEY_REMEMBER_LOGIN,
                true
        );

        editor.putString(
                KEY_REMEMBERED_IDENTITY,
                rememberedIdentity
        );
    }

    editor.apply();
}

}
