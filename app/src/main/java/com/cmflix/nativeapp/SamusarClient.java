package com.cmflix.nativeapp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
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
 * samusar.com scraper — listing + stream resolve.
 *
 * ALL traffic goes through the Cloudflare Pages proxy
 * (PROXY_BASE_URL) — the phone never contacts
 * samusar.com directly (VPN bypass for Myanmar users).
 * The proxy rewrites samusar.com URLs in HTML to its
 * own origin, so parsed detail/thumb/stream URLs are
 * already proxy URLs.
 *
 * - Network request အားလုံး background thread ပေါ်မှာ။
 * - Cookie များကို request တိုင်းတွင် ပြန်ပို့သည်
 *   (detail page session + tokenized mp4 URL များအတွက်)။
 * - Stream URL များကို ဘယ်နေရာမှ cache မလုပ်ပါ —
 *   token များသည် session-bound ဖြစ်သောကြောင့်
 *   play/download မလုပ်မီ အမြဲ fresh resolve လုပ်ရမည်။
 *
 * NOTE: CookieManager ကို global default အဖြစ်
 * မသတ်မှတ်ပါ — ApiClient ၏ request များကို
 * လုံးဝ မထိခိုက်စေရန် manual cookie jar သုံးသည်။
 */
public final class SamusarClient {

    private static final String TAG =
            "SamusarClient";

    /*
     * Cloudflare Pages proxy — samusar.com VPN bypass
     * for Myanmar users.
     *
     * ALL Myanmar traffic (listing HTML, detail HTML,
     * thumbnails, video streams) goes through this
     * proxy; the phone never contacts samusar.com
     * directly. The proxy rewrites samusar.com URLs
     * in HTML to its own origin and translates the
     * Referer back internally.
     *
     * Change this one constant to point at a new
     * proxy host if needed.
     */
    public static final String PROXY_BASE_URL =
            "https://tw.kyakya.xubi.org";

    /*
     * Proxy base URL resolver — server-configurable။
     *
     * /app-content ၏ samusar.proxy (AppContentManager
     * မှတဆင့်) ရှိလျှင် အဲ့ဒါကို သုံးမည်၊ မရှိလျှင် /
     * လွတ်နေလျှင် PROXY_BASE_URL default သို့
     * fallback လုပ်မည်။
     *
     * Domain ပျက်/ပြောင်းလျှင် server (D1) မှာ
     * ပြင်ရုံဖြင့် APK rebuild မလိုတော့ပါ။
     *
     * Trailing slash များကို ဖယ်ပြီး http(s)
     * URL စစ်မှန်ကြောင်း validate လုပ်သည်။
     */
    public static String getBaseUrl() {
        String configured =
                AppContentManager
                        .getCachedSamusarProxyUrl();

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

        return PROXY_BASE_URL;
    }

    private static final String LIST_PATH =
            "/latest-updates";

    /*
     * Listing path resolver — server-configurable။
     *
     * /app-content ၏ samusar.listing (AppContentManager
     * မှတဆင့်) ရှိလျှင် အဲ့ဒါကို သုံးမည်၊ မရှိလျှင်
     * LIST_PATH default ("/latest-updates") သို့ fallback။
     *
     * Path ပြောင်း/ပျက်လျှင် server (D1) မှာ
     * ပြင်ရုံဖြင့် APK rebuild မလိုတော့ပါ။
     *
     * Leading slash မရှိလျှင် ဖြည့်ပေးမည်။
     */
    public static String getListPath() {
        String configured =
                AppContentManager.getCachedSamusarListingPath();

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
     * Samusar video များ၏ stable ID prefix။
     * LocalStore resume key အဖြစ်
     * "samusar:" + detailUrl ကို သုံးသည်။
     */
    public static final String ID_PREFIX =
            "samusar:";

    public static String videoId(String detailUrl) {
        String url =
                detailUrl == null
                        ? ""
                        : detailUrl.trim();

        if (url.startsWith(ID_PREFIX)) {
            return url;
        }

        return ID_PREFIX + url;
    }

    public static String detailUrlFromId(String videoId) {
        String id =
                videoId == null
                        ? ""
                        : videoId.trim();

        if (id.startsWith(ID_PREFIX)) {
            return id.substring(ID_PREFIX.length());
        }

        return id;
    }

    /*
     * Browser-like UA — samusar သည် bot UA များကို
     * block လုပ်နိုင်သောကြောင့်။
     */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_REDIRECTS = 5;

    private static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool();

    /*
     * Samusar-only in-memory cookie jar.
     * Synchronized — background thread အများအပြားမှ
     * တစ်ပြိုင်နက် သုံးနိုင်သည်။
     *
     * NOTE: cookie များကို domain ဖြင့် ခွဲမသိမ်းပါ —
     * request အားလုံး proxy host တခုတည်းသို့
     * သွားသောကြောင့် name-keyed jar တစ်ခု လုံလောက်သည်။
     */
    private static final List<HttpCookie> COOKIE_JAR =
            new ArrayList<>();

    /*
     * Listing page cache (5 မိနစ်) — proxy + edge
     * cache နှင့်အတူ request အရေအတွက် လျှော့ချရန်။
     * Key = page URL, value = (timestamp, items).
     * Detail page / stream URL များကို cache
     * လုံးဝ မလုပ်ပါ (token များ dynamic ဖြစ်သောကြောင့်)။
     */
    private static final long LISTING_CACHE_TTL_MS =
            5 * 60 * 1000L;

    private static final int LISTING_CACHE_MAX_PAGES =
            20;

    private static final class CachedListing {
        final long fetchedAt;
        final List<SamusarVideo> videos;
        final boolean hasMore;

        CachedListing(
                long fetchedAt,
                List<SamusarVideo> videos,
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
        CachedListing cached =
                LISTING_CACHE.get(url);

        if (cached == null) {
            return null;
        }

        if (
                System.currentTimeMillis() -
                        cached.fetchedAt >
                        LISTING_CACHE_TTL_MS
        ) {
            LISTING_CACHE.remove(url);

            return null;
        }

        return cached;
    }

    private static synchronized void putCachedListing(
            String url,
            List<SamusarVideo> videos,
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
                LISTING_CACHE.size() >
                        LISTING_CACHE_MAX_PAGES
        ) {
            String oldest =
                    LISTING_CACHE.keySet()
                            .iterator()
                            .next();

            LISTING_CACHE.remove(oldest);
        }
    }

    /*
     * Listing cache ကို ရှင်းရန် (pull-to-refresh /
     * manual refresh အတွက်)။
     */
    public static synchronized void clearListingCache() {
        LISTING_CACHE.clear();
    }

    private SamusarClient() {
    }

    public static final class SamusarVideo {
        public final String title;
        public final String thumbUrl;
        public final String detailUrl;

        public SamusarVideo(
                String title,
                String thumbUrl,
                String detailUrl
        ) {
            this.title = title;
            this.thumbUrl = thumbUrl;
            this.detailUrl = detailUrl;
        }
    }

    public static final class SamusarStream {
        public final String url480;
        public final String url720;
        public final String url1080;

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

        public SamusarStream(
                String url480,
                String url720,
                String url1080,
                String cookieHeader,
                String referer
        ) {
            this.url480 = url480;
            this.url720 = url720;
            this.url1080 = url1080;
            this.cookieHeader = cookieHeader;
            this.referer = referer;
        }

        /*
         * Default 720p, fallback 480p, နောက်ဆုံး 1080p။
         */
        public String bestUrl() {
            if (
                    url720 != null &&
                            !url720.isEmpty()
            ) {
                return url720;
            }

            if (
                    url480 != null &&
                            !url480.isEmpty()
            ) {
                return url480;
            }

            return url1080 == null ? "" : url1080;
        }

        public boolean hasStream() {
            return !bestUrl().isEmpty();
        }
    }

    public interface PageCallback {
        void onResult(
                List<SamusarVideo> videos,
                boolean hasMore
        );

        void onError(Exception error);
    }

    public interface StreamCallback {
        void onResult(SamusarStream stream);

        void onError(Exception error);
    }

    /*
     * Listing page N ကို fetch လုပ်သည်။
     * Page 1 = /latest-updates , page N = /latest-updates/N/
     *
     * 5-မိနစ် in-memory cache ပါသည်။
     */
    public static void fetchPage(
            int page,
            PageCallback callback
    ) {
        fetchPage(page, false, callback);
    }

    /*
     * forceRefresh = true ဆိုလျှင် cache ကို
     * ကျော်ပြီး အမြဲ fresh fetch လုပ်မည်
     * (pull-to-refresh / manual refresh အတွက်)။
     */
    public static void fetchPage(
            int page,
            boolean forceRefresh,
            PageCallback callback
    ) {
        final int safePage = Math.max(1, page);
        final boolean refresh = forceRefresh;

        EXECUTOR.execute(() -> {
            try {
                String baseUrl = getBaseUrl();
                String listPath = getListPath();

                String url =
                        safePage <= 1
                                ? baseUrl + listPath
                                : baseUrl + listPath
                                        + "/" + safePage + "/";

                if (!refresh) {
                    CachedListing cached =
                            getCachedListing(url);

                    if (cached != null) {
                        Log.d(
                                TAG,
                                "listing cache hit: "
                                        + url
                        );

                        callback.onResult(
                                new ArrayList<>(
                                        cached.videos
                                ),
                                cached.hasMore
                        );

                        return;
                    }
                }

                String html = get(url, baseUrl + "/");

                List<SamusarVideo> videos =
                        parseListing(html);

                /*
                 * Card အရေအတွက် 0 ဖြစ်နေလျှင်
                 * နောက် page မရှိတော့ဟု ယူဆသည်။
                 */
                boolean hasMore = !videos.isEmpty();

                putCachedListing(url, videos, hasMore);

                callback.onResult(videos, hasMore);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    /*
     * Detail page ကို fetch လုပ်ပြီး tokenized mp4
     * URL များကို fresh resolve လုပ်သည်။
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
                        get(detailUrl.trim(), getBaseUrl() + "/");

                SamusarStream stream =
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
    // HTTP layer — cookie-preserving, manual redirect handling
    // ------------------------------------------------------------------

    private static String get(
            String url,
            String referer
    ) throws Exception {
        String currentUrl = forceHttps(url);
        String currentReferer = referer;

        for (
                int hop = 0;
                hop <= MAX_REDIRECTS;
                hop++
        ) {
            HttpURLConnection connection = null;

            try {
                connection =
                        (HttpURLConnection)
                                new URL(currentUrl)
                                        .openConnection();

                connection.setInstanceFollowRedirects(
                        false
                );
                connection.setConnectTimeout(
                        CONNECT_TIMEOUT_MS
                );
                connection.setReadTimeout(
                        READ_TIMEOUT_MS
                );
                connection.setRequestMethod("GET");

                connection.setRequestProperty(
                        "User-Agent",
                        USER_AGENT
                );
                connection.setRequestProperty(
                        "Accept",
                        "text/html,application/xhtml+xml,"
                                + "application/xml;q=0.9,"
                                + "image/avif,image/webp,*/*;q=0.8"
                );
                connection.setRequestProperty(
                        "Accept-Language",
                        "en-US,en;q=0.9"
                );
                connection.setRequestProperty(
                        "Accept-Encoding",
                        "identity"
                );

                if (
                        currentReferer != null &&
                                !currentReferer.isEmpty()
                ) {
                    connection.setRequestProperty(
                            "Referer",
                            currentReferer
                    );
                }

                String cookieHeader =
                        currentCookieHeader();

                if (!cookieHeader.isEmpty()) {
                    connection.setRequestProperty(
                            "Cookie",
                            cookieHeader
                    );
                }

                int status =
                        connection.getResponseCode();

                storeCookies(connection);

                Log.d(
                        TAG,
                        "GET " + currentUrl
                                + " -> HTTP " + status
                );

                if (isRedirect(status)) {
                    String location =
                            connection.getHeaderField(
                                    "Location"
                            );

                    if (
                            location == null ||
                                    location.isEmpty()
                    ) {
                        throw new IllegalStateException(
                                "Redirect location မရှိပါ: "
                                        + status
                        );
                    }

                    currentReferer = currentUrl;
                    currentUrl =
                            resolveUrl(
                                    currentUrl,
                                    location.trim()
                            );

                    continue;
                }

                if (
                        status < 200 ||
                                status >= 300
                ) {
                    String errorBody =
                            readErrorBody(connection);

                    Log.d(
                            TAG,
                            "GET " + currentUrl
                                    + " error body length="
                                    + errorBody.length()
                    );

                    if (isCloudflareChallenge(errorBody)) {
                        throw new IllegalStateException(
                                "Cloudflare protection blocked"
                                        + " the request (HTTP "
                                        + status + ")"
                        );
                    }

                    throw new IllegalStateException(
                            "Request failed: " + status
                    );
                }

                String body =
                        readStream(
                                connection,
                                connection.getInputStream()
                        );

                Log.d(
                        TAG,
                        "GET " + currentUrl
                                + " body length="
                                + body.length()
                );

                if (isCloudflareChallenge(body)) {
                    throw new IllegalStateException(
                            "Cloudflare protection blocked"
                                    + " the request"
                    );
                }

                return body;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        throw new IllegalStateException(
                "Redirect အလွန်အကျွံများနေသည်။"
        );
    }

    private static boolean isRedirect(int status) {
        return status == 301 ||
                status == 302 ||
                status == 303 ||
                status == 307 ||
                status == 308;
    }

    private static String forceHttps(String url) {
        if (
                url != null
                        && url.regionMatches(
                                true, 0,
                                "http://", 0, 7
                        )
        ) {
            return "https://" + url.substring(7);
        }

        return url;
    }

    private static String resolveUrl(
            String base,
            String location
    ) throws Exception {
        String resolved =
                new URL(new URL(base), location)
                        .toString();

        /*
         * Proxy/origin တခါတရံ http:// သို့ redirect
         * ချတတ်သည် — Android 9+ က cleartext ကို
         * ပိတ်ထားသောကြောင့် https:// သို့ အတင်း
         * upgrade လုပ်မည်။
         */
        if (
                resolved.regionMatches(
                        true, 0,
                        "http://", 0, 7
                )
        ) {
            resolved = forceHttps(resolved);
        }

        return resolved;
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
                        /*
                         * နာမည်တူ cookie အဟောင်းကို
                         * အစားထိုးမည်။
                         */
                        COOKIE_JAR.removeIf(
                                existing ->
                                        existing.getName()
                                                .equals(
                                                        cookie.getName()
                                                )
                        );

                        if (
                                !cookie.hasExpired()
                        ) {
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

    /*
     * Response body ကို ဖတ်သည်။ Server က gzip
     * ပေးလိုက်လျှင် (Accept-Encoding: identity
     * တောင်းထားသော်လည်း တချို့ server/CDN များ
     * gzip ပေးတတ်သည်) decompress လုပ်မည်။
     */
    private static String readStream(
            HttpURLConnection connection,
            InputStream input
    ) throws Exception {
        if (input == null) {
            return "";
        }

        InputStream decoded = input;

        String contentEncoding =
                connection.getContentEncoding();

        if (
                contentEncoding != null &&
                        contentEncoding.equalsIgnoreCase(
                                "gzip"
                        )
        ) {
            Log.d(TAG, "response is gzip-encoded, decompressing");
            decoded = new GZIPInputStream(input);
        }

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        decoded,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            StringBuilder result =
                    new StringBuilder();

            char[] buffer = new char[8192];
            int read;

            while (
                    (read =
                            reader.read(
                                    buffer,
                                    0,
                                    buffer.length
                            )) != -1
            ) {
                result.append(buffer, 0, read);
            }

            return result.toString();
        }
    }

    /*
     * HTTP error (4xx/5xx) response ၏ body ကို
     * ဖတ်သည်။ Cloudflare challenge စစ်ဆေးရန်
     * သုံးသည်။
     */
    private static String readErrorBody(
            HttpURLConnection connection
    ) {
        try {
            InputStream errorStream =
                    connection.getErrorStream();

            if (errorStream == null) {
                return "";
            }

            return readStream(
                    connection,
                    errorStream
            );
        } catch (Exception ignored) {
            return "";
        }
    }

    /*
     * Cloudflare bot-protection / challenge page
     * ဟုတ်မဟုတ် စစ်သည်။
     */
    private static boolean isCloudflareChallenge(
            String body
    ) {
        if (body == null || body.isEmpty()) {
            return false;
        }

        String lower =
                body.toLowerCase(Locale.US);

        return lower.contains("cloudflare")
                && (
                        lower.contains("challenge")
                                || lower.contains(
                                        "attention required"
                                )
                                || lower.contains(
                                        "just a moment"
                                )
                );
    }

    // ------------------------------------------------------------------
    // Listing parser
    // ------------------------------------------------------------------

    /*
     * Video card anchor:
     * <a href=".../videos/<id>/<hash>/"> ... </a>
     */
    private static final Pattern CARD_ANCHOR =
            Pattern.compile(
                    "<a\\b[^>]*?href\\s*=\\s*\"([^\"]*?/videos/\\d+/[A-Za-z0-9]+/?(?:\\?[^\"]*)?)\"[^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.DOTALL
            );

    private static final Pattern IMG_SRC =
            Pattern.compile(
                    "<img\\b[^>]*?(?:data-src|data-original|src)\\s*=\\s*\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern IMG_ALT =
            Pattern.compile(
                    "\\b(?:alt|title)\\s*=\\s*\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static List<SamusarVideo> parseListing(
            String html
    ) {
        List<SamusarVideo> videos = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return videos;
        }

        /*
         * JSON-escaped slash များ (https:\/\/...) ကို
         * normalize လုပ်မည်။
         */
        String normalized =
                html.replace("\\/", "/");

        Matcher cardMatcher =
                CARD_ANCHOR.matcher(normalized);

        while (cardMatcher.find()) {
            String href = cardMatcher.group(1).trim();
            String body = cardMatcher.group(2);

            if (href.isEmpty()) {
                continue;
            }

            String detailUrl = absoluteUrl(href);

            /*
             * Listing card များသာ — /videos/<id>/<hash>/
             * ပုံစံမဟုတ်လျှင် ကျော်မည်။
             */
            if (
                    !detailUrl.matches(
                            "(?i).*?/videos/\\d+/[A-Za-z0-9]+/?(\\?.*)?$"
                    )
            ) {
                continue;
            }

            String thumbUrl = "";
            String title = "";

            if (body != null) {
                Matcher imgMatcher =
                        IMG_SRC.matcher(body);

                if (imgMatcher.find()) {
                    thumbUrl =
                            absoluteUrl(
                                    imgMatcher
                                            .group(1)
                                            .trim()
                            );
                }

                /*
                 * Title ကို img alt/title attr မှ
                 * အရင်ရှာမည်။
                 */
                Matcher altMatcher =
                        IMG_ALT.matcher(body);

                if (altMatcher.find()) {
                    title =
                            unescapeHtml(
                                    altMatcher
                                            .group(1)
                                            .trim()
                            );
                }

                if (title.isEmpty()) {
                    title =
                            unescapeHtml(
                                    stripTags(body)
                                            .trim()
                            );
                }
            }

            if (title.isEmpty()) {
                title = "အမည်မသိ ဗီဒီယို";
            }

            /*
             * Detail URL တူနေသော card အထပ်များကို
             * ဖယ်မည်။
             */
            boolean duplicate = false;

            for (SamusarVideo existing : videos) {
                if (
                        existing.detailUrl.equals(
                                detailUrl
                        )
                ) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                videos.add(
                        new SamusarVideo(
                                title,
                                thumbUrl,
                                detailUrl
                        )
                );
            }
        }

        return videos;
    }

    // ------------------------------------------------------------------
    // Detail parser — tokenized mp4 URLs
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Detail parser — JS player config (video_url / video_alt_url /
    // video_alt_url2) with tokenized get_file URLs.
    //
    // Example:
    //   video_url: 'https://tw.kyakya.xubi.org/get_file/1/<hash>/.../56894.mp4/?v-acctoken=<b64>',
    //   video_url_text: '480p',
    //   video_alt_url: '.../56894_720p.mp4/?v-acctoken=<b64>',
    //   video_alt_url_text: '720p',
    //   video_alt_url2: '.../56894_1080p.mp4/?v-acctoken=<b64>',
    //   video_alt_url2_text: '1080p',
    // ------------------------------------------------------------------

    /*
     * video_url / video_alt_url / video_alt_url2 နှင့်
     * ၎င်းတို့၏ text label များ။
     */
    private static final Pattern PLAYER_URL =
            Pattern.compile(
                    "\\bvideo(_alt(_url2|_url)?|_url2?)\\s*:\\s*'([^']+)'",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern PLAYER_URL_TEXT =
            Pattern.compile(
                    "\\bvideo(_alt(_url2|_url)?|_url2?)_text\\s*:\\s*'([^']+)'",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern MP4_URL =
            Pattern.compile(
                    "(https?://[^\"'\\s<>\\\\]+?\\.mp4(?:\\?[^\"'\\s<>\\\\]*)?)",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Quality label: "label":"720p" / label="720p" /
     * 720p / 720 စသည်။
     */
    private static final Pattern QUALITY_LABEL =
            Pattern.compile(
                    "(\\d{3,4})\\s*p?",
                    Pattern.CASE_INSENSITIVE
            );

    private static SamusarStream parseDetail(
            String html,
            String detailUrl,
            String cookieHeader
    ) {
        String url480 = "";
        String url720 = "";
        String url1080 = "";

        if (html != null && !html.isEmpty()) {
            String normalized =
                    html.replace("\\/", "/");

            /*
             * 1) JS player config ကို အရင်ရှာမည်
             *    (video_url / video_alt_url / video_alt_url2)။
             *    *_text label မှ quality ကို ယူမည်၊
             *    label မရှိလျှင် key အစဉ်လိုက်
             *    480 / 720 / 1080 အဖြစ် သတ်မှတ်မည်။
             */
            Map<String, String> playerUrls =
                    new LinkedHashMap<>();
            Map<String, String> playerTexts =
                    new LinkedHashMap<>();

            Matcher urlMatcher =
                    PLAYER_URL.matcher(normalized);

            while (urlMatcher.find()) {
                String key =
                        "video" + urlMatcher.group(1);
                String value =
                        urlMatcher.group(3).trim();

                if (
                        !value.isEmpty()
                                && !playerUrls.containsKey(key)
                ) {
                    playerUrls.put(key, value);
                }
            }

            Matcher textMatcher =
                    PLAYER_URL_TEXT.matcher(normalized);

            while (textMatcher.find()) {
                String key =
                        "video" + textMatcher.group(1);
                String value =
                        textMatcher.group(3).trim();

                if (
                        !value.isEmpty()
                                && !playerTexts.containsKey(key)
                ) {
                    playerTexts.put(key, value);
                }
            }

            /*
             * Key → default quality mapping
             * (label မရှိသည့်အခါ သုံးရန်)။
             */
            String[][] keyOrder = {
                    {"video_url", "480"},
                    {"video_alt_url", "720"},
                    {"video_alt_url2", "1080"},
            };

            for (String[] entry : keyOrder) {
                String key = entry[0];
                String streamUrl = playerUrls.get(key);

                if (streamUrl == null || streamUrl.isEmpty()) {
                    continue;
                }

                int quality;

                String label = playerTexts.get(key);

                if (label != null && !label.isEmpty()) {
                    quality = qualityFromText(label);

                    if (quality <= 0) {
                        quality =
                                Integer.parseInt(entry[1]);
                    }
                } else {
                    quality =
                            Integer.parseInt(entry[1]);
                }

                if (
                        quality == 480 &&
                                url480.isEmpty()
                ) {
                    url480 = streamUrl;
                } else if (
                        quality == 720 &&
                                url720.isEmpty()
                ) {
                    url720 = streamUrl;
                } else if (
                        quality == 1080 &&
                                url1080.isEmpty()
                ) {
                    url1080 = streamUrl;
                }

                Log.d(
                        TAG,
                        "parseDetail: " + key + " -> "
                                + quality + "p"
                );
            }

            /*
             * 2) Player config မတွေ့လျှင် generic
             *    .mp4 scan ကို fallback အဖြစ် သုံးမည်။
             */
            if (
                    url480.isEmpty() &&
                            url720.isEmpty() &&
                            url1080.isEmpty()
            ) {
                Matcher matcher =
                        MP4_URL.matcher(normalized);

                Map<String, String> seen =
                        new LinkedHashMap<>();

                while (matcher.find()) {
                    String mp4Url =
                            matcher.group(1).trim();

                    if (seen.containsKey(mp4Url)) {
                        continue;
                    }

                    int start =
                            Math.max(
                                    0,
                                    matcher.start() - 400
                            );
                    int end =
                            Math.min(
                                    normalized.length(),
                                    matcher.end() + 400
                            );

                    String window =
                            normalized.substring(start, end);

                    int quality =
                            detectQuality(mp4Url, window);

                    seen.put(
                            mp4Url,
                            String.valueOf(quality)
                    );

                    if (
                            quality == 480 &&
                                    url480.isEmpty()
                    ) {
                        url480 = mp4Url;
                    } else if (
                            quality == 720 &&
                                    url720.isEmpty()
                    ) {
                        url720 = mp4Url;
                    } else if (
                            quality == 1080 &&
                                    url1080.isEmpty()
                    ) {
                        url1080 = mp4Url;
                    }
                }

                /*
                 * Quality label မတွေ့သော mp4
                 * တစ်ခုတည်းသာ ရှိလျှင် 720p
                 * အဖြစ် သတ်မှတ်မည်။
                 */
                if (
                        url480.isEmpty() &&
                                url720.isEmpty() &&
                                url1080.isEmpty() &&
                                !seen.isEmpty()
                ) {
                    url720 =
                            seen.keySet()
                                    .iterator()
                                    .next();
                }
            }
        }

        return new SamusarStream(
                url480,
                url720,
                url1080,
                cookieHeader == null
                        ? ""
                        : cookieHeader,
                detailUrl
        );
    }

    private static int detectQuality(
            String mp4Url,
            String window
    ) {
        /*
         * 1) URL ထဲမှာ quality ပါလျှင် အရင်ယူမည်
         *    (ဥပမာ .../720p/...mp4 , ..._720p.mp4)
         */
        int fromUrl =
                qualityFromText(mp4Url);

        if (fromUrl > 0) {
            return fromUrl;
        }

        /*
         * 2) URL အနီးအနားက label ကိုရှာမည်
         *    ("label":"720p" စသည်)
         */
        return qualityFromText(window);
    }

    private static int qualityFromText(String text) {
        Matcher matcher =
                QUALITY_LABEL.matcher(text);

        while (matcher.find()) {
            int value;

            try {
                value =
                        Integer.parseInt(
                                matcher.group(1)
                        );
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (
                    value == 480 ||
                            value == 720 ||
                            value == 1080
            ) {
                return value;
            }

            /*
             * 360 / 240 စသော quality နိမ့်များကို
             * 480 bucket ထဲထည့်မည်။
             */
            if (value < 480) {
                return 480;
            }

            /*
             * 1440 / 2160 စသည်များကို
             * 1080 bucket ထဲထည့်မည်။
             */
            if (value > 1080) {
                return 1080;
            }
        }

        return 0;
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

        if (
                trimmed.startsWith("//")
        ) {
            return "https:" + trimmed;
        }

        if (
                trimmed
                        .toLowerCase(Locale.US)
                        .startsWith("http")
        ) {
            return trimmed;
        }

        try {
            return new URL(
                    new URL(getBaseUrl()),
                    trimmed
            ).toString();
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private static String stripTags(String html) {
        String noTags =
                html.replaceAll(
                        "<[^>]+>",
                        " "
                );

        return noTags
                .replaceAll("\\s+", " ")
                .trim();
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

    /*
     * Cookie jar ကို ရှင်းရန် (logout / session
     * ပြန်စရန် လိုအပ်ပါက)။
     */
    public static synchronized void clearCookies() {
        COOKIE_JAR.clear();
    }
}