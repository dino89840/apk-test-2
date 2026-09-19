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

public final class AppContentManager {

    /*
     * Admin က banner ကို တစ်လ ၁/၂ ပုံသာ
     * ပြောင်းမည်ဖြစ်သောကြောင့် app config ကို
     * 12 နာရီ cache လုပ်ထားသည်။
     *
     * Device တစ်လုံးအတွက် တစ်ရက်အများဆုံး
     * ၂ request ခန့်သာဝင်မည်။
     */
    private static final long CACHE_TTL_MS =
            12L * 60L * 60L * 1000L;

    private static final String PREFS =
            "cmflix_app_content_v1";

    private static final String KEY_BODY =
            "cached_body";

    private static final String KEY_ETAG =
            "cached_etag";

    private static final String KEY_SAVED_AT =
            "cached_saved_at";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private AppContentManager() {
    }

    public interface Callback {
        void onContent(JSONObject content);

        void onError(Exception error);
    }

    public static void load(
            Context context,
            Callback callback
    ) {
        Context appContext =
                context.getApplicationContext();

        SharedPreferences preferences =
                appContext.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        String cachedBody =
                preferences.getString(
                        KEY_BODY,
                        ""
                );

        long savedAt =
                preferences.getLong(
                        KEY_SAVED_AT,
                        0L
                );

        boolean cacheFresh =
                !cachedBody.isEmpty() &&
                savedAt > 0L &&
                System.currentTimeMillis() - savedAt
                        < CACHE_TTL_MS;

        if (cacheFresh) {
            try {
                callback.onContent(
                        new JSONObject(
                                cachedBody
                        )
                );

                return;
            } catch (Exception ignored) {
                preferences
                        .edit()
                        .remove(KEY_BODY)
                        .remove(KEY_ETAG)
                        .remove(KEY_SAVED_AT)
                        .apply();
            }
        }

        /*
         * Cache ဟောင်းရှိရင် UI ကိုချက်ချင်းပြမယ်။
         * ပြီးမှ background မှာ refresh လုပ်မယ်။
         */
        if (!cachedBody.isEmpty()) {
            try {
                callback.onContent(
                        new JSONObject(
                                cachedBody
                        )
                );
            } catch (Exception ignored) {
            }
        }

        EXECUTOR.execute(() ->
                requestLatest(
                        appContext,
                        preferences,
                        cachedBody,
                        callback
                )
        );
    }

    private static void requestLatest(
            Context context,
            SharedPreferences preferences,
            String staleBody,
            Callback callback
    ) {
        HttpURLConnection connection = null;

        try {
            URL url =
                    new URL(
                            BuildConfig.API_BASE_URL +
                                    "app-content"
                    );

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(18000);
            connection.setUseCaches(true);

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

            String etag =
                    preferences.getString(
                            KEY_ETAG,
                            ""
                    );

            if (!etag.isEmpty()) {
                connection.setRequestProperty(
                        "If-None-Match",
                        etag
                );
            }

            int statusCode =
                    connection.getResponseCode();

            if (
                    statusCode ==
                            HttpURLConnection.HTTP_NOT_MODIFIED
            ) {
                preferences
                        .edit()
                        .putLong(
                                KEY_SAVED_AT,
                                System.currentTimeMillis()
                        )
                        .apply();

                if (!staleBody.isEmpty()) {
                    callback.onContent(
                            new JSONObject(
                                    staleBody
                            )
                    );
                }

                return;
            }

            if (
                    statusCode < 200 ||
                    statusCode >= 300
            ) {
                throw new IllegalStateException(
                        "App content request failed: " +
                                statusCode
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
                                    KEY_BODY,
                                    json.toString()
                            )
                            .putLong(
                                    KEY_SAVED_AT,
                                    System.currentTimeMillis()
                            );

            if (
                    responseETag != null &&
                    !responseETag.trim().isEmpty()
            ) {
                editor.putString(
                        KEY_ETAG,
                        responseETag.trim()
                );
            }

            editor.apply();

            callback.onContent(json);
        } catch (Exception error) {
            /*
             * Cache အဟောင်းရှိပြီးသားဆို network error
             * ကြောင့် UI ကိုမဖျက်ပါ။
             */
            if (
                    staleBody == null ||
                    staleBody.isEmpty()
            ) {
                callback.onError(error);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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

    public static void clearCache(
            Context context
    ) {
        context.getApplicationContext()
                .getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .clear()
                .apply();
    }
}
