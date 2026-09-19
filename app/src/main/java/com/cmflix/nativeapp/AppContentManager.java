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
     * Banner/notification JSON ကို local storage မှာ
     * သိမ်းထားမည်။
     *
     * Online ဖြစ်ရင် app ဖွင့်တိုင်း ETag ဖြင့်
     * config ပြောင်း/မပြောင်း စစ်မည်။
     *
     * Server data မပြောင်းရင် 304 response ပဲရပြီး
     * JSON body အပြည့်ပြန် download လုပ်ရန်မလိုပါ။
     *
     * Offline/network error ဖြစ်မှ cached content ကို
     * fallback အဖြစ်အသုံးပြုမည်။
     */
    private static final String PREFS =
            "cmflix_app_content_v2";

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

        /*
         * Internet မရှိလျှင် network request မလုပ်ဘဲ
         * cached banner/notification ကိုအသုံးပြုမည်။
         */
        if (!NetworkUtils.isOnline(appContext)) {
            JSONObject cached =
                    parseCachedBody(
                            preferences,
                            cachedBody
                    );

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
         * Online ဖြစ်လျှင် server ကို ETag ဖြင့်
         * revalidate လုပ်ပြီးမှ content ပြမည်။
         *
         * ဒီလိုလုပ်ထားလို့ notification အသစ်ကို
         * cache TTL က ပိတ်ထားမည်မဟုတ်ပါ။
         */
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
            String cachedBody,
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

            /*
             * HttpURLConnection/proxy cache အဟောင်းကို
             * တိုက်ရိုက်အသုံးမပြုစေရန်။
             *
             * ETag/304 ကိုတော့ ဆက်အသုံးပြုမည်။
             */
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
                    "Pragma",
                    "no-cache"
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

            /*
             * Cached body ရှိမှသာ ETag ပို့မည်။
             * Body မရှိဘဲ 304 ပြန်လာခြင်းကိုကာကွယ်ရန်။
             */
            if (
                    cachedBody != null &&
                    !cachedBody.isEmpty() &&
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
                JSONObject cached =
                        parseCachedBody(
                                preferences,
                                cachedBody
                        );

                if (cached == null) {
                    throw new IllegalStateException(
                            "Cached app content မရပါ။"
                    );
                }

                preferences
                        .edit()
                        .putLong(
                                KEY_SAVED_AT,
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
                String errorBody =
                        readStream(
                                connection.getErrorStream()
                        );

                throw new IllegalStateException(
                        errorBody == null ||
                                errorBody.trim().isEmpty()
                                ? "App content request failed: " +
                                statusCode
                                : errorBody
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
            } else {
                editor.remove(KEY_ETAG);
            }

            editor.apply();

            callback.onContent(json);

        } catch (Exception error) {

            /*
             * Request မအောင်မြင်ရင် cache အဟောင်းကို
             * fallback အဖြစ်ဆက်သုံးမည်။
             */
            JSONObject cached =
                    parseCachedBody(
                            preferences,
                            cachedBody
                    );

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

    private static JSONObject parseCachedBody(
            SharedPreferences preferences,
            String cachedBody
    ) {
        if (
                cachedBody == null ||
                cachedBody.trim().isEmpty()
        ) {
            return null;
        }

        try {
            return new JSONObject(cachedBody);
        } catch (Exception ignored) {
            preferences
                    .edit()
                    .remove(KEY_BODY)
                    .remove(KEY_ETAG)
                    .remove(KEY_SAVED_AT)
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
