package com.cmflix.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final String KEY_GRID_SPAN =
            "grid_span";

    /*
     * မြန်မာ (samusar) video များ၏ watch progress။
     * Backend title ID မရှိသောကြောင့် detail URL ကို
     * key အဖြစ် သုံးသည် ("samusar:" prefix ဖြင့်)။
     * KEY_HISTORY နှင့် ရောမထည့်ပါ — MainActivity ၏
     * Continue Watching / Recently Viewed တွင်
     * slug မရှိသော item များ ပေါ်လာခြင်းကို
     * ရှောင်ရန် သီးသန့် key ထားသည်။
     */
    private static final String KEY_SAMUSAR_PROGRESS =
            "samusar_progress";

    private static final int MAX_SAMUSAR = 20;

    

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

    /*
 * History နဲ့ download data များကို account တစ်ခုချင်းစီ
 * အလိုက်ခွဲမည်။
 *
 * Search history က account-sensitive မဟုတ်သောကြောင့်
 * device-level key အတိုင်းဆက်သုံးမည်။
 */
private static boolean isAccountScopedKey(
        String key
) {
    return KEY_HISTORY.equals(key) ||
            KEY_DOWNLOADS.equals(key) ||
            KEY_SAMUSAR_PROGRESS.equals(key);
}

/*
 * ဥပမာ:
 *
 * view_history::user-uuid
 * download_history::user-uuid
 *
 * Login မရှိလျှင် guest namespace သုံးမည်။
 */
private static String storageKey(
        String key
) {
    if (!isAccountScopedKey(key)) {
        return key;
    }

    String userId =
            SessionManager.getUserId();

    String owner =
            userId == null ||
            userId.trim().isEmpty()
                    ? "guest"
                    : userId.trim();

    String scopedKey =
            key + "::" + owner;

    /*
     * App version အဟောင်းက unscoped key ဖြင့်သိမ်းထားသော
     * history ရှိနိုင်သည်။
     *
     * Upgrade လုပ်ချိန်မှာ လက်ရှိ login ဝင်ထားသော account
     * namespace ထဲသို့ တစ်ကြိမ်သာ migrate လုပ်မည်။
     */
    if (
            !prefs().contains(scopedKey) &&
            prefs().contains(key)
    ) {
        String legacyValue =
                prefs().getString(
                        key,
                        "[]"
                );

        prefs()
                .edit()
                .putString(
                        scopedKey,
                        legacyValue == null
                                ? "[]"
                                : legacyValue
                )
                .remove(key)
                .apply();
    }

    return scopedKey;
}

private static JSONArray readArray(
        String key
) {
    try {
        return new JSONArray(
                prefs().getString(
                        storageKey(key),
                        "[]"
                )
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
            .putString(
                    storageKey(key),
                    array == null
                            ? "[]"
                            : array.toString()
            )
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
public static synchronized Map<String, long[]>
getProgressSnapshot() {
    JSONArray history =
            readArray(KEY_HISTORY);

    Map<String, long[]> result =
            new HashMap<>();

    for (
            int index = 0;
            index < history.length();
            index++
    ) {
        JSONObject item =
                history.optJSONObject(index);

        if (item == null) {
            continue;
        }

        String id = itemId(item);

        if (id.isEmpty()) {
            continue;
        }

        result.put(
                id,
                new long[]{
                        item.optLong(
                                "_position",
                                0L
                        ),
                        item.optLong(
                                "_duration",
                                0L
                        )
                }
        );
    }

    return result;
}

    public static long getResumePosition(
            String titleId
    ) {
        /*
         * Samusar video ဖြစ်လျှင် သီးသန့်
         * progress store မှ ဖတ်မည်။
         */
        if (isSamusarId(titleId)) {
            return getSamusarResumePosition(
                    titleId
            );
        }

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
public static synchronized void clearResumePosition(
        String titleId
) {
    if (
            titleId == null ||
            titleId.trim().isEmpty()
    ) {
        return;
    }

    /*
     * Samusar video ဖြစ်လျှင် သီးသန့်
     * progress store ကို ရှင်းမည်။
     */
    if (isSamusarId(titleId)) {
        clearSamusarProgress(titleId);
        return;
    }

    String id = titleId.trim();

    JSONArray history =
            readArray(KEY_HISTORY);

    JSONObject item =
            findById(history, id);

    if (item == null) {
        return;
    }

    try {
        item.put("_position", 0L);
    } catch (Exception ignored) {
    }

    saveArray(
            KEY_HISTORY,
            history
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

    /*
     * Grid column ရွေးချယ်မှု (device-level)။
     *
     * 0 = auto (screen size အလိုက်)
     * 2 / 3 = user ရွေးထားသော column အရေအတွက်
     */
    public static int getGridSpan() {
        return prefs().getInt(
                KEY_GRID_SPAN,
                0
        );
    }

    public static void saveGridSpan(int value) {
        prefs()
                .edit()
                .putInt(KEY_GRID_SPAN, value)
                .apply();
    }

    // ------------------------------------------------------------------
    // Samusar (မြန်မာ) watch progress — resume / continue watching
    // ------------------------------------------------------------------

    /*
     * Samusar video resume entry။
     */
    public static final class SamusarProgress {
        public final String id;
        public final String title;
        public final String thumbUrl;
        public final long position;
        public final long duration;

        public SamusarProgress(
                String id,
                String title,
                String thumbUrl,
                long position,
                long duration
        ) {
            this.id = id;
            this.title = title;
            this.thumbUrl = thumbUrl;
            this.position = position;
            this.duration = duration;
        }
    }

    private static boolean isSamusarId(String id) {
        return id != null &&
                (
                        id.startsWith(
                                SamusarClient.ID_PREFIX
                        )
                                ||
                                id.startsWith(
                                        MmtubeClient.ID_PREFIX
                                )
                                ||
                                id.startsWith(
                                        MmlovetvClient.ID_PREFIX
                                )
                );
    }

    public static synchronized void saveSamusarProgress(
            String videoId,
            String title,
            String thumbUrl,
            long position,
            long duration
    ) {
        if (
                videoId == null ||
                        videoId.trim().isEmpty()
        ) {
            return;
        }

        String id = videoId.trim();

        JSONArray store =
                readArray(KEY_SAMUSAR_PROGRESS);

        JSONObject existing =
                findById(store, id);

        JSONObject record =
                existing == null
                        ? new JSONObject()
                        : existing;

        try {
            if (existing == null) {
                record.put("id", id);
            }

            record.put(
                    "title",
                    title == null || title.trim().isEmpty()
                            ? "အမည်မသိ ဗီဒီယို"
                            : title.trim()
            );

            record.put(
                    "thumb_url",
                    thumbUrl == null
                            ? ""
                            : thumbUrl.trim()
            );

            /*
             * ပြီးဆုံးသွားလျှင် (95%+ ကြည့်ပြီး)
             * resume မလုပ်တော့ဘဲ position ကို ရှင်းမည် —
             * saveProgress() ၏ logic အတိုင်း။
             */
            boolean completed =
                    duration > 0L &&
                            (
                                    position >=
                                            duration * 0.95d ||
                                            duration - position <=
                                                    30_000L
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
        } catch (Exception ignored) {
        }

        saveArray(
                KEY_SAMUSAR_PROGRESS,
                putFirst(
                        store,
                        record,
                        MAX_SAMUSAR
                )
        );
    }

    public static synchronized long[] getSamusarProgress(
            String videoId
    ) {
        if (
                videoId == null ||
                        videoId.trim().isEmpty()
        ) {
            return new long[]{0L, 0L};
        }

        JSONObject item =
                findById(
                        readArray(KEY_SAMUSAR_PROGRESS),
                        videoId.trim()
                );

        if (item == null) {
            return new long[]{0L, 0L};
        }

        return new long[]{
                item.optLong("_position", 0L),
                item.optLong("_duration", 0L)
        };
    }

    /*
     * getResumePosition() ၏ validity rules အတိုင်း —
     * 10s+ ကြည့်ထားပြီး အဆုံးမရောက်သေးမှ resume ။
     */
    public static long getSamusarResumePosition(
            String videoId
    ) {
        long[] progress =
                getSamusarProgress(videoId);

        long position = progress[0];
        long duration = progress[1];

        if (position < 10_000L) {
            return 0L;
        }

        if (
                duration > 0L &&
                        (
                                position >= duration * 0.95d ||
                                        duration - position <=
                                                30_000L
                        )
        ) {
            return 0L;
        }

        return position;
    }

    /*
     * MyanmarActivity ၏ "ဆက်လက်ကြည့်ရှုရန်"
     * section အတွက်။
     */
    public static synchronized List<SamusarProgress>
    getSamusarContinueWatching() {
        JSONArray source =
                readArray(KEY_SAMUSAR_PROGRESS);

        List<SamusarProgress> result =
                new ArrayList<>();

        for (
                int index = 0;
                index < source.length();
                index++
        ) {
            /*
             * Storage မှာ MAX_SAMUSAR (20) cap
             * ရှိပြီးသား — ဒါက defensive cap
             * (ဗားရှင်းအဟောင်း data အတွက်)။
             */
            if (result.size() >= MAX_SAMUSAR) {
                break;
            }

            JSONObject item =
                    source.optJSONObject(index);

            if (item == null) {
                continue;
            }

            long position =
                    item.optLong("_position", 0L);

            long duration =
                    item.optLong("_duration", 0L);

            boolean valid =
                    position >= 10_000L &&
                            (
                                    duration <= 0L ||
                                            (
                                                    position <
                                                            duration *
                                                                    0.95d &&
                                                            duration -
                                                                    position >
                                                                    30_000L
                                            )
                            );

            if (!valid) {
                continue;
            }

            result.add(
                    new SamusarProgress(
                            item.optString("id", ""),
                            item.optString(
                                    "title",
                                    "အမည်မသိ ဗီဒီယို"
                            ),
                            item.optString(
                                    "thumb_url",
                                    ""
                            ),
                            position,
                            duration
                    )
            );
        }

        return result;
    }

    public static synchronized void clearSamusarProgress(
            String videoId
    ) {
        if (
                videoId == null ||
                        videoId.trim().isEmpty()
        ) {
            return;
        }

        String id = videoId.trim();

        JSONArray store =
                readArray(KEY_SAMUSAR_PROGRESS);

        JSONObject item =
                findById(store, id);

        if (item == null) {
            return;
        }

        try {
            item.put("_position", 0L);
        } catch (Exception ignored) {
        }

        saveArray(
                KEY_SAMUSAR_PROGRESS,
                store
        );
    }

    /*
     * Myanmar \"ဆက်လက်ကြည့်ရှုရန်\" row ကို
     * အကုန်ရှင်းရန် — CLEAR button အတွက်။
     */
    public static synchronized void clearAllSamusarProgress() {
        saveArray(
                KEY_SAMUSAR_PROGRESS,
                new JSONArray()
        );
    }

}