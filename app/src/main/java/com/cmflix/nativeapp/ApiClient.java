package com.cmflix.nativeapp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.SharedPreferences;

import java.security.MessageDigest;


public final class ApiClient {

    private static final String SESSION_COOKIE_NAME =
            "__Host-cmflix_session";

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(4);

    public interface Callback {
        void onSuccess(JSONObject json);
        void onError(Exception error);
    }

    private ApiClient() {
    }
    /*
 * v2 သုံးထားတာက app version အဟောင်းက
 * pagination page-1 cache များကို ပြန်မယူစေရန်ဖြစ်သည်။
 */
private static final String CACHE_PREFS =
        "cmflix_public_api_cache_v3";

private static final String[] LEGACY_CACHE_PREFS = {
        "cmflix_public_api_cache_v1",
        "cmflix_public_api_cache_v2"
};

private static final String CACHE_MIGRATION_PREFS =
        "cmflix_cache_security_migration";

private static final String KEY_SENSITIVE_CACHE_CLEARED =
        "sensitive_cache_cleared_v3";

private static SharedPreferences cachePreferences;

private static Context applicationContext;


    public static synchronized void initialize(
        Context context
) {
    if (context == null) {
        return;
    }

    applicationContext =
            context.getApplicationContext();

    SessionManager.initialize(
            applicationContext
    );

    /*
     * App version အဟောင်းက video_url/download_url ပါသော
     * detail response များကို SharedPreferences ထဲ
     * သိမ်းထားနိုင်သောကြောင့် တစ်ကြိမ်ရှင်းမယ်။
     */
    SharedPreferences migrationPreferences =
            applicationContext
                    .getSharedPreferences(
                            CACHE_MIGRATION_PREFS,
                            Context.MODE_PRIVATE
                    );

    boolean alreadyCleared =
            migrationPreferences.getBoolean(
                    KEY_SENSITIVE_CACHE_CLEARED,
                    false
            );

    if (!alreadyCleared) {
        for (String legacyName :
                LEGACY_CACHE_PREFS) {

            applicationContext
                    .getSharedPreferences(
                            legacyName,
                            Context.MODE_PRIVATE
                    )
                    .edit()
                    .clear()
                    .commit();
        }

        migrationPreferences
                .edit()
                .putBoolean(
                        KEY_SENSITIVE_CACHE_CLEARED,
                        true
                )
                .commit();
    }

    if (cachePreferences == null) {
        cachePreferences =
                applicationContext
                        .getSharedPreferences(
                                CACHE_PREFS,
                                Context.MODE_PRIVATE
                        );
    }
}



    public static void get(
            String path,
            Callback callback
    ) {
        request("GET", path, null, callback);
    }
    public static void getCached(
        String path,
        long maxAgeMillis,
        Callback callback
) {
    if (
            cachePreferences == null ||
            !isPublicCacheablePath(path)
    ) {
        get(path, callback);
        return;
    }

    EXECUTOR.execute(() -> {
        String key = cacheKey(path);

        long savedAt =
                cachePreferences.getLong(
                        key + "_time",
                        0L
                );

        String cachedBody =
                cachePreferences.getString(
                        key + "_body",
                        ""
                );

        boolean fresh =
                savedAt > 0L &&
                !cachedBody.isEmpty() &&
                System.currentTimeMillis() - savedAt
                        < maxAgeMillis;

        boolean online =
                NetworkUtils.isOnline(
                        applicationContext
                );

        /*
         * Cache က fresh ဖြစ်နေရင် cache ကိုသုံးမယ်။
         *
         * Internet မရှိရင် cache ဟောင်းဖြစ်နေသော်လည်း
         * ရှိပြီးသား data ကို ချက်ချင်းပြမယ်။
         */
        if (
                !cachedBody.isEmpty() &&
                (fresh || !online)
        ) {
            try {
                callback.onSuccess(
                        new JSONObject(cachedBody)
                );

                return;
            } catch (Exception ignored) {
                cachePreferences
                        .edit()
                        .remove(key + "_time")
                        .remove(key + "_body")
                        .apply();
            }
        }

        /*
         * Cache မရှိဘဲ internet လည်းမရှိရင်
         * host/domain စာတန်းပါတဲ့ system exception
         * မပြဘဲ callback error ပို့မယ်။
         */
        if (!online) {
            callback.onError(
                    new java.net.UnknownHostException(
                            "No internet connection"
                    )
            );

            return;
        }

        get(
                path,
                new Callback() {
                    @Override
public void onSuccess(
        JSONObject json
) {
    /*
     * Detail response ကို cache လုပ်နိုင်သည်။
     *
     * Title list ဖြစ်လျှင် hasMore=false ဖြစ်သော
     * single-page category ကိုသာ cache လုပ်မည်။
     *
     * hasMore=true ကို cache လုပ်လိုက်လျှင်
     * page 1 အဟောင်းနှင့် page 2 အသစ် ရောသွားနိုင်သည်။
     */
    if (
            shouldPersistPublicResponse(
                    path,
                    json
            )
    ) {
        cachePreferences
                .edit()
                .putLong(
                        key + "_time",
                        System.currentTimeMillis()
                )
                .putString(
                        key + "_body",
                        json.toString()
                )
                .apply();
    } else {
        /*
         * ဒီ path အတွက် အရင်ကကျန်ခဲ့သော
         * unsafe list cache ရှိလျှင် ဖယ်ရှားမည်။
         */
        cachePreferences
                .edit()
                .remove(key + "_time")
                .remove(key + "_body")
                .apply();
    }

    callback.onSuccess(json);
}


                    @Override
                    public void onError(
                            Exception error
                    ) {
                        /*
                         * Request အချိန်မှာ network ပြတ်သွားရင်
                         * stale cache ရှိသမျှ ဆက်ပြမယ်။
                         */
                        if (!cachedBody.isEmpty()) {
                            try {
                                callback.onSuccess(
                                        new JSONObject(
                                                cachedBody
                                        )
                                );

                                return;
                            } catch (Exception ignored) {
                            }
                        }

                        callback.onError(error);
                    }
                }
        );
    });
}


public static void clearPublicCache() {
    if (cachePreferences != null) {
        cachePreferences
                .edit()
                .clear()
                .apply();
    }
}

private static boolean isPublicCacheablePath(
        String path
) {
    if (
            path == null ||
            path.trim().isEmpty()
    ) {
        return false;
    }

    String normalized =
            path.trim();

    /*
     * titles/{slug} detail response ကို
     * လုံးဝ cache မလုပ်ပါ။
     *
     * အနာဂတ် backend regression တစ်ခုကြောင့်
     * media URL ပြန်ပါလာခဲ့သော်လည်း XML ထဲ
     * မသိမ်းမိစေရန်ဖြစ်သည်။
     */
    if (
            normalized.startsWith(
                    "titles/"
            )
    ) {
        return false;
    }

    /*
     * Category list page-1 ကိုသာ cache လုပ်မယ်။
     */
    if (
            !normalized.startsWith(
                    "titles?"
            )
    ) {
        return false;
    }

    String query =
            normalized.substring(
                    "titles?".length()
            );

    String page = "";
    String search = "";

    for (String part :
            query.split("&")) {

        int separator =
                part.indexOf("=");

        String name =
                separator >= 0
                        ? part.substring(
                                0,
                                separator
                        )
                        : part;

        String value =
                separator >= 0
                        ? part.substring(
                                separator + 1
                        )
                        : "";

        if ("page".equals(name)) {
            page = value;
        } else if ("q".equals(name)) {
            search = value;
        }
    }

    return "1".equals(page) &&
            search.trim().isEmpty();
}
private static boolean hasSensitiveMediaValue(
        JSONObject object
) {
    if (object == null) {
        return false;
    }

    String[] sensitiveKeys = {
            "video_url",
            "download_url",
            "videoUrl",
            "downloadUrl",
            "stream_url",
            "streamUrl",
            "playback_url",
            "playbackUrl"
    };

    for (String key : sensitiveKeys) {
        if (!object.has(key)) {
            continue;
        }

        Object value =
                object.opt(key);

        if (
                value != null &&
                value != JSONObject.NULL &&
                !String.valueOf(value)
                        .trim()
                        .isEmpty()
        ) {
            return true;
        }
    }

    JSONArray names =
            object.names();

    if (names == null) {
        return false;
    }

    for (
            int index = 0;
            index < names.length();
            index++
    ) {
        String key =
                names.optString(
                        index,
                        ""
                );

        Object value =
                object.opt(key);

        if (
                value instanceof JSONObject &&
                hasSensitiveMediaValue(
                        (JSONObject) value
                )
        ) {
            return true;
        }

        if (
                value instanceof JSONArray &&
                hasSensitiveMediaValue(
                        (JSONArray) value
                )
        ) {
            return true;
        }
    }

    return false;
}

private static boolean hasSensitiveMediaValue(
        JSONArray array
) {
    if (array == null) {
        return false;
    }

    for (
            int index = 0;
            index < array.length();
            index++
    ) {
        Object value =
                array.opt(index);

        if (
                value instanceof JSONObject &&
                hasSensitiveMediaValue(
                        (JSONObject) value
                )
        ) {
            return true;
        }

        if (
                value instanceof JSONArray &&
                hasSensitiveMediaValue(
                        (JSONArray) value
                )
        ) {
            return true;
        }
    }

    return false;
}

private static boolean shouldPersistPublicResponse(
        String path,
        JSONObject json
) {
    if (
            path == null ||
            json == null
    ) {
        return false;
    }

    /*
     * Response အတွင်း media URL အစစ်တစ်ခုခုပါလာရင်
     * ဘယ် endpoint ဖြစ်ဖြစ် disk ထဲမသိမ်းပါ။
     */
    if (hasSensitiveMediaValue(json)) {
        return false;
    }

    String normalized =
            path.trim();

    /*
     * Detail response ကို ဘယ်တော့မှမသိမ်းပါ။
     */
    if (
            normalized.startsWith(
                    "titles/"
            )
    ) {
        return false;
    }

    if (
            !normalized.startsWith(
                    "titles?"
            )
    ) {
        return false;
    }

    /*
     * Single-page list ဖြစ်ပြီး sensitive media URL
     * မပါမှသာ cache လုပ်မယ်။
     */
    return !json.optBoolean(
            "hasMore",
            false
    );
}



private static String cacheKey(
        String path
) {
    try {
        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        byte[] bytes =
                digest.digest(
                        path.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder result =
                new StringBuilder();

        for (byte value : bytes) {
            result.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return "api_" + result;
    } catch (Exception error) {
        return "api_" +
                Integer.toHexString(
                        path.hashCode()
                );
    }
}


    public static void post(
            String path,
            JSONObject body,
            Callback callback
    ) {
        request("POST", path, body, callback);
    }

    public static void delete(
            String path,
            Callback callback
    ) {
        request("DELETE", path, null, callback);
    }

    private static void request(
        String method,
        String path,
        JSONObject requestBody,
        Callback callback
) {
    if (
            BuildConfig.CMFLIX_APP_KEY == null ||
            BuildConfig.CMFLIX_APP_KEY
                    .trim()
                    .isEmpty()
    ) {
        callback.onError(
                new IllegalStateException(
                        "App configuration is missing."
                )
        );

        return;
    }

    EXECUTOR.execute(() -> {

            HttpURLConnection connection = null;

            try {
                String apiBaseUrl =
        ConfigManager.requireApiBaseUrl();

URL url =
        new URL(
                apiBaseUrl +
                        normalizePath(path)
        );


                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setUseCaches(false);

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

                String cookie =
                        SessionManager.getCookie();

                if (!cookie.isEmpty()) {
                    connection.setRequestProperty(
                            "Cookie",
                            cookie
                    );
                }

                if (
                        !"GET".equals(method) &&
                        !SessionManager.getCsrf().isEmpty()
                ) {
                    connection.setRequestProperty(
                            "x-csrf-token",
                            SessionManager.getCsrf()
                    );
                }

                if (requestBody != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty(
                            "Content-Type",
                            "application/json; charset=utf-8"
                    );

                    byte[] bytes = requestBody
                            .toString()
                            .getBytes(StandardCharsets.UTF_8);

                    connection.setFixedLengthStreamingMode(
                            bytes.length
                    );

                    try (
                            OutputStream output =
                                    connection.getOutputStream()
                    ) {
                        output.write(bytes);
                    }
                }

                int statusCode =
                        connection.getResponseCode();

                captureSessionCookie(connection);

                InputStream input;

                if (
                        statusCode >= 200 &&
                        statusCode < 300
                ) {
                    input = connection.getInputStream();
                } else {
                    input = connection.getErrorStream();
                }

                String responseBody = readStream(input);

                JSONObject json;

                if (responseBody.trim().isEmpty()) {
                    json = new JSONObject();
                } else {
                    json = new JSONObject(responseBody);
                }

                if (
                        statusCode < 200 ||
                        statusCode >= 300
                ) {
                    if (statusCode == 401) {
                        String error = json.optString(
                                "error",
                                "Login ဝင်ရန်လိုအပ်ပါသည်"
                        );

                        /*
                         * User session expired ဖြစ်မှသာ local session ဖျက်မယ်။
                         * Middleware app-key error ဖြစ်ရင် key ပြဿနာကို
                         * message အတိုင်း ပြပေးမယ်။
                         */
                        if (
                                error.contains("Login") ||
                                error.contains("login")
                        ) {
                            SessionManager.clear();
                        }
                    }

                    throw new RuntimeException(
                            json.optString(
                                    "message",
                                    json.optString(
                                            "error",
                                            "HTTP " + statusCode
                                    )
                            )
                    );
                }

                callback.onSuccess(json);
            } catch (Exception error) {
                callback.onError(error);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static void captureSessionCookie(
            HttpURLConnection connection
    ) {
        String setCookie =
                connection.getHeaderField("Set-Cookie");

        if (setCookie == null || setCookie.isEmpty()) {
            return;
        }

        if (
                setCookie.toLowerCase()
                        .contains("max-age=0")
        ) {
            SessionManager.clear();
            return;
        }

        String firstPart =
                setCookie.split(";", 2)[0].trim();

        if (
                firstPart.startsWith(
                        SESSION_COOKIE_NAME + "="
                )
        ) {
            String value = firstPart.substring(
                    (SESSION_COOKIE_NAME + "=").length()
            );

            if (value.isEmpty()) {
                SessionManager.clear();
            } else {
                SessionManager.saveCookie(firstPart);
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
            StringBuilder body =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            return body.toString();
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

    public static String encode(String value) {
        try {
            return URLEncoder.encode(
                    value,
                    StandardCharsets.UTF_8.name()
            );
        } catch (Exception error) {
            return value;
        }
    }

    public static JSONArray items(JSONObject json) {
        return json.optJSONArray("items");
    }
}
