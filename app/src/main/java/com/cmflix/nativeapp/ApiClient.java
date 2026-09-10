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

    public static void initialize(Context context) {
        SessionManager.initialize(context);
    }

    public static void get(
            String path,
            Callback callback
    ) {
        request("GET", path, null, callback);
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
