package com.cmflix.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {
    private static final String BASE = "https://kkflix.xubi.org/api/";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);

    public interface Callback {
        void onSuccess(JSONObject json);
        void onError(Exception error);
    }

    private ApiClient() {}

    public static void get(String path, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/json");

                int code = connection.getResponseCode();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                code >= 200 && code < 300
                                        ? connection.getInputStream()
                                        : connection.getErrorStream(),
                                StandardCharsets.UTF_8
                        )
                );

                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(body.toString());

                if (code < 200 || code >= 300) {
                    throw new RuntimeException(
                            json.optString("error", "HTTP " + code)
                    );
                }

                callback.onSuccess(json);
            } catch (Exception e) {
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    public static JSONArray items(JSONObject json) {
        return json.optJSONArray("items");
    }
}
