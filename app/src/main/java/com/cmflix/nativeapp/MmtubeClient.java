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
 * mmtube.net scraper — "Myanmar 1" source (direct, no proxy).
 *
 * KVS (Kernel Video Sharing) template site:
 * - Listing: GET https://www.mmtube.net/latest-updates
 *   page N = https://www.mmtube.net/latest-updates/N/
 *   (20 cards per page; card = div.thumb.thumb_rel.item,
 *   detail URL in <a href>, title in title attr,
 *   thumb in img[data-original]).
 * - Detail: GET https://www.mmtube.net/video/<id>/<hash>/
 *   → flashvars video_url: '<mp4>' (single quality;
 *   video_url_hd:'1' is only a flag, not a URL).
 *
 * Direct connection (NO proxy) — the site currently
 * works from Myanmar without VPN.
 * No login/cookies needed for listing; the detail
 * page sets session cookies (kt_acctoken, PHPSESSID)
 * which are forwarded to the player/download along
 * with the detail-page Referer for the tokenized
 * get_file MP4 URL.
 * Stream URLs are NOT cached — always fresh resolve.
 *
 * NOTE: CookieManager ကို global default အဖြစ်
 * မသတ်မှတ်ပါ — ApiClient ၏ request များကို
 * လုံးဝ မထိခိုက်စေရန် manual cookie jar သုံးသည်။
 */
public final class MmtubeClient {

    private static final String TAG = "MmtubeClient";

    /*
     * Direct site URL (plaintext — the user provided
     * this URL openly; build-time encryption key is
     * unavailable outside CI).
     */
    public static final String BASE_URL =
            "https://www.mmtube.net";

    public static final String LIST_PATH =
            "/latest-updates";

    /*
     * Mmtube video များ၏ stable ID prefix။
     * LocalStore resume key အဖြစ်
     * "mmtube:" + detailUrl ကို သုံးသည်။
     */
    public static final String ID_PREFIX = "mmtube:";

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
     * Mmtube-only in-memory cookie jar (KVS session
     * cookies for the tokenized get_file MP4 URL).
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
        final List<MmtubeVideo> videos;
        final boolean hasMore;

        CachedListing(
                long fetchedAt,
                List<MmtubeVideo> videos,
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
            List<MmtubeVideo> videos,
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

    private MmtubeClient() {
    }

    public static final class MmtubeVideo {
        public final String title;
        public final String thumbUrl;
        public final String detailUrl;

        public MmtubeVideo(
                String title,
                String thumbUrl,
                String detailUrl
        ) {
            this.title = title;
            this.thumbUrl = thumbUrl;
            this.detailUrl = detailUrl;
        }
    }

    public static final class MmtubeStream {
        public final String url;

        /*
         * flashvars video_url_hd flag ('1' = HD) —
         * row label အတွက်သာ။
         */
        public final boolean isHd;

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

        public MmtubeStream(
                String url,
                boolean isHd,
                String cookieHeader,
                String referer
        ) {
            this.url = url;
            this.isHd = isHd;
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
                List<MmtubeVideo> videos,
                boolean hasMore
        );

        void onError(Exception error);
    }

    public interface StreamCallback {
        void onResult(MmtubeStream stream);

        void onError(Exception error);
    }

    /*
     * Listing page N — page 1 = /latest-updates,
     * page N = /latest-updates/N/
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
                                ? BASE_URL + LIST_PATH
                                : BASE_URL + LIST_PATH
                                        + "/" + safePage + "/";

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

                List<MmtubeVideo> videos =
                        parseListing(html);

                /*
                 * Pagination မှ total pages ကို
                 * ဖတ်နိုင်လျှင် အဲ့ဒါကို သုံးမည်၊
                 * မရလျှင် non-empty fallback။
                 */
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
     * Detail page ကို fetch လုပ်ပြီး tokenized mp4
     * URL ကို fresh resolve လုပ်သည်။
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

                String html =
                        get(detailUrl.trim(), BASE_URL + "/");

                MmtubeStream stream =
                        parseDetail(
                                html,
                                detailUrl.trim(),
                                currentCookieHeader()
                        );

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

            String cookieHeader = currentCookieHeader();

            if (!cookieHeader.isEmpty()) {
                connection.setRequestProperty(
                        "Cookie", cookieHeader
                );
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

    private static synchronized String currentCookieHeader() {
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
    // Listing parser — div.thumb.thumb_rel.item
    // ------------------------------------------------------------------

    /*
     * <div class="thumb thumb_rel item  ">
     *   <a href="https://www.mmtube.net/video/6404/.../"
     *      title="...">
     *     <div class="img-holder">
     *       <img class="lazy-load"
     *            src="data:image/gif;base64,..."
     *            data-original="https://.../390x218/1.jpg"
     *            ...>
     */
    private static final Pattern ITEM_BLOCK =
            Pattern.compile(
                    "<div\\s+class=\"thumb\\s+thumb_rel\\s+item[^\"]*\""
                            + "(.*?)</div>\\s*</div>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern ITEM_LINK =
            Pattern.compile(
                    "<a\\s+href=\"([^\"]+)\"[^>]*"
                            + "title=\"([^\"]*)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern ITEM_THUMB =
            Pattern.compile(
                    "data-original=\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern ITEM_THUMB_WEBP =
            Pattern.compile(
                    "data-webp=\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Pagination: <a href="/latest-updates/307/" ...>307</a>
     * → total pages (fallback -1)။
     */
    private static final Pattern PAGE_LINK =
            Pattern.compile(
                    "/latest-updates/(\\d+)/",
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

    private static List<MmtubeVideo> parseListing(
            String html
    ) {
        List<MmtubeVideo> videos = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return videos;
        }

        String normalized = html.replace("\\/", "/");

        Matcher itemMatcher = ITEM_BLOCK.matcher(normalized);

        while (itemMatcher.find()) {
            String body = itemMatcher.group(1);

            Matcher linkMatcher = ITEM_LINK.matcher(body);

            if (!linkMatcher.find()) {
                continue;
            }

            String detailUrl =
                    absoluteUrl(
                            linkMatcher.group(1).trim()
                    );

            if (detailUrl.isEmpty()) {
                continue;
            }

            String title =
                    unescapeHtml(
                            linkMatcher.group(2).trim()
                    );

            if (title.isEmpty()) {
                title = "အမည်မသိ ဗီဒီယို";
            }

            String thumbUrl = "";
            Matcher thumbMatcher =
                    ITEM_THUMB.matcher(body);

            if (thumbMatcher.find()) {
                thumbUrl =
                        absoluteUrl(
                                thumbMatcher.group(1).trim()
                        );
            } else {
                Matcher webpMatcher =
                        ITEM_THUMB_WEBP.matcher(body);

                if (webpMatcher.find()) {
                    thumbUrl =
                            absoluteUrl(
                                    webpMatcher.group(1)
                                            .trim()
                            );
                }
            }

            // base64 placeholder ကို ကျော်မည်
            if (thumbUrl.startsWith("data:")) {
                thumbUrl = "";
            }

            boolean duplicate = false;

            for (MmtubeVideo existing : videos) {
                if (
                        existing.detailUrl.equals(detailUrl)
                ) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                videos.add(
                        new MmtubeVideo(
                                title, thumbUrl, detailUrl
                        )
                );
            }
        }

        Log.d(TAG, "parseListing: " + videos.size() + " videos");

        return videos;
    }

    // ------------------------------------------------------------------
    // Detail parser — flashvars video_url
    // ------------------------------------------------------------------

    /*
     * var flashvars = { ... video_url: 'https://www.mmtube.net/get_file/0/...mp4/?v-acctoken=...',
     *                   video_url_hd: '1', ... }
     * NOTE: video_url_hd သည် flag သာ ('1') — URL မဟုတ်။
     */
    private static final Pattern VIDEO_URL =
            Pattern.compile(
                    "\\bvideo_url\\b\\s*:\\s*'([^']+)'",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern VIDEO_URL_HD_FLAG =
            Pattern.compile(
                    "\\bvideo_url_hd\\b\\s*:\\s*'([^']*)'",
                    Pattern.CASE_INSENSITIVE
            );

    private static MmtubeStream parseDetail(
            String html,
            String detailUrl,
            String cookieHeader
    ) {
        String streamUrl = "";
        boolean isHd = false;

        if (html != null && !html.isEmpty()) {
            String normalized = html.replace("\\/", "/");

            Matcher urlMatcher =
                    VIDEO_URL.matcher(normalized);

            if (urlMatcher.find()) {
                streamUrl = urlMatcher.group(1).trim();
            }

            Matcher hdMatcher =
                    VIDEO_URL_HD_FLAG.matcher(normalized);

            if (hdMatcher.find()) {
                isHd =
                        "1".equals(
                                hdMatcher.group(1).trim()
                        );
            }

            Log.d(
                    TAG,
                    "parseDetail: url found=" +
                            !streamUrl.isEmpty() +
                            " hd=" + isHd
            );
        }

        return new MmtubeStream(
                streamUrl,
                isHd,
                cookieHeader,
                detailUrl
        );
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

    private static String unescapeHtml(String text) {
        if (text == null) {
            return "";
        }

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

    public static synchronized void clearCookies() {
        COOKIE_JAR.clear();
    }
}
