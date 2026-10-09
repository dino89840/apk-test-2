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
            "https://javtiful.com";

    private static final String LIST_PATH =
            "/reducing-mosaic";

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
     * Listing page N — page 1 = /reducing-mosaic,
     * page N = /reducing-mosaic?page=N
     */
    public static void fetchPage(
            int page,
            PageCallback callback
    ) {
        final int safePage = Math.max(1, page);

        String url =
                safePage <= 1
                        ? BASE_URL + LIST_PATH
                        : BASE_URL + LIST_PATH
                                + "?page=" + safePage;

        fetchListingUrl(url, callback);
    }

    /*
     * Search — server-side rendered HTML, SAME
     * <article class="video-card"> structure as listing.
     *
     * URL: /search?videoType=reducing_mosaic&q=...&page=N
     * videoType=reducing_mosaic → mosaic-only results
     * (JAV code နဲ့ရော မင်းသမီးနာမည်နဲ့ရော ရှာလို့ရသည်).
     */
    public static void searchVideos(
            String query,
            int page,
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
                BASE_URL
                        + "/search?videoType=reducing_mosaic"
                        + "&q=" + encoded
                        + (safePage > 1
                                ? "&page=" + safePage
                                : "");

        fetchListingUrl(url, callback);
    }

    /*
     * Actress filmography — /actress/{slug}?videoType=reducing_mosaic
     * (same video-card HTML, mosaic-only, pagination with &page=N).
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

        if (base.contains("?")) {
            url.append("&videoType=reducing_mosaic");
        } else {
            url.append("?videoType=reducing_mosaic");
        }

        if (safePage > 1) {
            url.append("&page=").append(safePage);
        }

        fetchListingUrl(url.toString(), callback);
    }

    /*
     * Shared listing fetcher — listing ရော search
     * ရော ဒီကနေပဲ သွားသည် (parse တူတူပဲ).
     */
    private static void fetchListingUrl(
            final String url,
            final PageCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                String html = get(url);

                List<JavtifulVideo> videos =
                        parseListing(html);

                /*
                 * 23 cards = full page → likely more.
                 * Empty = no more pages.
                 */
                boolean hasMore = !videos.isEmpty();

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

    private static List<JavtifulVideo> parseListing(
            String html
    ) {
        List<JavtifulVideo> videos = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return videos;
        }

        Matcher articleMatcher = ARTICLE.matcher(html);

        while (articleMatcher.find()) {
            String body = articleMatcher.group(1);

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
                    new URL(BASE_URL), trimmed
            ).toString();
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private static String unescapeHtml(String text) {
        return text
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}