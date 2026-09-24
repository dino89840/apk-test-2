package com.cmflix.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ConfigManager {

    /*
     * GitHub config source ကို app ထဲမှာ
     * ဒီတစ်နေရာတည်းမှာသာ သတ်မှတ်ထားသည်။
     */
    private static final String CONFIG_URL =
            "raw.githubusercontent.com/cmflix/cmflix/main/config.json";

    private static final long CONFIG_CACHE_TTL_MS =
            24L * 60L * 60L * 1000L;

    /*
     * First install မှာ request မအောင်မြင်လျှင်
     * API calls အများကြီးက config ကို တစ်ပြိုင်နက်တည်း
     * ထပ်ခေါ်မနေစေရန် short retry throttle။
     */
    private static final long FAILED_RETRY_DELAY_MS =
            60L * 1000L;

    private static final String PREFS =
            "cmflix_runtime_config_v1";

    private static final String KEY_API_BASE_URL =
            "api_base_url";

    private static final String KEY_SAVED_AT =
            "saved_at";

    private static final Object LOCK =
            new Object();

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static SharedPreferences preferences;
    private static Context applicationContext;

    private static volatile String cachedApiBaseUrl = "";
    private static volatile long cachedSavedAt = 0L;

    private static volatile boolean refreshInFlight = false;
    private static volatile long lastRefreshAttemptAt = 0L;

    private ConfigManager() {
    }

    public static void initialize(
            Context context
    ) {
        if (context == null) {
            return;
        }

        synchronized (LOCK) {
            if (preferences == null) {
                applicationContext =
                        context.getApplicationContext();

                preferences =
                        applicationContext
                                .getSharedPreferences(
                                        PREFS,
                                        Context.MODE_PRIVATE
                                );

                loadCachedConfigLocked();
            }
        }

        refreshIfNeeded();
    }

    /*
     * ApiClient/AppContentManager ရဲ့ worker thread က
     * ဒီ method ကိုခေါ်မည်။
     *
     * Valid cache ရှိလျှင် ချက်ချင်းပြန်ပေးမည်။
     * Cache မရှိသေးလျှင် config ကို download လုပ်ပြီးမှ
     * API base URL ကိုပြန်ပေးမည်။
     *
     * ဒီ method ကို UI thread မှ မခေါ်ရ။
     */
    public static String requireApiBaseUrl()
            throws Exception {

        ensureInitialized();

        String available =
                cachedApiBaseUrl;

        /*
         * Cached URL ရှိနေပါက stale ဖြစ်နေလည်း
         * API request ကို မတားဘဲ ဆက်သုံးမည်။
         *
         * Stale refresh ကို initialize()/refreshIfNeeded()
         * က background မှာလုပ်နေမည်။
         */
        if (
                available != null &&
                !available.trim().isEmpty()
        ) {
            return available;
        }

        synchronized (LOCK) {
            available = cachedApiBaseUrl;

            if (
                    available != null &&
                    !available.trim().isEmpty()
            ) {
                return available;
            }

            long now =
                    System.currentTimeMillis();

            if (
                    lastRefreshAttemptAt > 0L &&
                    now - lastRefreshAttemptAt <
                            FAILED_RETRY_DELAY_MS
            ) {
                throw new IllegalStateException(
                        "App configuration ကို ရယူ၍မရသေးပါ။"
                );
            }

            lastRefreshAttemptAt = now;

            try {
                String downloaded =
                        downloadConfig();

                saveConfigLocked(downloaded);

                return cachedApiBaseUrl;
            } catch (Exception error) {
                /*
                 * Download failure ဖြစ်သော်လည်း cache ကို
                 * မဖျက်ပါ။ Cache မရှိမှသာ safe error ပေးမည်။
                 */
                available = cachedApiBaseUrl;

                if (
                        available != null &&
                        !available.trim().isEmpty()
                ) {
                    return available;
                }

                throw new IllegalStateException(
                        "App configuration ကို ရယူ၍မရပါ။ " +
                                "အင်တာနက်ချိတ်ဆက်မှုကို စစ်ဆေးပြီး " +
                                "ပြန်လည်ကြိုးစားပါ။",
                        error
                );
            }
        }
    }

    /*
     * App စဖွင့်ချိန် cache မရှိလျှင် သို့မဟုတ်
     * 24 hours ကျော်သွားလျှင် background refresh လုပ်မည်။
     */
    public static void refreshIfNeeded() {
        if (preferences == null) {
            return;
        }

        long now =
                System.currentTimeMillis();

        boolean hasCachedUrl =
                cachedApiBaseUrl != null &&
                        !cachedApiBaseUrl
                                .trim()
                                .isEmpty();

        boolean fresh =
                hasCachedUrl &&
                        cachedSavedAt > 0L &&
                        now - cachedSavedAt <
                                CONFIG_CACHE_TTL_MS;

        if (fresh || refreshInFlight) {
            return;
        }

        refreshInFlight = true;

        EXECUTOR.execute(() -> {
            try {
                synchronized (LOCK) {
                    long currentTime =
                            System.currentTimeMillis();

                    boolean cacheStillFresh =
                            cachedApiBaseUrl != null &&
                                    !cachedApiBaseUrl
                                            .trim()
                                            .isEmpty() &&
                                    cachedSavedAt > 0L &&
                                    currentTime -
                                            cachedSavedAt <
                                            CONFIG_CACHE_TTL_MS;

                    if (cacheStillFresh) {
                        return;
                    }

                    if (
                            lastRefreshAttemptAt > 0L &&
                            currentTime -
                                    lastRefreshAttemptAt <
                                    FAILED_RETRY_DELAY_MS
                    ) {
                        return;
                    }

                    lastRefreshAttemptAt =
                            currentTime;

                    String downloaded =
                            downloadConfig();

                    saveConfigLocked(downloaded);
                }
            } catch (Exception ignored) {
                /*
                 * Config server/GitHub ယာယီမရလျှင်
                 * cachedApiBaseUrl ကို မဖျက်ပါ။
                 *
                 * Cache ရှိသေးသရွေ့ app က cache နဲ့
                 * ဆက်အလုပ်လုပ်နိုင်မည်။
                 */
            } finally {
                refreshInFlight = false;
            }
        });
    }

    public static String getCachedApiBaseUrl() {
        String value =
                cachedApiBaseUrl;

        return value == null
                ? ""
                : value;
    }

    private static void ensureInitialized() {
        if (preferences == null) {
            throw new IllegalStateException(
                    "ConfigManager.initialize() must be called first."
            );
        }
    }

    private static void loadCachedConfigLocked() {
        String stored =
                preferences.getString(
                        KEY_API_BASE_URL,
                        ""
                );

        long storedAt =
                preferences.getLong(
                        KEY_SAVED_AT,
                        0L
                );

        try {
            cachedApiBaseUrl =
                    normalizeAndValidateApiUrl(
                            stored
                    );

            cachedSavedAt =
                    storedAt;
        } catch (Exception ignored) {
            cachedApiBaseUrl = "";
            cachedSavedAt = 0L;

            preferences
                    .edit()
                    .remove(KEY_API_BASE_URL)
                    .remove(KEY_SAVED_AT)
                    .apply();
        }
    }

    private static String downloadConfig()
            throws Exception {

        HttpURLConnection connection = null;

        try {
            URL source =
                    new URL(CONFIG_URL);

            if (
                    !"https".equalsIgnoreCase(
                            source.getProtocol()
                    )
            ) {
                throw new SecurityException(
                        "Config source must use HTTPS."
                );
            }

            connection =
                    (HttpURLConnection)
                            source.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Cache-Control",
                    "no-cache"
            );

            connection.setRequestProperty(
                    "User-Agent",
                    "CMFLIX-Native-Android"
            );

            int statusCode =
                    connection.getResponseCode();

            if (
                    statusCode < 200 ||
                    statusCode >= 300
            ) {
                throw new IllegalStateException(
                        "Config request failed: HTTP " +
                                statusCode
                );
            }

            String responseBody =
                    readStream(
                            connection.getInputStream()
                    );

            if (
                    responseBody == null ||
                    responseBody.trim().isEmpty()
            ) {
                throw new IllegalStateException(
                        "Config response is empty."
                );
            }

            JSONObject config =
                    new JSONObject(
                            responseBody
                    );

            String apiBaseUrl =
                    config.optString(
                            "api_base_url",
                            ""
                    );

            return normalizeAndValidateApiUrl(
                    apiBaseUrl
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void saveConfigLocked(
            String apiBaseUrl
    ) throws Exception {

        String normalized =
                normalizeAndValidateApiUrl(
                        apiBaseUrl
                );

        String previous =
                cachedApiBaseUrl == null
                        ? ""
                        : cachedApiBaseUrl.trim();

        long now =
                System.currentTimeMillis();

        cachedApiBaseUrl = normalized;
        cachedSavedAt = now;

        preferences
                .edit()
                .putString(
                        KEY_API_BASE_URL,
                        normalized
                )
                .putLong(
                        KEY_SAVED_AT,
                        now
                )
                .apply();

        /*
         * Runtime မှာ API host ပြောင်းသွားလျှင်
         * old host ရဲ့ public responses/banner/notice
         * cache တွေကို domain အသစ်နဲ့ မရောစေရန်ရှင်းမည်။
         */
        if (
                !previous.isEmpty() &&
                !sameHost(
                        previous,
                        normalized
                )
        ) {
            ApiClient.clearPublicCache();

            if (applicationContext != null) {
                AppContentManager.clearCache(
                        applicationContext
                );
            }
        }
    }

    /*
     * Config မှာ domain ပဲထည့်ထားလျှင်
     * existing backend structure အတိုင်း /api/ ထည့်မည်။
     *
     * Config မှာ path ပါပြီးသားဆို ထို path ကိုအသုံးပြုမည်။
     */
    private static String normalizeAndValidateApiUrl(
            String value
    ) throws Exception {

        if (
                value == null ||
                value.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "api_base_url is missing."
            );
        }

        String candidate =
                value.trim();

        URI uri =
                new URI(candidate)
                        .normalize();

        String scheme =
                uri.getScheme();

        String host =
                uri.getHost();

        if (
                !"https".equalsIgnoreCase(
                        scheme
                )
        ) {
            throw new SecurityException(
                    "api_base_url must use HTTPS."
            );
        }

        if (
                host == null ||
                host.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "api_base_url host is invalid."
            );
        }

        if (
                uri.getUserInfo() != null ||
                uri.getQuery() != null ||
                uri.getFragment() != null
        ) {
            throw new IllegalArgumentException(
                    "api_base_url contains unsupported parts."
            );
        }

        if (isUnsafeHost(host)) {
            throw new SecurityException(
                    "api_base_url host is not allowed."
            );
        }

        String path =
                uri.getPath();

        String normalizedPath;

        if (
                path == null ||
                path.trim().isEmpty() ||
                "/".equals(path.trim())
        ) {
            normalizedPath = "/api/";
        } else {
            normalizedPath =
                    path.endsWith("/")
                            ? path
                            : path + "/";
        }

        URI normalized =
                new URI(
                        "https",
                        null,
                        host,
                        uri.getPort(),
                        normalizedPath,
                        null,
                        null
                );

        return normalized.toASCIIString();
    }

    private static boolean isUnsafeHost(
            String host
    ) {
        String normalized =
                host.toLowerCase(
                        Locale.US
                );

        if (
                "localhost".equals(normalized) ||
                normalized.endsWith(".localhost") ||
                "::1".equals(normalized) ||
                "0:0:0:0:0:0:0:1".equals(normalized)
        ) {
            return true;
        }

        if (
                normalized.startsWith("127.") ||
                normalized.startsWith("10.") ||
                normalized.startsWith("192.168.") ||
                normalized.startsWith("169.254.") ||
                normalized.startsWith("0.")
        ) {
            return true;
        }

        if (normalized.startsWith("172.")) {
            String[] parts =
                    normalized.split("\\.");

            if (parts.length >= 2) {
                try {
                    int second =
                            Integer.parseInt(
                                    parts[1]
                            );

                    if (
                            second >= 16 &&
                            second <= 31
                    ) {
                        return true;
                    }
                } catch (
                        NumberFormatException ignored
                ) {
                }
            }
        }

        return false;
    }

    private static boolean sameHost(
            String first,
            String second
    ) {
        try {
            URL firstUrl =
                    new URL(first);

            URL secondUrl =
                    new URL(second);

            return firstUrl
                    .getHost()
                    .equalsIgnoreCase(
                            secondUrl.getHost()
                    ) &&
                    effectivePort(firstUrl) ==
                            effectivePort(secondUrl);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int effectivePort(
            URL url
    ) {
        int port =
                url.getPort();

        return port >= 0
                ? port
                : url.getDefaultPort();
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
}
