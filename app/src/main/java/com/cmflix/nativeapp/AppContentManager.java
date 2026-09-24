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

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final AtomicBoolean
            NOTICE_REQUEST_IN_FLIGHT =
            new AtomicBoolean(false);

    private AppContentManager() {
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
         * Periodic timer နဲ့ Activity resume callback
         * တစ်ချိန်တည်းဝင်လာလျှင် duplicate request
         * မပို့စေရန်။
         */
        if (
                !NOTICE_REQUEST_IN_FLIGHT
                        .compareAndSet(
                                false,
                                true
                        )
        ) {
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
