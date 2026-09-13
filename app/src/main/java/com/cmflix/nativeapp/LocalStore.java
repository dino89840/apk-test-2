package com.cmflix.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LocalStore {

    private static final String PREFS =
            "cmflix_local_features_v1";

    private static final String KEY_HISTORY =
            "view_history";

    private static final String KEY_DOWNLOADS =
            "download_history";

    private static final String KEY_SEARCHES =
            "search_history";

    private static final String KEY_RESIZE_MODE =
            "resize_mode";

    private static final String KEY_AMOLED =
            "amoled_theme";

    private static final int MAX_RECENT = 20;
    private static final int MAX_DOWNLOADS = 50;
    private static final int MAX_SEARCHES = 10;

    private static SharedPreferences preferences;

    private LocalStore() {
    }

    public static synchronized void initialize(
            Context context
    ) {
        if (preferences != null) {
            return;
        }

        preferences =
                context.getApplicationContext()
                        .getSharedPreferences(
                                PREFS,
                                Context.MODE_PRIVATE
                        );
    }

    private static SharedPreferences prefs() {
        if (preferences == null) {
            throw new IllegalStateException(
                    "LocalStore.initialize() was not called."
            );
        }

        return preferences;
    }

    private static JSONArray readArray(String key) {
        try {
            return new JSONArray(
                    prefs().getString(key, "[]")
            );
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void saveArray(
            String key,
            JSONArray array
    ) {
        prefs()
                .edit()
                .putString(key, array.toString())
                .apply();
    }

    private static String itemId(JSONObject item) {
        if (item == null) {
            return "";
        }

        String id = item.optString("id", "").trim();

        if (!id.isEmpty()) {
            return id;
        }

        return item.optString("slug", "").trim();
    }

    private static JSONObject copyTitle(
            JSONObject source
    ) {
        JSONObject result = new JSONObject();

        if (source == null) {
            return result;
        }

        copy(source, result, "id");
        copy(source, result, "slug");
        copy(source, result, "title");
        copy(source, result, "year");
        copy(source, result, "rating");
        copy(source, result, "category");
        copy(source, result, "poster_url");
        copy(source, result, "backdrop_url");

        return result;
    }

    private static void copy(
            JSONObject source,
            JSONObject target,
            String key
    ) {
        if (!source.has(key)) {
            return;
        }

        try {
            target.put(key, source.opt(key));
        } catch (Exception ignored) {
        }
    }

    private static JSONObject findById(
            JSONArray array,
            String id
    ) {
        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject item =
                    array.optJSONObject(index);

            if (
                    item != null &&
                    id.equals(itemId(item))
            ) {
                return item;
            }
        }

        return null;
    }

    private static JSONArray withoutId(
            JSONArray array,
            String id
    ) {
        JSONArray result = new JSONArray();

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject item =
                    array.optJSONObject(index);

            if (
                    item != null &&
                    !id.equals(itemId(item))
            ) {
                result.put(item);
            }
        }

        return result;
    }

    private static JSONArray putFirst(
            JSONArray source,
            JSONObject item,
            int limit
    ) {
        JSONArray result = new JSONArray();
        result.put(item);

        String id = itemId(item);

        for (int index = 0;
             index < source.length() &&
                     result.length() < limit;
             index++) {

            JSONObject current =
                    source.optJSONObject(index);

            if (
                    current != null &&
                    !id.equals(itemId(current))
            ) {
                result.put(current);
            }
        }

        return result;
    }

    public static synchronized void rememberRecentlyViewed(
            JSONObject title
    ) {
        String id = itemId(title);

        if (id.isEmpty()) {
            return;
        }

        JSONArray history =
                readArray(KEY_HISTORY);

        JSONObject previous =
                findById(history, id);

        JSONObject record =
                copyTitle(title);

        try {
            record.put(
                    "_viewed_at",
                    System.currentTimeMillis()
            );

            record.put(
                    "_local_kind",
                    "recent"
            );

            if (previous != null) {
                record.put(
                        "_position",
                        previous.optLong(
                                "_position",
                                0L
                        )
                );

                record.put(
                        "_duration",
                        previous.optLong(
                                "_duration",
                                0L
                        )
                );

                record.put(
                        "_watched_at",
                        previous.optLong(
                                "_watched_at",
                                0L
                        )
                );
            }
        } catch (Exception ignored) {
        }

        saveArray(
                KEY_HISTORY,
                putFirst(
                        history,
                        record,
                        MAX_RECENT
                )
        );
    }

    public static synchronized void saveProgress(
            String titleId,
            long position,
            long duration
    ) {
        if (
                titleId == null ||
                titleId.trim().isEmpty()
        ) {
            return;
        }

        String id = titleId.trim();

        JSONArray history =
                readArray(KEY_HISTORY);

        JSONObject existing =
                findById(history, id);

        JSONObject record =
                existing == null
                        ? new JSONObject()
                        : existing;

        try {
            if (existing == null) {
                record.put("id", id);
                record.put("title", "Recently watched");
                record.put(
                        "_viewed_at",
                        System.currentTimeMillis()
                );
            }

            boolean completed =
                    duration > 0L &&
                    (
                            position >= duration * 0.95d ||
                            duration - position <= 30_000L
                    );

            if (completed) {
                record.put("_position", 0L);
                record.put("_duration", duration);
            } else {
                record.put(
                        "_position",
                        Math.max(0L, position)
                );

                record.put(
                        "_duration",
                        Math.max(0L, duration)
                );
            }

            record.put(
                    "_watched_at",
                    System.currentTimeMillis()
            );

            record.put(
                    "_local_kind",
                    "recent"
            );
        } catch (Exception ignored) {
        }

        saveArray(
                KEY_HISTORY,
                putFirst(
                        history,
                        record,
                        MAX_RECENT
                )
        );
    }

    public static synchronized long[] getProgress(
            String titleId
    ) {
        if (
                titleId == null ||
                titleId.trim().isEmpty()
        ) {
            return new long[]{0L, 0L};
        }

        JSONObject item =
                findById(
                        readArray(KEY_HISTORY),
                        titleId.trim()
                );

        if (item == null) {
            return new long[]{0L, 0L};
        }

        return new long[]{
                item.optLong("_position", 0L),
                item.optLong("_duration", 0L)
        };
    }

    public static long getResumePosition(
            String titleId
    ) {
        long[] progress =
                getProgress(titleId);

        long position = progress[0];
        long duration = progress[1];

        if (position < 10_000L) {
            return 0L;
        }

        if (
                duration > 0L &&
                (
                        position >= duration * 0.95d ||
                        duration - position <= 30_000L
                )
        ) {
            return 0L;
        }

        return position;
    }

    public static synchronized List<JSONObject>
    getRecentlyViewed() {
        JSONArray source =
                readArray(KEY_HISTORY);

        List<JSONObject> result =
                new ArrayList<>();

        for (int index = 0;
             index < source.length() &&
                     result.size() < MAX_RECENT;
             index++) {

            JSONObject item =
                    source.optJSONObject(index);

            if (item == null) {
                continue;
            }

            try {
                item.put(
                        "_local_kind",
                        "recent"
                );
            } catch (Exception ignored) {
            }

            result.add(item);
        }

        return result;
    }

    public static synchronized List<JSONObject>
    getContinueWatching() {
        JSONArray source =
                readArray(KEY_HISTORY);

        List<JSONObject> result =
                new ArrayList<>();

        for (int index = 0;
             index < source.length();
             index++) {

            JSONObject item =
                    source.optJSONObject(index);

            if (item == null) {
                continue;
            }

            long position =
                    item.optLong(
                            "_position",
                            0L
                    );

            long duration =
                    item.optLong(
                            "_duration",
                            0L
                    );

            boolean valid =
                    position >= 10_000L &&
                    (
                            duration <= 0L ||
                            (
                                    position < duration * 0.95d &&
                                    duration - position > 30_000L
                            )
                    );

            if (!valid) {
                continue;
            }

            try {
                item.put(
                        "_local_kind",
                        "continue"
                );
            } catch (Exception ignored) {
            }

            result.add(item);
        }

        return result;
    }

    public static synchronized void clearContinueWatching() {
        JSONArray source =
                readArray(KEY_HISTORY);

        for (int index = 0;
             index < source.length();
             index++) {

            JSONObject item =
                    source.optJSONObject(index);

            if (item == null) {
                continue;
            }

            try {
                item.put("_position", 0L);
                item.put("_duration", 0L);
            } catch (Exception ignored) {
            }
        }

        saveArray(KEY_HISTORY, source);
    }

    public static synchronized void clearRecentlyViewed() {
        saveArray(
                KEY_HISTORY,
                new JSONArray()
        );
    }

    public static synchronized void recordDownload(
            String titleId
    ) {
        if (
                titleId == null ||
                titleId.trim().isEmpty()
        ) {
            return;
        }

        String id = titleId.trim();

        JSONObject recent =
                findById(
                        readArray(KEY_HISTORY),
                        id
                );

        JSONObject record =
                recent == null
                        ? new JSONObject()
                        : copyTitle(recent);

        try {
            if (recent == null) {
                record.put("id", id);
                record.put("title", "Downloaded title");
            }

            record.put(
                    "_downloaded_at",
                    System.currentTimeMillis()
            );

            record.put(
                    "_local_kind",
                    "download"
            );
        } catch (Exception ignored) {
        }

        JSONArray downloads =
                readArray(KEY_DOWNLOADS);

        saveArray(
                KEY_DOWNLOADS,
                putFirst(
                        downloads,
                        record,
                        MAX_DOWNLOADS
                )
        );
    }

    public static synchronized List<JSONObject>
    getDownloadHistory() {
        JSONArray source =
                readArray(KEY_DOWNLOADS);

        List<JSONObject> result =
                new ArrayList<>();

        for (int index = 0;
             index < source.length();
             index++) {

            JSONObject item =
                    source.optJSONObject(index);

            if (item == null) {
                continue;
            }

            try {
                item.put(
                        "_local_kind",
                        "download"
                );
            } catch (Exception ignored) {
            }

            result.add(item);
        }

        return result;
    }

    public static synchronized void clearDownloadHistory() {
        saveArray(
                KEY_DOWNLOADS,
                new JSONArray()
        );
    }

    public static synchronized void addSearch(
            String query
    ) {
        if (query == null) {
            return;
        }

        String value = query.trim();

        if (value.isEmpty()) {
            return;
        }

        JSONArray old =
                readArray(KEY_SEARCHES);

        JSONArray next =
                new JSONArray();

        next.put(value);

        for (int index = 0;
             index < old.length() &&
                     next.length() < MAX_SEARCHES;
             index++) {

            String current =
                    old.optString(index, "").trim();

            if (
                    !current.isEmpty() &&
                    !current.equalsIgnoreCase(value)
            ) {
                next.put(current);
            }
        }

        saveArray(KEY_SEARCHES, next);
    }

    public static synchronized List<String>
    getSearchHistory() {
        JSONArray source =
                readArray(KEY_SEARCHES);

        List<String> result =
                new ArrayList<>();

        for (int index = 0;
             index < source.length();
             index++) {

            String value =
                    source.optString(index, "").trim();

            if (!value.isEmpty()) {
                result.add(value);
            }
        }

        return result;
    }

    public static synchronized void clearSearchHistory() {
        saveArray(
                KEY_SEARCHES,
                new JSONArray()
        );
    }

    public static int getResizeMode() {
        return prefs().getInt(
                KEY_RESIZE_MODE,
                0
        );
    }

    public static void saveResizeMode(int value) {
        prefs()
                .edit()
                .putInt(KEY_RESIZE_MODE, value)
                .apply();
    }

    public static boolean isAmoledTheme() {
        return prefs().getBoolean(
                KEY_AMOLED,
                false
        );
    }

    public static boolean toggleAmoledTheme() {
        boolean value = !isAmoledTheme();

        prefs()
                .edit()
                .putBoolean(KEY_AMOLED, value)
                .apply();

        return value;
    }
}
