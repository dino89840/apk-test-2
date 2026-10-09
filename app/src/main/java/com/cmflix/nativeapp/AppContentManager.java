package com.cmflix.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppContentManager {

    /*
     * Banner သည် rarely-changing content ဖြစ်သောကြောင့်
     * device မှာ 12 hours cache ထားမည်။
     *
     * Refresh လုပ်ရသောအခါ /app-content ကိုခေါ်ပြီး
     * Cloudflare edge cache မှရယူမည်။
     */
    private static final long BANNER_CACHE_TTL_MS =
        12L * 60L * 60L * 1000L;

/*
 * Notification ကို Activity resume တိုင်း network
 * validation မလုပ်ဘဲ device ထဲမှာ 30 minutes သုံးမည်။
 *
 * 30 minutes ကျော်မှ ETag ဖြင့် server ကို
 * ပြန်စစ်မည်။
 */
private static final long NOTICE_CACHE_TTL_MS =
        30L * 60L * 1000L;


    private static final String PREFS =
            "cmflix_app_content_v3";

    private static final String KEY_BANNER_BODY =
            "banner_body";

    private static final String KEY_BANNER_SAVED_AT =
            "banner_saved_at";

    private static final String KEY_NOTICE_BODY =
            "notice_body";

    private static final String KEY_NOTICE_ETAG =
        "notice_etag";

private static final String KEY_NOTICE_SAVED_AT =
        "notice_saved_at";


    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final AtomicBoolean
            NOTICE_REQUEST_IN_FLIGHT =
            new AtomicBoolean(false);

    private AppContentManager() {
    }

    /*
     * မြန်မာ (samusar) category kill-switch။
     *
     * Backend /app-content မှ "myanmar": {"enabled": ...}
     * ကို ဖတ်သည်။ Field မရှိသေးလျှင် (backend cache
     * မ� refresh ရသေးလျှင်) default TRUE — tab ပြမည်။
     * Explicitly false ဖြစ်မှသာ ပိတ်မည်။
     *
     * Cached JSON body ထဲမှာ full response ပါပြီးသား
     * ဖြစ်သောကြောင့် network request အသစ် လုံးဝ မလိုပါ။
     */
    public static boolean isMyanmarEnabled(
            JSONObject content
    ) {
        if (content == null) {
            return true;
        }

        JSONObject myanmar =
                content.optJSONObject("myanmar");

        if (myanmar == null) {
            return true;
        }

        return myanmar.optBoolean("enabled", true);
    }

    /*
     * Javtiful category kill-switch.
     *
     * Backend /app-content မှ "javtiful": {"enabled": ...}
     * ကို ဖတ်သည်။ Field မရိသေးလျှင် (backend cache
     * မ refresh ရသေးလျှင်) default TRUE — card ပမည်။
     * Explicitly false ဖစ်မှသာ ပိတ်မည်။
     */
    public static boolean isJavtifulEnabled(
            JSONObject content
    ) {
        if (content == null) {
            return true;
        }

        JSONObject javtiful =
                content.optJSONObject("javtiful");

        if (javtiful == null) {
            return true;
        }

        return javtiful.optBoolean("enabled", true);
    }

    /*
     * In-app APK update — server-driven version info။
     *
     * Backend /app-content မှ "apk": {"versionCode": N,
     * "url": "<https apk url>"} ကို ဖတ်သည်။
     *
     * versionCode=0 (သို့မဟုတ်) url လွတ်နေလျှင် →
     * update မရှိ။ MainActivity မှ BuildConfig.VERSION_CODE
     * နှင့် နှိုင်းယှဉ်ပြီး dialog ပြမည်။
     *
     * cached /app-content JSON ထဲက ဖတ်သောကြောင့်
     * network request အသစ် လုံးဝ မလိုပါ။
     */
    public static int getApkVersionCode(
            JSONObject content
    ) {
        if (content == null) {
            return 0;
        }

        JSONObject apk =
                content.optJSONObject("apk");

        if (apk == null) {
            return 0;
        }

        return apk.optInt("versionCode", 0);
    }

    public static String getApkUrl(
            JSONObject content
    ) {
        if (content == null) {
            return "";
        }

        JSONObject apk =
                content.optJSONObject("apk");

        if (apk == null) {
            return "";
        }

        return apk.optString("url", "").trim();
    }

    /*
     * Samusar proxy URL — server-configurable။
     *
     * Backend /app-content မှ "samusar": {"proxy": "<url>"}
     * ကို ဖတ်သည်။ Field မရှိလျှင် / လွတ်နေလျှင် ""
     * ပြန်ပေးမည် — SamusarClient က hardcoded
     * default (PROXY_BASE_URL) သို့ fallback လုပ်မည်။
     *
     * myanmar.enabled လိုပဲ cached /app-content
     * JSON ထဲက ဖတ်သောကြောင့် network request
     * အသစ် လုံးဝ မလိုပါ (12-hour TTL တူ)။
     */
    public static String getSamusarProxyUrl(
            JSONObject content
    ) {
        if (content == null) {
            return "";
        }

        JSONObject samusar =
                content.optJSONObject("samusar");

        if (samusar == null) {
            return "";
        }

        String proxy =
                samusar.optString("proxy", "");

        return proxy == null ? "" : proxy.trim();
    }

    /*
     * Javtiful base URL + listing path — server-configurable။
     *
     * Backend /app-content မှ "javtiful": {"base": "<url>",
     * "listing": "<path>"} ကို ဖတ်သည်။ Field မရှိလျှင် /
     * လွတ်နေလျှင် "" ပြန်ပေးမည် — JavtifulClient က
     * hardcoded default သို့ fallback လုပ်မည်။
     *
     * cached /app-content JSON ထဲက ဖတ်သောကြောင့်
     * network request အသစ် လုံးဝ မလိုပါ။
     */
    public static String getJavtifulBaseUrl(
            JSONObject content
    ) {
        if (content == null) {
            return "";
        }

        JSONObject javtiful =
                content.optJSONObject("javtiful");

        if (javtiful == null) {
            return "";
        }

        String base =
                javtiful.optString("base", "");

        return base == null ? "" : base.trim();
    }

    public static String getJavtifulListingPath(
            JSONObject content
    ) {
        if (content == null) {
            return "";
        }

        JSONObject javtiful =
                content.optJSONObject("javtiful");

        if (javtiful == null) {
            return "";
        }

        String listing =
                javtiful.optString("listing", "");

        return listing == null ? "" : listing.trim();
    }

    /*
     * Samusar listing path — server-configurable။
     *
     * Backend /app-content မှ "samusar": {"listing": "<path>"}
     * ကို ဖတ်သည်။ proxy base URL လိုပဲ D1 မှ ပြင်နိုင်သည်။
     * Field မရှိလျှင် "" — SamusarClient က hardcoded
     * default ("/latest-updates") သို့ fallback လုပ်မည်။
     */
    public static String getSamusarListingPath(
            JSONObject content
    ) {
        if (content == null) {
            return "";
        }

        JSONObject samusar =
                content.optJSONObject("samusar");

        if (samusar == null) {
            return "";
        }

        String listing =
                samusar.optString("listing", "");

        return listing == null ? "" : listing.trim();
    }

    /*
     * နောက်ဆုံး /app-content မှ ရသော proxy URL
     * (memory cache)။ loadBanner က content ရတိုင်း
     * update လုပ်သည် — cached path ရော network
     * path ရော။
     */
    private static volatile String cachedSamusarProxyUrl =
            "";

    private static volatile String cachedJavtifulBaseUrl =
            "";

    private static volatile String cachedJavtifulListingPath =
            "";

    private static volatile String cachedSamusarListingPath =
            "";

    private static void updateCachedSamusarProxyUrl(
            JSONObject content
    ) {
        cachedSamusarProxyUrl =
                getSamusarProxyUrl(content);
    }

    private static void updateCachedSourceUrls(
            JSONObject content
    ) {
        cachedJavtifulBaseUrl =
                getJavtifulBaseUrl(content);
        cachedJavtifulListingPath =
                getJavtifulListingPath(content);
        cachedSamusarListingPath =
                getSamusarListingPath(content);
    }

    public static String getCachedSamusarProxyUrl() {
        return cachedSamusarProxyUrl;
    }

    public static String getCachedJavtifulBaseUrl() {
        return cachedJavtifulBaseUrl;
    }

    public static String getCachedJavtifulListingPath() {
        return cachedJavtifulListingPath;
    }

    public static String getCachedSamusarListingPath() {
        return cachedSamusarListingPath;
    }

    public interface Callback {

        void onContent(JSONObject content);

        void onError(Exception error);
    }

    /*
     * Cached banner ကိုချက်ချင်းပြမည်။
     *
     * Cache 12 hours ကျော်မှ /app-content ကို
     * ပြန် request လုပ်မည်။ Server ဘက်မှာ CDN cache
     * ရှိလို့ D1 request တိုက်ရိုက်မတက်ပါ။
     */
    public static void loadBanner(
            Context context,
            Callback callback
    ) {
        Context appContext =
                context.getApplicationContext();

        SharedPreferences preferences =
                preferences(appContext);

        String cachedBody =
                preferences.getString(
                        KEY_BANNER_BODY,
                        ""
                );

        JSONObject cached =
                parseCachedBody(
                        preferences,
                        KEY_BANNER_BODY,
                        cachedBody
                );

        if (cached != null) {
            updateCachedSamusarProxyUrl(cached);
            updateCachedSourceUrls(cached);
            callback.onContent(cached);
        }

        long savedAt =
                preferences.getLong(
                        KEY_BANNER_SAVED_AT,
                        0L
                );

        boolean fresh =
                cached != null &&
                savedAt > 0L &&
                System.currentTimeMillis() - savedAt
                        < BANNER_CACHE_TTL_MS;

        if (
                fresh ||
                !NetworkUtils.isOnline(appContext)
        ) {
            if (
                    cached == null &&
                    !NetworkUtils.isOnline(appContext)
            ) {
                callback.onError(
                        new IllegalStateException(
                                "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။"
                        )
                );
            }

            return;
        }

        EXECUTOR.execute(() ->
                requestBanner(
                        preferences,
                        cached != null,
                        callback
                )
        );
    }

    /*
     * Notification ကို local TTL မသုံးဘဲ
     * ETag ဖြင့် server နဲ့ validate လုပ်မည်။
     *
     * Same notification ဖြစ်လျှင် 304 ပြန်လာပြီး
     * JSON body download လုပ်စရာမလိုပါ။
     */
    public static void refreshNotification(
        Context context,
        Callback callback
) {
    Context appContext =
            context.getApplicationContext();

    SharedPreferences preferences =
            preferences(appContext);

    String cachedBody =
            preferences.getString(
                    KEY_NOTICE_BODY,
                    ""
            );

    JSONObject cached =
            parseCachedBody(
                    preferences,
                    KEY_NOTICE_BODY,
                    cachedBody
            );

    long savedAt =
            preferences.getLong(
                    KEY_NOTICE_SAVED_AT,
                    0L
            );

    boolean fresh =
            cached != null &&
            savedAt > 0L &&
            System.currentTimeMillis() - savedAt
                    < NOTICE_CACHE_TTL_MS;

    /*
     * 30 minutes အတွင်း cache ရှိနေရင်
     * network request လုံးဝမပို့ပါ။
     */
    if (fresh) {
        callback.onContent(cached);
        return;
    }

    /*
     * Internet မရှိလျှင် ရှိပြီးသား notification ကို
     * stale ဖြစ်နေလည်း ပြမည်။
     */
    if (!NetworkUtils.isOnline(appContext)) {
        if (cached != null) {
            callback.onContent(cached);
        } else {
            callback.onError(
                    new IllegalStateException(
                            "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။"
                    )
            );
        }

        return;
    }

    /*
     * Activity callbacks တစ်ပြိုင်နက်ဝင်လာလျှင်
     * duplicate network request မပို့စေရန်။
     */
    if (
            !NOTICE_REQUEST_IN_FLIGHT
                    .compareAndSet(
                            false,
                            true
                    )
    ) {
        /*
         * Request တစ်ခုလုပ်နေပြီး cache ရှိရင်
         * ရှိပြီးသား content ကိုပြမည်။
         */
        if (cached != null) {
            callback.onContent(cached);
        }

        return;
    }

    EXECUTOR.execute(() -> {
        try {
            requestNotification(
                    preferences,
                    cachedBody,
                    cached,
                    callback
            );
        } finally {
            NOTICE_REQUEST_IN_FLIGHT.set(
                    false
            );
        }
    });
}


    private static void requestBanner(
            SharedPreferences preferences,
            boolean hasCachedBody,
            Callback callback
    ) {
        HttpURLConnection connection = null;

        try {
            connection =
                    openConnection(
                            "app-content",
                            true
                    );

            int statusCode =
                    connection.getResponseCode();

            if (
                    statusCode < 200 ||
                    statusCode >= 300
            ) {
                throw requestError(
                        connection,
                        statusCode,
                        "Banner request failed"
                );
            }

            String responseBody =
                    readStream(
                            connection.getInputStream()
                    );

            JSONObject json =
                    new JSONObject(
                            responseBody
                    );

            updateCachedSamusarProxyUrl(json);
            updateCachedSourceUrls(json);

            preferences
                    .edit()
                    .putString(
                            KEY_BANNER_BODY,
                            json.toString()
                    )
                    .putLong(
                            KEY_BANNER_SAVED_AT,
                            System.currentTimeMillis()
                    )
                    .apply();

            callback.onContent(json);

        } catch (Exception error) {
            /*
             * Cached banner ပြပြီးသားဖြစ်လျှင်
             * refresh failure ကို silent fallback လုပ်မည်။
             */
            if (!hasCachedBody) {
                callback.onError(error);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void requestNotification(
            SharedPreferences preferences,
            String cachedBody,
            JSONObject cached,
            Callback callback
    ) {
        HttpURLConnection connection = null;

        try {
            connection =
                    openConnection(
                            "app-notification",
                            false
                    );

            String etag =
                    preferences.getString(
                            KEY_NOTICE_ETAG,
                            ""
                    );

            /*
             * Cached JSON body ရှိမှ If-None-Match ပို့မည်။
             * Body မရှိဘဲ 304 ရသွားခြင်းကိုကာကွယ်သည်။
             */
            if (
                    cachedBody != null &&
                    !cachedBody.trim().isEmpty() &&
                    etag != null &&
                    !etag.trim().isEmpty()
            ) {
                connection.setRequestProperty(
                        "If-None-Match",
                        etag.trim()
                );
            }

            int statusCode =
                    connection.getResponseCode();

            if (
        statusCode ==
                HttpURLConnection
                        .HTTP_NOT_MODIFIED
) {
    if (cached == null) {
        throw new IllegalStateException(
                "Cached notification မရှိပါ။"
        );
    }

    /*
     * Server က notification မပြောင်းသေးကြောင်း
     * အတည်ပြုခဲ့သောကြောင့် local TTL ပြန်စမည်။
     */
    preferences
            .edit()
            .putLong(
                    KEY_NOTICE_SAVED_AT,
                    System.currentTimeMillis()
            )
            .apply();

    callback.onContent(cached);
    return;
}


            if (
                    statusCode < 200 ||
                    statusCode >= 300
            ) {
                throw requestError(
                        connection,
                        statusCode,
                        "Notification request failed"
                );
            }

            String responseBody =
                    readStream(
                            connection.getInputStream()
                    );

            JSONObject json =
                    new JSONObject(
                            responseBody
                    );

            String responseETag =
                    connection.getHeaderField(
                            "ETag"
                    );

            SharedPreferences.Editor editor =
        preferences
                .edit()
                .putString(
                        KEY_NOTICE_BODY,
                        json.toString()
                )
                .putLong(
                        KEY_NOTICE_SAVED_AT,
                        System.currentTimeMillis()
                );


            if (
                    responseETag != null &&
                    !responseETag.trim().isEmpty()
            ) {
                editor.putString(
                        KEY_NOTICE_ETAG,
                        responseETag.trim()
                );
            } else {
                editor.remove(
                        KEY_NOTICE_ETAG
                );
            }

            editor.apply();

            callback.onContent(json);

        } catch (Exception error) {
            /*
             * Network/server error ဖြစ်လျှင်
             * cached notification ကို fallback သုံးမည်။
             */
            if (cached != null) {
                callback.onContent(cached);
            } else {
                callback.onError(error);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection openConnection(
            String path,
            boolean allowHttpCache
    ) throws Exception {
        if (
                BuildConfig.CMFLIX_APP_KEY == null ||
                BuildConfig.CMFLIX_APP_KEY
                        .trim()
                        .isEmpty()
        ) {
            throw new IllegalStateException(
                    "App configuration is missing."
            );
        }

        String apiBaseUrl =
        ConfigManager.requireApiBaseUrl();

URL url =
        new URL(
                apiBaseUrl +
                        normalizePath(path)
        );


        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setUseCaches(
                allowHttpCache
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setRequestProperty(
                "x-cmflix-app-key",
                BuildConfig.CMFLIX_APP_KEY
        );

        connection.setRequestProperty(
                "x-cmflix-device-id",
                SessionManager.getDeviceId()
        );

        if (!allowHttpCache) {
            /*
             * Notification ကို proxy/browser cache ကနေ
             * မယူဘဲ ETag validation request ကို server
             * ဆီရောက်စေရန်။
             */
            connection.setRequestProperty(
                    "Cache-Control",
                    "no-cache"
            );

            connection.setRequestProperty(
                    "Pragma",
                    "no-cache"
            );
        }

        return connection;
    }

    private static Exception requestError(
            HttpURLConnection connection,
            int statusCode,
            String fallback
    ) {
        try {
            String errorBody =
                    readStream(
                            connection.getErrorStream()
                    );

            if (
                    errorBody != null &&
                    !errorBody.trim().isEmpty()
            ) {
                return new IllegalStateException(
                        errorBody
                );
            }
        } catch (Exception ignored) {
        }

        return new IllegalStateException(
                fallback + ": " + statusCode
        );
    }

    private static SharedPreferences preferences(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    private static JSONObject parseCachedBody(
            SharedPreferences preferences,
            String key,
            String cachedBody
    ) {
        if (
                cachedBody == null ||
                cachedBody.trim().isEmpty()
        ) {
            return null;
        }

        try {
            return new JSONObject(
                    cachedBody
            );
        } catch (Exception ignored) {
            preferences
                    .edit()
                    .remove(key)
                    .apply();

            return null;
        }
    }

    private static String readStream(
            InputStream input
    ) throws Exception {
        if (input == null) {
            return "";
        }

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        input,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            StringBuilder result =
                    new StringBuilder();

            String line;

            while (
                    (line = reader.readLine()) != null
            ) {
                result.append(line);
            }

            return result.toString();
        }
    }
private static String normalizePath(
        String path
) {
    if (path == null) {
        return "";
    }

    String normalized =
            path.trim();

    while (
            normalized.startsWith("/")
    ) {
        normalized =
                normalized.substring(1);
    }

    return normalized;
}

    public static void clearCache(
            Context context
    ) {
        preferences(
                context.getApplicationContext()
        )
                .edit()
                .clear()
                .apply();
    }
}