package com.cmflix.nativeapp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/*
 * javtiful.com scraper — "Reducing Mosaic" category.
 *
 * - Listing: GET https://javtiful.com/reducing-mosaic?page=N
 *   (page 1 = no ?page param). 23 cards per page.
 * - Detail: GET https://javtiful.com/video/<id>/<slug>
 *   → "src":"https://fast-stream.jav.si/p/..." (direct MP4,
 *   JSON-escaped). Single quality — no 480p/720p variants.
 * - Search: GET /search?videoType=reducing_mosaic&q=...&page=N
 *   (same video-card HTML as listing; mosaic-only).
 *
 * Direct connection (NO proxy for now — user will test first).
 * No cookies/tokens needed. Browser-like User-Agent.
 * Stream URLs are NOT cached — always fresh resolve.
 */
public final class JavtifulClient {

    private static final String TAG = "JavtifulClient";

    public static final String BASE_URL =
            CryptoUtil.dec("3fAfkf0EhROF7nHpAefDpDetIg5eJYpoc7+7YkR+8Ns=");

    private static final String LIST_PATH =
            CryptoUtil.dec("PdihCRihrYmfUzelfU2IEtT4YSV6dihBOk2eJRYPII4=");

    /*
     * Uncensored listing path — encrypted fallback
     * (server-configurable via javtiful.uncensored_listing).
     */
    private static final String UNCENSORED_LIST_PATH =
            CryptoUtil.dec("uSM2UR3EsSY8vqQxPthIzw==");

    /*
     * Chinese AV listing path — encrypted fallback
     * (server-configurable via javtiful.chinese_listing).
     */
    private static final String CHINESE_LIST_PATH =
            CryptoUtil.dec(
                    "GIchTKG3QS4GcxcH8OxsA0BmUwOO8jsWYITNtJzqNuI="
            );

    /*
     * Badge filter types for parseListing.
     *
     * - FILTER_NONE: no badge filter (main listings —
     *   the listing page itself is already type-specific)
     * - FILTER_MOSAIC: "Reducing Mosaic" badge only
     * - FILTER_UNCENSORED: "Uncensored" badge only
     * - FILTER_MOSAIC_UNCENSORED: either badge
     *   (actress filmography — combined)
     *
     * Search result cards carry badges: NO badge =
     * censored, "Reducing Mosaic", "Uncensored".
     * The site IGNORES videoType URL params (JS-only
     * filter), so filtering is client-side.
     */
    public static final int FILTER_NONE = 0;
    public static final int FILTER_MOSAIC = 1;
    public static final int FILTER_UNCENSORED = 2;
    public static final int FILTER_MOSAIC_UNCENSORED = 3;

    /*
     * Base URL resolver — server-configurable။
     *
     * /app-content ၏ javtiful.base (AppContentManager
     * မှတဆင့်) ရှိလျှင် အဲ့ဒါကို သုံးမည်၊ မရှိလျှင် /
     * လွတ်နေလျှင် BASE_URL default သို့ fallback။
     *
     * Domain ပျက်/ပြောင်းလျှင် server (D1) မှာ
     * ပြင်ရုံဖြင့် APK rebuild မလိုတော့ပါ။
     *
     * Trailing slash များကို ဖယ်ပြီး http(s)
     * URL စစ်မှန်ကြောင်း validate လုပ်သည်။
     */
    public static String getBaseUrl() {
        String configured =
                AppContentManager.getCachedJavtifulBaseUrl();

        if (configured != null) {
            configured = configured.trim();

            while (
                    configured.endsWith("/")
                            && configured.length() > 1
            ) {
                configured =
                        configured.substring(
                                0,
                                configured.length() - 1
                        );
            }

            if (
                    configured.regionMatches(
                            true, 0,
                            "https://", 0, 8
                    )
                            ||
                    configured.regionMatches(
                            true, 0,
                            "http://", 0, 7
                    )
            ) {
                return configured;
            }
        }

        return BASE_URL;
    }

    /*
     * Listing path resolver — server-configurable။
     *
     * /app-content ၏ javtiful.listing ရှိလျှင်
     * အဲ့ဒါကို သုံးမည်၊ မရှိလျှင် LIST_PATH default
     * (encrypted LIST_PATH) သို့ fallback။
     *
     * Leading slash မရှိလျှင် ဖြည့်ပေးမည်။
     */
    public static String getListPath() {
        String configured =
                AppContentManager.getCachedJavtifulListingPath();

        if (configured != null) {
            configured = configured.trim();

            if (!configured.isEmpty()) {
                return configured.startsWith("/")
                        ? configured
                        : "/" + configured;
            }
        }

        return LIST_PATH;
    }

    /*
     * Uncensored listing path resolver — server-configurable။
     *
     * /app-content ၏ javtiful.uncensored_listing
     * ရှိလျှင် အဲ့ဒါကို သုံးမည်၊ မရှိလျှင်
     * UNCENSORED_LIST_PATH default (encrypted) သို့ fallback။
     *
     * Leading slash မရှိလျှင် ဖြည့်ပေးမည်။
     */
    public static String getUncensoredListPath() {
        String configured =
                AppContentManager
                        .getCachedJavtifulUncensoredListingPath();

        if (configured != null) {
            configured = configured.trim();

            if (!configured.isEmpty()) {
                return configured.startsWith("/")
                        ? configured
                        : "/" + configured;
            }
        }

        return UNCENSORED_LIST_PATH;
    }

    /*
     * Chinese AV listing path resolver — server-configurable။
     *
     * /app-content ၏ javtiful.chinese_listing
     * ရှိလျှင် အဲ့ဒါကို သုံးမည်၊ မရှိလျှင်
     * CHINESE_LIST_PATH default (encrypted) သို့ fallback။
     *
     * Leading slash မရှိလျှင် ဖြည့်ပေးမည်။
     */
    public static String getChineseListPath() {
        String configured =
                AppContentManager
                        .getCachedJavtifulChineseListingPath();

        if (configured != null) {
            configured = configured.trim();

            if (!configured.isEmpty()) {
                return configured.startsWith("/")
                        ? configured
                        : "/" + configured;
            }
        }

        return CHINESE_LIST_PATH;
    }

    public static final String ID_PREFIX = "javtiful:";

    public static String videoId(String detailUrl) {
        String url =
                detailUrl == null ? "" : detailUrl.trim();

        if (url.startsWith(ID_PREFIX)) {
            return url;
        }

        return ID_PREFIX + url;
    }

    public static String detailUrlFromId(String videoId) {
        String id =
                videoId == null ? "" : videoId.trim();

        if (id.startsWith(ID_PREFIX)) {
            return id.substring(ID_PREFIX.length());
        }

        return id;
    }

    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool();

    private JavtifulClient() {
    }

    public static final class JavtifulVideo {
        public final String title;
        public final String thumbUrl;
        public final String detailUrl;
        public final String duration;

        public JavtifulVideo(
                String title,
                String thumbUrl,
                String detailUrl,
                String duration
        ) {
            this.title = title;
            this.thumbUrl = thumbUrl;
            this.detailUrl = detailUrl;
            this.duration = duration;
        }
    }

    public static final class JavtifulStream {
        public final String url;
        public final String referer;

        public JavtifulStream(String url, String referer) {
            this.url = url;
            this.referer = referer;
        }

        public boolean hasStream() {
            return url != null && !url.isEmpty();
        }
    }

    public static final class JavtifulActress {
        public final String name;
        public final String photoUrl;
        public final String pageUrl;

        public JavtifulActress(
                String name,
                String photoUrl,
                String pageUrl
        ) {
            this.name = name;
            this.photoUrl = photoUrl;
            this.pageUrl = pageUrl;
        }
    }

    public interface PageCallback {
        void onResult(
                List<JavtifulVideo> videos,
                boolean hasMore
        );

        void onError(Exception error);
    }

    public interface StreamCallback {
        void onResult(
                JavtifulStream stream,
                List<JavtifulActress> actresses
        );

        void onError(Exception error);
    }

    /*
     * Query builder — appends ?sort=X and/or ?page=N
     * (verified live: /reducing-mosaic?sort=added_month&page=2
     * returns 23 cards; same for /uncensored and
     * /category/chinese-av).
     */
    private static String buildSortPageQuery(
            String sort,
            int page
    ) {
        StringBuilder q = new StringBuilder();

        if (sort != null && !sort.trim().isEmpty()) {
            q.append("?sort=").append(sort.trim());
        }

        if (page > 1) {
            q.append(
                    q.length() == 0 ? "?page=" : "&page="
            ).append(page);
        }

        return q.toString();
    }

    /*
     * Listing page N — page 1 = /reducing-mosaic,
     * page N = /reducing-mosaic?page=N
     * (reducing mosaic listing).
     * sort = "" (Latest) or added_today/added_week/
     * added_month/popular_today/popular_week/
     * popular_month/most_liked/most_viewed.
     */
    public static void fetchPage(
            int page,
            PageCallback callback
    ) {
        fetchPage(page, "", callback);
    }

    public static void fetchPage(
            int page,
            String sort,
            PageCallback callback
    ) {
        final int safePage = Math.max(1, page);

        String url =
                getBaseUrl()
                        + getListPath()
                        + buildSortPageQuery(sort, safePage);

        fetchListingUrl(url, safePage, FILTER_NONE, callback);
    }

    /*
     * Uncensored listing page N — page 1 = /uncensored,
     * page N = /uncensored?page=N
     */
    public static void fetchUncensoredPage(
            int page,
            PageCallback callback
    ) {
        fetchUncensoredPage(page, "", callback);
    }

    public static void fetchUncensoredPage(
            int page,
            String sort,
            PageCallback callback
    ) {
        final int safePage = Math.max(1, page);

        String url =
                getBaseUrl()
                        + getUncensoredListPath()
                        + buildSortPageQuery(sort, safePage);

        fetchListingUrl(url, safePage, FILTER_NONE, callback);
    }

    /*
     * Chinese AV listing page N — page 1 = /category/chinese-av,
     * page N = /category/chinese-av?page=N
     * (all cards carry the "Uncensored" badge).
     * NOTE: sorted category pages return a single page
     * (no pagination links) — ?sort=X&page=2 yields 0 cards.
     */
    public static void fetchChinesePage(
            int page,
            PageCallback callback
    ) {
        fetchChinesePage(page, "", callback);
    }

    public static void fetchChinesePage(
            int page,
            String sort,
            PageCallback callback
    ) {
        final int safePage = Math.max(1, page);

        String url =
                getBaseUrl()
                        + getChineseListPath()
                        + buildSortPageQuery(sort, safePage);

        fetchListingUrl(url, safePage, FILTER_NONE, callback);
    }

    /*
     * Search (mosaic mode) — server-side rendered HTML, SAME
     * <article class="video-card"> structure as listing.
     *
     * NOTE: javtiful.com IGNORES the videoType URL param
     * (filter is JS-only). Mosaic-only is enforced
     * client-side in parseListing (FILTER_MOSAIC).
     *
     * URL: /search?q=...&page=N
     * (JAV code နဲ့ရော မင်းသမီးနာမည်နဲ့ရော ရှာလို့ရသည်).
     */
    public static void searchVideos(
            String query,
            int page,
            PageCallback callback
    ) {
        searchWithFilter(query, page, FILTER_MOSAIC, callback);
    }

    /*
     * Search (uncensored mode) — same as searchVideos
     * but keeps only "Uncensored" badge cards.
     */
    public static void searchUncensored(
            String query,
            int page,
            PageCallback callback
    ) {
        searchWithFilter(
                query, page, FILTER_UNCENSORED, callback
        );
    }

    /*
     * Search (chinese mode) — same as searchUncensored:
     * Chinese AV cards carry the "Uncensored" badge,
     * so keeps only "Uncensored" badge cards.
     */
    public static void searchChinese(
            String query,
            int page,
            PageCallback callback
    ) {
        searchWithFilter(
                query, page, FILTER_UNCENSORED, callback
        );
    }

    private static void searchWithFilter(
            String query,
            int page,
            int filter,
            PageCallback callback
    ) {
        String q =
                query == null ? "" : query.trim();

        if (q.isEmpty()) {
            callback.onError(
                    new IllegalStateException(
                            "ရှာဖွေမှုစာသား မရှိပါ။"
                    )
            );
            return;
        }

        final int safePage = Math.max(1, page);

        String encoded;

        try {
            encoded = URLEncoder.encode(q, "UTF-8");
        } catch (Exception ignored) {
            encoded = q.replace(" ", "+");
        }

        String url =
                getBaseUrl()
                        + "/search?q=" + encoded
                        + (safePage > 1
                                ? "&page=" + safePage
                                : "");

        fetchListingUrl(url, safePage, filter, callback);
    }

    /*
     * Actress filmography — /actress/{slug}
     * (same video-card HTML).
     *
     * Combined filter: keeps "Reducing Mosaic" AND
     * "Uncensored" badge cards (FILTER_MOSAIC_UNCENSORED),
     * deduped — so tapping an actress from either Jav
     * or Asian shows her videos from both categories.
     */
    public static void getActressVideos(
            String actressUrl,
            int page,
            PageCallback callback
    ) {
        String base =
                actressUrl == null ? "" : actressUrl.trim();

        if (base.isEmpty()) {
            callback.onError(
                    new IllegalStateException(
                            "Actress URL မရှိပါ။"
                    )
            );
            return;
        }

        final int safePage = Math.max(1, page);

        StringBuilder url = new StringBuilder(base);

        if (safePage > 1) {
            if (base.contains("?")) {
                url.append("&page=").append(safePage);
            } else {
                url.append("?page=").append(safePage);
            }
        }

        fetchListingUrl(
                url.toString(),
                safePage,
                FILTER_MOSAIC_UNCENSORED,
                callback
        );
    }

    /*
     * Site pagination summary — e.g.
     * <span class="pagination__summary">Page 1 of 3</span>
     * Returns total pages, or -1 if not found.
     *
     * (javtiful.com REPEATS the last page's content
     * for ?page= beyond the end instead of returning
     * empty — so hasMore must come from this, not
     * from "non-empty response".)
     */
    private static int parseTotalPages(String html) {
        if (html == null) {
            return -1;
        }

        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile(
                            "Page\\s+\\d+\\s+of\\s+(\\d+)",
                            java.util.regex.Pattern
                                    .CASE_INSENSITIVE
                    ).matcher(html);

            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {
        }

        return -1;
    }

    /*
     * Shared listing fetcher — listing ရော search
     * ရော ဒီကနေပဲ သွားသည် (parse တူတူပဲ).
     *
     * filter: FILTER_NONE / FILTER_MOSAIC /
     * FILTER_UNCENSORED / FILTER_MOSAIC_UNCENSORED
     * (see constants above).
     */
    private static void fetchListingUrl(
            final String url,
            final int page,
            final int filter,
            final PageCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                String html = get(url);

                List<JavtifulVideo> videos =
                        parseListing(html, filter);

                /*
                 * hasMore comes from the site's own
                 * "Page X of N" when present — the site
                 * repeats the last page beyond the end,
                 * so a non-empty response does NOT mean
                 * more pages exist. Fallback: non-empty.
                 */
                int totalPages = parseTotalPages(html);

                boolean hasMore =
                        totalPages > 0
                                ? page < totalPages
                                : !videos.isEmpty();

                callback.onResult(videos, hasMore);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    /*
     * Detail page → direct MP4 stream URL + actress list.
     * Never cached (fresh resolve every time).
     */
    public static void resolveStream(
            String detailUrl,
            StreamCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                if (
                        detailUrl == null ||
                                detailUrl.trim().isEmpty()
                ) {
                    throw new IllegalStateException(
                            "Detail URL မရှိပါ။"
                    );
                }

                String html = get(detailUrl.trim());
                String streamUrl = parseDetail(html);

                List<JavtifulActress> actresses =
                        parseActresses(html);

                if (
                        streamUrl == null ||
                                streamUrl.isEmpty()
                ) {
                    throw new IllegalStateException(
                            "Video link ရှာမတွေ့ပါ။"
                    );
                }

                callback.onResult(
                        new JavtifulStream(
                                streamUrl,
                                detailUrl.trim()
                        ),
                        actresses
                );
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private static String get(String url) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(url).openConnection();

            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty(
                    "User-Agent", USER_AGENT
            );
            connection.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,"
                            + "application/xml;q=0.9,*/*;q=0.8"
            );
            connection.setRequestProperty(
                    "Accept-Language", "en-US,en;q=0.9"
            );

            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "Request failed: " + status
                );
            }

            return readStream(connection);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(
            HttpURLConnection connection
    ) throws Exception {
        InputStream input = connection.getInputStream();

        if (input == null) {
            return "";
        }

        String encoding = connection.getContentEncoding();

        if (
                encoding != null &&
                        encoding.equalsIgnoreCase("gzip")
        ) {
            input = new GZIPInputStream(input);
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
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[8192];
            int read;

            while (
                    (read = reader.read(buffer, 0, 8192))
                            != -1
            ) {
                result.append(buffer, 0, read);
            }

            return result.toString();
        }
    }

    // ------------------------------------------------------------------
    // Listing parser
    // ------------------------------------------------------------------

    /*
     * <article class="video-card"> ... </article>
     */
    private static final Pattern ARTICLE =
            Pattern.compile(
                    "<article\\s+class=\"video-card\">(.*?)</article>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    /*
     * <a href="/video/<id>/<slug>" class="video-card__thumbnail">
     */
    private static final Pattern THUMB_LINK =
            Pattern.compile(
                    "<a\\s+href=\"([^\"]+)\"\\s+class=\"video-card__thumbnail\"",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * data-front-lazy-src="..." (fallback: src="...")
     */
    private static final Pattern THUMB_IMG =
            Pattern.compile(
                    "data-front-lazy-src=\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern THUMB_IMG_SRC =
            Pattern.compile(
                    "<img[^>]+src=\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * <a ... class="video-card__title" title="...">
     */
    private static final Pattern TITLE_ATTR =
            Pattern.compile(
                    "class=\"video-card__title\"[^>]*title=\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern TITLE_TEXT =
            Pattern.compile(
                    "class=\"video-card__title\"[^>]*>([^<]+)</a>",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * <span class="video-card__details"><strong>FHD</strong>02:59:35</span>
     */
    private static final Pattern DETAILS =
            Pattern.compile(
                    "class=\"video-card__details\">(?:<strong>[^<]*</strong>)?([^<]+)</span>",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * <span class="video-card__type">Reducing Mosaic</span>
     * <span class="video-card__type">Uncensored</span>
     * — badge filter အတွက် (search + actress).
     * Censored card တွေမှာ ဒီ span မပါပါ။
     */
    private static final Pattern TYPE_SPAN =
            Pattern.compile(
                    "class=\"video-card__type\"[^>]*>([^<]*)</span>",
                    Pattern.CASE_INSENSITIVE
            );

    private static List<JavtifulVideo> parseListing(
            String html,
            int filter
    ) {
        List<JavtifulVideo> videos = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return videos;
        }

        Matcher articleMatcher = ARTICLE.matcher(html);

        while (articleMatcher.find()) {
            String body = articleMatcher.group(1);

            /*
             * Badge filter — type span text:
             * - "Reducing Mosaic" → mosaic
             * - "Uncensored" → uncensored
             * - (no span) → censored (excluded by all filters)
             */
            if (filter != FILTER_NONE) {
                Matcher typeMatcher =
                        TYPE_SPAN.matcher(body);

                String typeText = "";

                if (typeMatcher.find()) {
                    typeText =
                            typeMatcher.group(1).trim();
                }

                boolean isMosaic =
                        "Reducing Mosaic"
                                .equalsIgnoreCase(typeText);
                boolean isUncensored =
                        "Uncensored"
                                .equalsIgnoreCase(typeText);

                boolean keep;

                if (filter == FILTER_MOSAIC) {
                    keep = isMosaic;
                } else if (filter == FILTER_UNCENSORED) {
                    keep = isUncensored;
                } else {
                    // FILTER_MOSAIC_UNCENSORED
                    keep = isMosaic || isUncensored;
                }

                if (!keep) {
                    continue;
                }
            }

            String detailUrl = "";
            Matcher linkMatcher = THUMB_LINK.matcher(body);

            if (linkMatcher.find()) {
                detailUrl =
                        absoluteUrl(
                                linkMatcher.group(1).trim()
                        );
            }

            if (detailUrl.isEmpty()) {
                continue;
            }

            String thumbUrl = "";
            Matcher imgMatcher = THUMB_IMG.matcher(body);

            if (imgMatcher.find()) {
                thumbUrl =
                        absoluteUrl(
                                imgMatcher.group(1).trim()
                        );
            } else {
                Matcher srcMatcher =
                        THUMB_IMG_SRC.matcher(body);

                if (srcMatcher.find()) {
                    String src =
                            srcMatcher.group(1).trim();

                    // placeholder SVG ကို ကျော်မည်
                    if (!src.contains("placeholder")) {
                        thumbUrl = absoluteUrl(src);
                    }
                }
            }

            String title = "";
            Matcher titleMatcher = TITLE_ATTR.matcher(body);

            if (titleMatcher.find()) {
                title = unescapeHtml(
                        titleMatcher.group(1).trim()
                );
            } else {
                Matcher textMatcher =
                        TITLE_TEXT.matcher(body);

                if (textMatcher.find()) {
                    title = unescapeHtml(
                            textMatcher.group(1).trim()
                    );
                }
            }

            if (title.isEmpty()) {
                title = "အမည်မသိ ဗီဒီယို";
            }

            String duration = "";
            Matcher detailsMatcher = DETAILS.matcher(body);

            if (detailsMatcher.find()) {
                duration =
                        detailsMatcher.group(1).trim();
            }

            videos.add(
                    new JavtifulVideo(
                            title, thumbUrl, detailUrl,
                            duration
                    )
            );
        }

        Log.d(TAG, "parseListing: " + videos.size() + " videos");

        return videos;
    }

    // ------------------------------------------------------------------
    // Detail parser — "src":"https://fast-stream.jav.si/p/..."
    // ------------------------------------------------------------------

    private static final Pattern STREAM_SRC =
            Pattern.compile(
                    "\"src\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static String parseDetail(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        /*
         * JSON-escaped slashes (\/) ကို normalize
         * လုပ်မည်။
         */
        String normalized = html.replace("\\/", "/");

        Matcher matcher = STREAM_SRC.matcher(normalized);

        while (matcher.find()) {
            String src = matcher.group(1).trim();

            // fast-stream MP4 ကိုသာ ယူမည်
            if (src.contains("fast-stream.jav.si")) {
                Log.d(TAG, "parseDetail: stream found");
                return src;
            }
        }

        // fallback: တွေ့သမျှ ပထမဆုံး src
        Matcher fallback = STREAM_SRC.matcher(normalized);

        if (fallback.find()) {
            return fallback.group(1).trim();
        }

        return "";
    }

    // ------------------------------------------------------------------
    // Actress parser —
    // <a href="/actress/{slug}" class="watch-actor-card">
    //   <img src="..." alt="{name}">
    //   <span>{name}</span>
    // </a>
    // ------------------------------------------------------------------

    private static final Pattern ACTRESS_CARD =
            Pattern.compile(
                    "<a\\s+href=\"(/actress/[^\"]+)\"\\s+"
                            + "class=\"watch-actor-card\">(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern ACTRESS_IMG =
            Pattern.compile(
                    "<img[^>]+src=\"([^\"]+)\"[^>]*alt=\"([^\"]*)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern ACTRESS_IMG_ALT_FIRST =
            Pattern.compile(
                    "<img[^>]+alt=\"([^\"]*)\"[^>]*src=\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern ACTRESS_NAME_SPAN =
            Pattern.compile(
                    "<span>([^<]+)</span>",
                    Pattern.CASE_INSENSITIVE
            );

    private static List<JavtifulActress> parseActresses(
            String html
    ) {
        List<JavtifulActress> actresses = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return actresses;
        }

        Matcher cardMatcher = ACTRESS_CARD.matcher(html);

        while (cardMatcher.find()) {
            String pageUrl =
                    absoluteUrl(
                            cardMatcher.group(1).trim()
                    );

            String inner = cardMatcher.group(2);

            String photoUrl = "";
            String name = "";

            Matcher imgMatcher = ACTRESS_IMG.matcher(inner);

            if (imgMatcher.find()) {
                photoUrl =
                        absoluteUrl(
                                imgMatcher.group(1).trim()
                        );
                name = unescapeHtml(
                        imgMatcher.group(2).trim()
                );
            } else {
                Matcher altFirstMatcher =
                        ACTRESS_IMG_ALT_FIRST.matcher(inner);

                if (altFirstMatcher.find()) {
                    name = unescapeHtml(
                            altFirstMatcher.group(1)
                                    .trim()
                    );
                    photoUrl =
                            absoluteUrl(
                                    altFirstMatcher
                                            .group(2).trim()
                            );
                }
            }

            if (name.isEmpty()) {
                Matcher spanMatcher =
                        ACTRESS_NAME_SPAN.matcher(inner);

                if (spanMatcher.find()) {
                    name = unescapeHtml(
                            spanMatcher.group(1).trim()
                    );
                }
            }

            if (!pageUrl.isEmpty() && !name.isEmpty()) {
                actresses.add(
                        new JavtifulActress(
                                name, photoUrl, pageUrl
                        )
                );
            }
        }

        Log.d(TAG, "parseActresses: " + actresses.size());

        return actresses;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String absoluteUrl(String url) {
        if (url == null) {
            return "";
        }

        String trimmed = url.trim();

        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }

        if (
                trimmed.toLowerCase(Locale.US)
                        .startsWith("http")
        ) {
            return trimmed;
        }

        try {
            return new URL(
                    new URL(getBaseUrl()), trimmed
            ).toString();
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    /*
     * Numeric character references: &#039; &#39; &#x27; ...
     * (javtiful emits &#039; with a leading zero, which the
     * old fixed list missed).
     */
    private static final Pattern NUMERIC_ENTITY =
            Pattern.compile("&#(\\d+);|&#x([0-9a-fA-F]+);");

    private static String unescapeHtml(String text) {
        if (text == null) {
            return "";
        }

        Matcher numeric = NUMERIC_ENTITY.matcher(text);
        StringBuffer decoded = new StringBuffer();

        while (numeric.find()) {
            int codePoint;

            try {
                if (numeric.group(1) != null) {
                    codePoint = Integer.parseInt(numeric.group(1));
                } else {
                    codePoint = Integer.parseInt(numeric.group(2), 16);
                }
            } catch (NumberFormatException invalid) {
                continue;
            }

            numeric.appendReplacement(
                    decoded,
                    Matcher.quoteReplacement(
                            new String(Character.toChars(codePoint))
                    )
            );
        }

        numeric.appendTail(decoded);

        return decoded.toString()
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}