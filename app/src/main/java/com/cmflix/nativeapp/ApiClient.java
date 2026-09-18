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
        "cmflix_public_api_cache_v2";

private static SharedPreferences cachePreferences;

private static Context applicationContext;


    public static synchronized void initialize(
        Context context
) {
    applicationContext =
            context.getApplicationContext();

    SessionManager.initialize(
            applicationContext
    );

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

    String normalized = path.trim();

    /*
     * Movie detail response ကို cache လုပ်မည်။
     */
    if (
            normalized.startsWith("titles/") &&
            normalized.length() >
                    "titles/".length()
    ) {
        return true;
    }

    if (!normalized.startsWith("titles?")) {
        return false;
    }

    String query =
            normalized.substring(
                    "titles?".length()
            );

    String page = "";
    String search = "";

    for (String part : query.split("&")) {
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

    /*
     * Category ပထမ page နှင့် search မပါသည့်
     * request ကိုသာ cache လုပ်မည်။
     */
    return "1".equals(page) &&
            search.trim().isEmpty();
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

    String normalized =
            path.trim();

    /*
     * titles/{slug} က movie detail ဖြစ်သောကြောင့်
     * သတ်မှတ်ထားသော TTL အတိုင်း cache လုပ်နိုင်သည်။
     */
    if (
            normalized.startsWith("titles/") &&
            normalized.length() >
                    "titles/".length()
    ) {
        return true;
    }

    /*
     * Search၊ favorites နှင့် တခြား responses
     * ကို ဒီ cache ထဲ မထည့်ပါ။
     */
    if (!normalized.startsWith("titles?")) {
        return false;
    }

    /*
     * hasMore=false ဆိုသည်မှာ response တစ်ခုတည်းနဲ့
     * category အကုန်ပါပြီးသားဖြစ်သည်။
     *
     * ဒါကြောင့် cached page 1 + fresh page 2
     * ရောသွားနိုင်သောအခြေအနေ မရှိတော့ပါ။
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
                URL url = new URL(
                        BuildConfig.API_BASE_URL + path
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
