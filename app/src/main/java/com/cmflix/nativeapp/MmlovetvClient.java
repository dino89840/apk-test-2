package com.cmflix.nativeapp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/*
 * mmlovetv.com scraper — "Myanmar + All 3" source
 * (direct, no proxy).
 *
 * WordPress site (cfr2ss secure-streaming plugin):
 * - Listing: GET https://mmlovetv.com/
 *   page N = https://mmlovetv.com/page/N/
 *   (cards = li.wp-block-post.video,
 *   detail URL in a.cfr2ss-video-thumbnail-link href,
 *   title in img alt / h2.wp-block-post-title,
 *   thumb in <img src>, duration in
 *   span.cfr2ss-duration-badge).
 * - Detail: GET https://mmlovetv.com/video/<slug>/
 *   → <video data-attachment-id="{id}">
 *     <source src=".../wp-json/cfr2ss/v1/stream/{id}
 *       ?cfr2ss_exp=...&cfr2ss_sig=..." type="video/mp4">
 *   The presigned URL may be short-lived; a fresh one
 *   can be fetched from
 *   GET /wp-json/cfr2ss/v1/get-video-url/{id}?_=ts
 *   (X-Requested-With: XMLHttpRequest,
 *   credentials/cookies required) which returns
 *   {"success":true,"url":"..."}.
 *
 * Video format: MP4 (data-mime-type="video/mp4") —
 * NOT m3u8.
 *
 * Direct connection (NO proxy). Stream URLs are NOT
 * cached — always fresh resolve. Session cookies
 * (HttpOnly worker cookie) are forwarded to the
 * player/download along with the detail-page Referer.
 *
 * NOTE: CookieManager ကို global default အဖြစ်
 * မသတ်မှတ်ပါ — ApiClient ၏ request များကို
 * လုံးဝ မထိခိုက်စေရန် manual cookie jar သုံးသည်။
 */
public final class MmlovetvClient {

    private static final String TAG = "MmlovetvClient";

    /*
     * Direct site URL (plaintext — the user provided
     * this URL openly; build-time encryption key is
     * unavailable outside CI).
     */
    public static final String BASE_URL =
            "https://mmlovetv.com";

    public static final String IMG_HOST =
            "img.mmlovetv.com";

    /*
     * Mmlovetv video များ၏ stable ID prefix။
     * LocalStore resume key အဖြစ်
     * "mmlovetv:" + detailUrl ကို သုံးသည်။
     */
    public static final String ID_PREFIX = "mmlovetv:";

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

    /*
     * Mmlovetv-only in-memory cookie jar (Cloudflare
     * worker / session cookies for the presigned
     * stream URL).
     */
    private static final List<HttpCookie> COOKIE_JAR =
            new ArrayList<>();

    /*
     * Listing page cache (5 မိနစ်) — request
     * အရေအတွက် လျှော့ချရန်။ Detail page /
     * stream URL များကို cache လုံးဝ မလုပ်ပါ။
     */
    private static final long LISTING_CACHE_TTL_MS =
            5 * 60 * 1000L;

    private static final int LISTING_CACHE_MAX_PAGES =
            20;

    private static final class CachedListing {
        final long fetchedAt;
        final List<MmlovetvVideo> videos;
        final boolean hasMore;

        CachedListing(
                long fetchedAt,
                List<MmlovetvVideo> videos,
                boolean hasMore
        ) {
            this.fetchedAt = fetchedAt;
            this.videos = videos;
            this.hasMore = hasMore;
        }
    }

    private static final Map<String, CachedListing>
            LISTING_CACHE = new LinkedHashMap<>();

    private static synchronized CachedListing getCachedListing(
            String url
    ) {
        CachedListing cached = LISTING_CACHE.get(url);

        if (cached == null) {
            return null;
        }

        if (
                System.currentTimeMillis() - cached.fetchedAt
                        > LISTING_CACHE_TTL_MS
        ) {
            LISTING_CACHE.remove(url);

            return null;
        }

        return cached;
    }

    private static synchronized void putCachedListing(
            String url,
            List<MmlovetvVideo> videos,
            boolean hasMore
    ) {
        LISTING_CACHE.put(
                url,
                new CachedListing(
                        System.currentTimeMillis(),
                        new ArrayList<>(videos),
                        hasMore
                )
        );

        while (
                LISTING_CACHE.size() > LISTING_CACHE_MAX_PAGES
        ) {
            String oldest =
                    LISTING_CACHE.keySet().iterator().next();
            LISTING_CACHE.remove(oldest);
        }
    }

    public static synchronized void clearListingCache() {
        LISTING_CACHE.clear();
    }

    private MmlovetvClient() {
    }

    public static final class MmlovetvVideo {
        public final String title;
        public final String thumbUrl;
        public final String detailUrl;
        public final String duration;

        public MmlovetvVideo(
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

    public static final class MmlovetvStream {
        public final String url;

        /*
         * Detail page request မှ ရသော session cookies
         * ("Cookie" header အဖြစ် ပြန်ပို့ရန်)။
         */
        public final String cookieHeader;

        /*
         * Video detail page URL — "Referer" header အဖြစ်
         * ပြန်ပို့ရန်။
         */
        public final String referer;

        public MmlovetvStream(
                String url,
                String cookieHeader,
                String referer
        ) {
            this.url = url;
            this.cookieHeader =
                    cookieHeader == null ? "" : cookieHeader;
            this.referer =
                    referer == null ? "" : referer;
        }

        public String bestUrl() {
            return url == null ? "" : url;
        }

        public boolean hasStream() {
            return !bestUrl().isEmpty();
        }
    }

    public interface PageCallback {
        void onResult(
                List<MmlovetvVideo> videos,
                boolean hasMore
        );

        void onError(Exception error);
    }

    public interface StreamCallback {
        void onResult(MmlovetvStream stream);

        void onError(Exception error);
    }

    /*
     * Listing page N — page 1 = https://mmlovetv.com/,
     * page N = https://mmlovetv.com/page/N/
     * (5-မိနစ် in-memory cache ပါသည်)။
     */
    public static void fetchPage(
            int page,
            PageCallback callback
    ) {
        fetchPage(page, false, callback);
    }

    public static void fetchPage(
            int page,
            boolean forceRefresh,
            PageCallback callback
    ) {
        final int safePage = Math.max(1, page);
        final boolean refresh = forceRefresh;

        EXECUTOR.execute(() -> {
            try {
                String url =
                        safePage <= 1
                                ? BASE_URL + "/"
                                : BASE_URL + "/page/"
                                        + safePage + "/";

                if (!refresh) {
                    CachedListing cached =
                            getCachedListing(url);

                    if (cached != null) {
                        Log.d(
                                TAG,
                                "listing cache hit: " + url
                        );

                        callback.onResult(
                                new ArrayList<>(cached.videos),
                                cached.hasMore
                        );

                        return;
                    }
                }

                String html = get(url, BASE_URL + "/");

                List<MmlovetvVideo> videos =
                        parseListing(html);

                int totalPages = parseTotalPages(html);

                boolean hasMore =
                        totalPages > 0
                                ? safePage < totalPages
                                : !videos.isEmpty();

                putCachedListing(url, videos, hasMore);

                callback.onResult(videos, hasMore);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    /*
     * Detail page ကို fetch လုပ်ပြီး presigned mp4
     * URL ကို fresh resolve လုပ်သည်။
     * 1) <source src=".../stream/{id}?..."> ကို
     *    အရင်ယူမည်။
     * 2) မရလျှင် /get-video-url/{id} API ကို
     *    XHR header ဖြင့် ခေါ်မည်။
     * Stream URL ကို cache လုံးဝ မလုပ်ပါ။
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

                String trimmedUrl = detailUrl.trim();

                String html =
                        get(trimmedUrl, BASE_URL + "/");

                /*
                 * The web player ALWAYS fetches a fresh
                 * presigned URL via get-video-url API
                 * (the <source src> in HTML is Varnish-
                 * cached and often expired/invalid).
                 * So we try the API FIRST.
                 */
                String attachmentId =
                        parseAttachmentId(html);

                MmlovetvStream stream = null;

                if (!attachmentId.isEmpty()) {
                    String freshUrl =
                            fetchFreshVideoUrl(
                                    attachmentId,
                                    trimmedUrl
                            );

                    if (!freshUrl.isEmpty()) {
                        stream =
                                new MmlovetvStream(
                                        freshUrl,
                                        getCookieHeader(),
                                        trimmedUrl
                                );
                    }
                }

                if (stream == null || !stream.hasStream()) {
                    /*
                     * Fallback: <source src> URL from HTML
                     * (may be cached/expired, but worth
                     * trying if API failed).
                     */
                    stream =
                            parseDetail(
                                    html,
                                    trimmedUrl,
                                    getCookieHeader()
                            );
                }

                if (!stream.hasStream()) {
                    throw new IllegalStateException(
                            "Video link ရှာမတွေ့ပါ။"
                    );
                }

                callback.onResult(stream);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    // ------------------------------------------------------------------
    // HTTP layer — cookie-preserving
    // ------------------------------------------------------------------

    private static String get(
            String url,
            String referer
    ) throws Exception {
        return get(url, referer, null);
    }

    /*
     * extraHeaders — e.g. X-Requested-With for the
     * get-video-url JSON API.
     */
    private static String get(
            String url,
            String referer,
            Map<String, String> extraHeaders
    ) throws Exception {
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
                            + "application/xml;q=0.9,"
                            + "application/json,"
                            + "image/avif,image/webp,*/*;q=0.8"
            );
            connection.setRequestProperty(
                    "Accept-Language", "en-US,en;q=0.9"
            );

            if (
                    referer != null && !referer.isEmpty()
            ) {
                connection.setRequestProperty(
                        "Referer", referer
                );
            }

            String cookieHeader = getCookieHeader();

            if (!cookieHeader.isEmpty()) {
                connection.setRequestProperty(
                        "Cookie", cookieHeader
                );
            }

            if (extraHeaders != null) {
                for (
                        Map.Entry<String, String> e :
                                extraHeaders.entrySet()
                ) {
                    connection.setRequestProperty(
                            e.getKey(), e.getValue()
                    );
                }
            }

            int status = connection.getResponseCode();

            storeCookies(connection);

            Log.d(TAG, "GET " + url + " -> HTTP " + status);

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

    private static synchronized void storeCookies(
            HttpURLConnection connection
    ) {
        Map<String, List<String>> headers =
                connection.getHeaderFields();

        if (headers == null) {
            return;
        }

        for (
                Map.Entry<String, List<String>> entry :
                        headers.entrySet()
        ) {
            if (
                    entry.getKey() == null ||
                            !entry.getKey()
                                    .equalsIgnoreCase(
                                            "Set-Cookie"
                                    )
            ) {
                continue;
            }

            for (String value : entry.getValue()) {
                try {
                    List<HttpCookie> parsed =
                            HttpCookie.parse(value);

                    for (HttpCookie cookie : parsed) {
                        COOKIE_JAR.removeIf(
                                existing ->
                                        existing.getName()
                                                .equals(
                                                        cookie.getName()
                                                )
                        );

                        if (!cookie.hasExpired()) {
                            COOKIE_JAR.add(cookie);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /*
     * Public accessor — Glide header module အတွက်။
     */
    public static synchronized String getCookieHeader() {
        StringBuilder builder = new StringBuilder();

        COOKIE_JAR.removeIf(
                cookie -> {
                    try {
                        return cookie.hasExpired();
                    } catch (Exception ignored) {
                        return true;
                    }
                }
        );

        for (HttpCookie cookie : COOKIE_JAR) {
            if (builder.length() > 0) {
                builder.append("; ");
            }

            builder.append(cookie.getName())
                    .append("=")
                    .append(cookie.getValue());
        }

        return builder.toString();
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
    // Listing parser — li.wp-block-post.video
    // ------------------------------------------------------------------

    /*
     * <li class="wp-block-post post-19824 video ...">
     *   <a href="https://mmlovetv.com/video/<slug>/"
     *      class="cfr2ss-video-thumbnail-link">
     *     <img src="https://img.mmlovetv.com/...png"
     *          alt="<title>">
     *     <span class="cfr2ss-duration-badge">00:59</span>
     *   </a>
     *   <h2 class="wp-block-post-title ...">
     *     <a ...>title</a>
     *   </h2>
     */
    private static final Pattern CARD_ITEM =
            Pattern.compile(
                    "<li[^>]*class=\"[^\"]*\\bwp-block-post\\b"
                            + "[^\"]*\\bvideo\\b[^\"]*\"(.*?)</li>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern CARD_LINK =
            Pattern.compile(
                    "<a[^>]*href=\"([^\"]*?/video/[^\"\"]+)\""
                            + "[^>]*class=\"[^\"]*cfr2ss-video-thumbnail-link"
                            + "[^\"]*\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern CARD_IMG =
            Pattern.compile(
                    "<img[^>]*src=\"([^\"]+)\"[^>]*alt=\"([^\"]*)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern CARD_IMG_ALT_AFTER =
            Pattern.compile(
                    "<img[^>]*alt=\"([^\"]*)\"[^>]*src=\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern CARD_DURATION =
            Pattern.compile(
                    "cfr2ss-duration-badge\"[^>]*>([^<]+)<",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CARD_TITLE_H2 =
            Pattern.compile(
                    "wp-block-post-title[^>]*>\\s*<a[^>]*>"
                            + "([^<]+)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    /*
     * Pagination: <a href="https://mmlovetv.com/page/208/">
     * → total pages (fallback -1)။
     */
    private static final Pattern PAGE_LINK =
            Pattern.compile(
                    "/page/(\\d+)/",
                    Pattern.CASE_INSENSITIVE
            );

    private static int parseTotalPages(String html) {
        if (html == null || html.isEmpty()) {
            return -1;
        }

        int max = -1;

        try {
            Matcher m = PAGE_LINK.matcher(html);

            while (m.find()) {
                int page = Integer.parseInt(m.group(1));

                if (page > max) {
                    max = page;
                }
            }
        } catch (Exception ignored) {
        }

        return max;
    }

    private static List<MmlovetvVideo> parseListing(
            String html
    ) {
        List<MmlovetvVideo> videos = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return videos;
        }

        String normalized = html.replace("\\/", "/");

        Matcher itemMatcher = CARD_ITEM.matcher(normalized);

        while (itemMatcher.find()) {
            String body = itemMatcher.group(1);

            Matcher linkMatcher = CARD_LINK.matcher(body);

            if (!linkMatcher.find()) {
                continue;
            }

            String detailUrl =
                    absoluteUrl(
                            linkMatcher.group(1).trim()
                    );

            if (
                    detailUrl.isEmpty() ||
                            !detailUrl.contains("/video/")
            ) {
                continue;
            }

            String title = "";
            String thumbUrl = "";

            Matcher imgMatcher = CARD_IMG.matcher(body);

            if (imgMatcher.find()) {
                thumbUrl =
                        absoluteUrl(
                                imgMatcher.group(1).trim()
                        );
                title =
                        unescapeHtml(
                                imgMatcher.group(2).trim()
                        );
            } else {
                Matcher imgAltMatcher =
                        CARD_IMG_ALT_AFTER.matcher(body);

                if (imgAltMatcher.find()) {
                    title =
                            unescapeHtml(
                                    imgAltMatcher.group(1)
                                            .trim()
                            );
                    thumbUrl =
                            absoluteUrl(
                                    imgAltMatcher.group(2)
                                            .trim()
                            );
                }
            }

            if (title.isEmpty()) {
                Matcher h2Matcher =
                        CARD_TITLE_H2.matcher(body);

                if (h2Matcher.find()) {
                    title =
                            unescapeHtml(
                                    h2Matcher.group(1).trim()
                            );
                }
            }

            if (title.isEmpty()) {
                title = "အမည်မသိ ဗီဒီယို";
            }

            String duration = "";
            Matcher durMatcher =
                    CARD_DURATION.matcher(body);

            if (durMatcher.find()) {
                duration = durMatcher.group(1).trim();
            }

            boolean duplicate = false;

            for (MmlovetvVideo existing : videos) {
                if (
                        existing.detailUrl.equals(detailUrl)
                ) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                videos.add(
                        new MmlovetvVideo(
                                title, thumbUrl,
                                detailUrl, duration
                        )
                );
            }
        }

        Log.d(TAG, "parseListing: " + videos.size() + " videos");

        return videos;
    }

    // ------------------------------------------------------------------
    // Detail parser — cfr2ss presigned stream URL
    // ------------------------------------------------------------------

    /*
     * <video id="cfr2-video-player"
     *        data-attachment-id="19816"
     *        data-mime-type="video/mp4" ...>
     *   <source src="https://mmlovetv.com/wp-json/cfr2ss/v1/stream/19816
     *     ?cfr2ss_exp=...&#038;cfr2ss_sig=..."
     *     type="video/mp4">
     *
     * NOTE: format is MP4 (NOT m3u8).
     */
    private static final Pattern ATTACHMENT_ID =
            Pattern.compile(
                    "data-attachment-id=\"(\\d+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern STREAM_SOURCE =
            Pattern.compile(
                    "<source[^>]*src=\"([^\"]*?/wp-json/cfr2ss/v1/stream/[^\"]+)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern FRESH_URL_JSON =
            Pattern.compile(
                    "\"url\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static String parseAttachmentId(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        Matcher m = ATTACHMENT_ID.matcher(html);

        if (m.find()) {
            return m.group(1).trim();
        }

        return "";
    }

    private static MmlovetvStream parseDetail(
            String html,
            String detailUrl,
            String cookieHeader
    ) {
        String streamUrl = "";

        if (html != null && !html.isEmpty()) {
            String normalized = html.replace("\\/", "/");

            Matcher srcMatcher =
                    STREAM_SOURCE.matcher(normalized);

            if (srcMatcher.find()) {
                streamUrl =
                        decodeEntities(
                                srcMatcher.group(1).trim()
                        );
            }

            Log.d(
                    TAG,
                    "parseDetail: url found=" +
                            !streamUrl.isEmpty()
            );
        }

        return new MmlovetvStream(
                streamUrl,
                cookieHeader,
                detailUrl
        );
    }

    /*
     * Fresh presigned URL via get-video-url JSON API:
     * GET /wp-json/cfr2ss/v1/get-video-url/{id}?_=ts
     * with X-Requested-With: XMLHttpRequest.
     * Returns {"success":true,"url":"..."}.
     */
    private static String fetchFreshVideoUrl(
            String attachmentId,
            String referer
    ) {
        try {
            String apiUrl =
                    BASE_URL
                            + "/wp-json/cfr2ss/v1/get-video-url/"
                            + attachmentId
                            + "?_="
                            + System.currentTimeMillis();

            Map<String, String> extra =
                    new LinkedHashMap<>();
            extra.put(
                    "X-Requested-With", "XMLHttpRequest"
            );

            String json = get(apiUrl, referer, extra);

            if (json == null || json.isEmpty()) {
                return "";
            }

            if (!json.contains("\"success\":true")
                    && !json.contains("\"success\": true")) {
                Log.d(
                        TAG,
                        "get-video-url not successful"
                );

                return "";
            }

            Matcher m = FRESH_URL_JSON.matcher(
                    json.replace("\\/", "/")
            );

            if (m.find()) {
                String freshUrl =
                        decodeEntities(m.group(1).trim());

                Log.d(
                        TAG,
                        "get-video-url: fresh url found=" +
                                !freshUrl.isEmpty()
                );

                return freshUrl;
            }
        } catch (Exception e) {
            Log.d(
                    TAG,
                    "get-video-url failed: " + e.getMessage()
            );
        }

        return "";
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String absoluteUrl(String url) {
        if (url == null) {
            return "";
        }

        String trimmed = url.trim();

        if (trimmed.isEmpty() || trimmed.startsWith("data:")) {
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

    private static String decodeEntities(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&#038;", "&")
                .replace("&#38;", "&")
                .replace("&amp;", "&")
                .replace("\\/", "/")
                .trim();
    }

    private static String unescapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&#038;", "&")
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


    /*
     * WebView-based stream resolve — hidden Chromium WebView
     * ဖြင့် detail page ကို browser အတိုင်း load လုပ်သည်။
     * Site ၏ ကိုယ်�ပိုင် JS (get-video-url API →
     * player.source) က ချပေးသော fresh presigned URL ကို
     * video element ၏ currentSrc မှ ပြန်ယူသည်။
     *
     * ဘာကြောင့် WebView လဲ:
     * /wp-json/cfr2ss/v1/* endpoint များသည်
     * HttpURLConnection (Java TLS fingerprint) မှ
     * ခေါ်သော request များကို cfr2ss_stream_forbidden
     * (403) ဖြင့် ငြင်းပယ်သည် — browser (Chromium)
     * stack သာလျှင် လက်ခံသည်။ WebView သည်
     * HttpOnly Worker cookie အပါအဝင် cookie အားလုံးကို
     * အလိုအလျောက် ကိုင်တွယ်သည်။
     *
     * NOTE: main thread ပေါ်တွင် အလုပ်လုပ်သည် —
     * ခေါ်သော thread မည်သည်ဖြစ်စေ main looper သို့
     * post လုပ်သည်။
     */
    public static void resolveStreamViaWebView(
            final android.content.Context context,
            final String detailUrl,
            final StreamCallback callback
    ) {
        android.os.Handler mainHandler =
                new android.os.Handler(
                        android.os.Looper.getMainLooper()
                );

        mainHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        resolveViaWebViewOnMain(
                                context,
                                detailUrl,
                                callback
                        );
                    }
                }
        );
    }

    /*
     * WebView ၏ CookieManager မှ cookie header
     * ထုတ်ယူသည် (HttpOnly Worker cookie အပါအဝင် —
     * getCookie() သည် HttpOnly များပါ ပြန်ပေးသည်)။
     */
    private static String getWebViewCookies(String logTag) {
        String cookies = "";

        try {
            android.webkit.CookieManager
                    .getInstance()
                    .flush();

            String c =
                    android.webkit.CookieManager
                            .getInstance()
                            .getCookie(
                                    "https://mmlovetv.com"
                            );

            if (c != null) {
                cookies = c;
            }
        } catch (Exception e) {
            android.util.Log.d(
                    logTag,
                    "getCookie failed: " + e
            );
        }

        if (cookies.isEmpty()) {
            cookies = getCookieHeader();
        }

        android.util.Log.d(
                logTag,
                "cookies length="
                        + cookies.length()
                        + " empty="
                        + cookies.isEmpty()
        );

        return cookies;
    }

    private static void resolveViaWebViewOnMain(
            final android.content.Context context,
            final String detailUrl,
            final StreamCallback callback
    ) {
        final String WV_TAG = TAG + "-WV";
        android.util.Log.d(WV_TAG, "resolveViaWebView start: " + detailUrl);

        android.webkit.WebView webView;

        try {
            webView =
                    new android.webkit.WebView(context);
        } catch (Exception e) {
            android.util.Log.d(WV_TAG, "WebView create failed: " + e);
            callback.onError(e);

            return;
        }

        final android.webkit.WebView wv = webView;

        /*
         * 1x1 invisible — window attached ဖြစ်မှ
         * JS timer / page load ပုံမှန်အလုပ်လုပ်သည်။
         */
        try {
            if (
                    context
                            instanceof
                            android.app.Activity
            ) {
                android.app.Activity activity =
                        (android.app.Activity) context;

                android.widget.FrameLayout.LayoutParams
                        lp =
                        new android.widget.FrameLayout.LayoutParams(
                                1,
                                1
                        );

                wv.setLayoutParams(lp);
                wv.setVisibility(
                        android.view.View.INVISIBLE
                );
                activity.addContentView(wv, lp);
            }
        } catch (Exception ignored) {
        }

        android.webkit.WebSettings settings =
                wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(
                false
        );

        try {
            settings.setUserAgentString(USER_AGENT);
        } catch (Exception ignored) {
        }

        try {
            android.webkit.CookieManager
                    .getInstance()
                    .setAcceptCookie(true);
        } catch (Exception ignored) {
        }

        final android.os.Handler mainHandler =
                new android.os.Handler(
                        android.os.Looper.getMainLooper()
                );

        final java.util.concurrent.atomic.AtomicBoolean
                done =
                new java.util.concurrent.atomic.AtomicBoolean(
                        false
                );

        final Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                if (
                        done.compareAndSet(false, true)
                ) {
                    try {
                        if (wv.getParent() != null) {
                            ((android.view.ViewGroup)
                                    wv.getParent())
                                    .removeView(wv);
                        }
                    } catch (Exception ignored) {
                    }

                    try {
                        wv.destroy();
                    } catch (Exception ignored) {
                    }
                }
            }
        };



        /*
         * Site ၏ ကိုယ်�ပိုင် JS နှင့် အတူတူ —
         * get-video-url API ကို WebView ၏ JS context
         * (browser cookies + Chromium TLS) ထဲမှ
         * တိုက်ရိုက်ခေါ်ပြီး fresh presigned URL ကို
         * @JavascriptInterface မှတဆင့် ပြန်ယူသည်။
         *
         * DOM polling (currentSrc) ထက် ပိုစိတ်ချရသည် —
         * Plyr က media element ကို rebuild လုပ်တတ်သဖြင့်
         * #cfr2-video-player ၏ currentSrc သည်
         * လွဲတတ်သည်။
         */
        final Object jsBridge = new Object() {
            @android.webkit.JavascriptInterface
            public void onVideoUrl(String url) {
                android.util.Log.d(
                        WV_TAG,
                        "JS bridge got URL, len="
                                + (url == null
                                        ? -1
                                        : url.length())
                );

                if (url == null) {
                    return;
                }

                final String cleanUrl = url.trim();

                if (cleanUrl.isEmpty()) {
                    android.util.Log.d(
                            WV_TAG,
                            "JS bridge URL empty"
                    );

                    return;
                }

                android.util.Log.d(
                        WV_TAG,
                        "JS bridge URL head: "
                                + cleanUrl.substring(
                                        0,
                                        Math.min(
                                                120,
                                                cleanUrl.length()
                                        )
                                )
                );

                mainHandler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (
                                        !done.compareAndSet(
                                                false,
                                                true
                                        )
                                ) {
                                        return;
                                }

                                MmlovetvStream stream =
                                        new MmlovetvStream(
                                                cleanUrl,
                                                getWebViewCookies(WV_TAG),
                                                detailUrl
                                        );

                                cleanup.run();
                                callback.onResult(stream);
                            }
                        }
                );
            }

            @android.webkit.JavascriptInterface
            public void onApiError(String msg) {
                android.util.Log.d(
                        WV_TAG,
                        "JS API error: " + msg
                );
            }
        };

        try {
            wv.addJavascriptInterface(jsBridge, "Android");
            android.util.Log.d(
                    WV_TAG,
                    "JavascriptInterface added"
            );
        } catch (Exception e) {
            android.util.Log.d(
                    WV_TAG,
                    "addJavascriptInterface failed: " + e
            );
        }

        final android.os.Handler pollHandler =
                new android.os.Handler(
                        android.os.Looper.getMainLooper()
                );

        /*
         * API fetch အတွက် JS — site ၏ plyr-js-js-after
         * နှင့် အတိအကျ တူညီသော request။
         */
        final Runnable injectApiFetch = new Runnable() {
            @Override
            public void run() {
                if (done.get()) {
                    return;
                }

                android.util.Log.d(
                        WV_TAG,
                        "injecting API fetch JS"
                );

                try {
                    wv.evaluateJavascript(
                            "(function(){"
                                    + "try{"
                                    + "var v=document.getElementById"
                                    + "('cfr2-video-player');"
                                    + "if(!v){Android.onApiError"
                                    + "('no video element');return;}"
                                    + "var id=v.getAttribute"
                                    + "('data-attachment-id');"
                                    + "if(!id){Android.onApiError"
                                    + "('no attachment-id');return;}"
                                    + "Android.onApiError"
                                    + "('fetching id='+id);"
                                    + "fetch('https://mmlovetv.com"
                                    + "/wp-json/cfr2ss/v1/get-video-url/'"
                                    + "+id+'?_='+Date.now(),{"
                                    + "method:'GET',"
                                    + "cache:'no-store',"
                                    + "credentials:'same-origin',"
                                    + "headers:{'X-Requested-With':"
                                    + "'XMLHttpRequest'}})"
                                    + ".then(function(r){"
                                    + "Android.onApiError"
                                    + "('api status='+r.status);"
                                    + "return r.json();})"
                                    + ".then(function(d){"
                                    + "if(d&&d.success&&d.url){"
                                    + "Android.onVideoUrl(d.url);}"
                                    + "else{Android.onApiError"
                                    + "('api bad: '+JSON.stringify(d)"
                                    + ".substring(0,200));}})"
                                    + ".catch(function(e){"
                                    + "Android.onApiError"
                                    + "('fetch fail: '+e);});"
                                    + "}catch(e){Android.onApiError"
                                    + "('js exc: '+e);}"
                                    + "})()",
                            null
                    );
                } catch (Exception e) {
                    android.util.Log.d(
                            WV_TAG,
                            "evaluateJavascript failed: " + e
                    );
                }
            }
        };

        /*
         * Fallback: DOM poll (ယခင် logic) — API fetch
         * မအောင်မြင်ခဲ့လျှင်။
         */
        final java.util.concurrent.atomic.AtomicBoolean
                domPollStarted =
                new java.util.concurrent.atomic.AtomicBoolean(
                        false
                );

        final java.util.concurrent.atomic.AtomicInteger
                attempts =
                new java.util.concurrent.atomic.AtomicInteger(
                        0
                );

        final int maxAttempts = 20;
        final long pollIntervalMs = 700L;

        final Runnable[] pollerHolder = new Runnable[1];

        final android.webkit.ValueCallback<String>
                jsCallback =
                new android.webkit.ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(
                            String value
                    ) {
                        if (done.get()) {
                            return;
                        }

                        String src =
                                unquoteJsValue(value);

                        android.util.Log.d(
                                WV_TAG,
                                "DOM poll got: "
                                        + (src.isEmpty()
                                                ? "(empty)"
                                                : src.substring(
                                                        0,
                                                        Math.min(
                                                                100,
                                                                src.length()
                                                        )
                                                ))
                        );

                        if (
                                !src.isEmpty()
                                        && done.compareAndSet(
                                                false, true
                                        )
                        ) {
                            MmlovetvStream stream =
                                    new MmlovetvStream(
                                            src,
                                            getWebViewCookies(WV_TAG),
                                            detailUrl
                                    );

                            cleanup.run();
                            callback.onResult(stream);
                        } else if (!done.get()) {
                            Runnable p =
                                    pollerHolder[0];

                            if (p != null) {
                                pollHandler.postDelayed(
                                        p,
                                        pollIntervalMs
                                );
                            }
                        }
                    }
                };

        final Runnable domPoller = new Runnable() {
            @Override
            public void run() {
                if (done.get()) {
                    return;
                }

                if (
                        attempts.incrementAndGet()
                                > maxAttempts
                ) {
                    android.util.Log.d(
                            WV_TAG,
                            "DOM poll exhausted, error out"
                    );
                    cleanup.run();
                    callback.onError(
                            new IllegalStateException(
                                    "Video link ရယူ၍မရပါ။"
                            )
                    );

                    return;
                }

                try {
                    wv.evaluateJavascript(
                            "(function(){"
                                    + "var v=document.getElementById"
                                    + "('cfr2-video-player');"
                                    + "if(!v)return '';"
                                    + "var s=v.currentSrc||v.src||'';"
                                    + "if(s)return s;"
                                    + "var sc=v.querySelector"
                                    + "('source');"
                                    + "return sc?(sc.src||''):'';"
                                    + "})()",
                            jsCallback
                    );
                } catch (Exception e) {
                    if (!done.get()) {
                        pollHandler.postDelayed(
                                this, pollIntervalMs
                        );
                    }
                }
            }
        };

        pollerHolder[0] = domPoller;

        final Runnable startDomPoll = new Runnable() {
            @Override
            public void run() {
                if (
                        !done.get()
                                && domPollStarted.compareAndSet(
                                        false, true
                                )
                ) {
                    android.util.Log.d(
                            WV_TAG,
                            "starting DOM poll fallback"
                    );
                    pollHandler.post(domPoller);
                }
            }
        };

        wv.setWebViewClient(
                new android.webkit.WebViewClient() {
                    @Override
                    public void onPageFinished(
                            android.webkit.WebView view,
                            String url
                    ) {
                        android.util.Log.d(
                                WV_TAG,
                                "onPageFinished: " + url
                        );

                        /*
                         * Site ၏ ကိုယ်�ပိုင် JS (fetch)
                         * ပြီးဆုံးရန် ခဏစောင့်၊ ပြီးမှ
                         * ကိုယ်�ပိုင် API fetch ထိုးမည်။
                         */
                        pollHandler.postDelayed(
                                injectApiFetch, 2500L
                        );

                        /*
                         * API fetch 12 စက္ကန့်အတွင်း
                         * မရလျှင် DOM poll fallback။
                         */
                        pollHandler.postDelayed(
                                startDomPoll, 12000L
                        );
                    }

                    @Override
                    public void onReceivedError(
                            android.webkit.WebView view,
                            android.webkit.WebResourceRequest
                                    request,
                            android.webkit.WebResourceError
                                    error
                    ) {
                        android.util.Log.d(
                                WV_TAG,
                                "onReceivedError: "
                                        + error.getDescription()
                        );
                    }
                }
        );

        /*
         * Safety: onPageFinished မခေါ်ဖြစ်ခဲ့လျှင်တောင်
         * API fetch စမ်းမည်။
         */
        pollHandler.postDelayed(injectApiFetch, 8000L);
        pollHandler.postDelayed(startDomPoll, 18000L);

        /*
         * Absolute timeout — 30s ကြာလည်း မရလျှင်
         * error (MyanmarDetailActivity က fallback
         * resolveStream ကို ခေါ်မည်)။
         */
        pollHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        if (
                                !done.get()
                        ) {
                            android.util.Log.d(
                                    WV_TAG,
                                    "absolute timeout, error out"
                            );
                            cleanup.run();
                            callback.onError(
                                    new IllegalStateException(
                                            "Video link ရယူ၍မရပါ။"
                                    )
                            );
                        }
                    }
                },
                30000L
        );

        try {
            android.util.Log.d(
                    WV_TAG,
                    "loadUrl: " + detailUrl
            );
            wv.loadUrl(detailUrl);
        } catch (Exception e) {
            cleanup.run();
            callback.onError(e);
        }
    }

    /*
     * evaluateJavascript ၏ quoted JSON string
     * result ကို unquote/unescape လုပ်သည်။
     */
    private static String unquoteJsValue(String value) {
        if (value == null) {
            return "";
        }

        String s = value.trim();

        if (
                s.length() >= 2
                        && s.startsWith("\"")
                        && s.endsWith("\"")
        ) {
            s = s.substring(1, s.length() - 1);
        }

        s =
                s.replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\/", "/");

        if ("null".equals(s)) {
            return "";
        }

        return s.trim();
    }

    public static synchronized void clearCookies() {
        COOKIE_JAR.clear();
    }
}
