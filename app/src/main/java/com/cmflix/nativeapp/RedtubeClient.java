package com.cmflix.nativeapp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

/*
 * redtube.com scraper — "Free Porn" category (18+ hub).
 *
 * - Listing: GET https://www.redtube.com/newest?page=N
 *   (page 1 = no ?page param). ~36 cards per page.
 *   Cards: <li id="video_list{id}" ...> or id="rt_{id}"
 *   Title: <a href="/{id}" title="...">
 *   Thumb: <img data-o_thumb="..."> (or data-src)
 *   Duration: <span class="... tm_video_duration">
 * - Search: GET https://www.redtube.com/?search={q}&page=N
 *   (same card HTML as listing).
 * - Detail: GET https://www.redtube.com/{id}
 *   → mediaDefinition JSON: {"format":"mp4","videoUrl":"/media/mp4?s={token}"}
 * - Stream: GET https://www.redtube.com/media/mp4?s={token}
 *   → JSON array: [{format, quality (1080/720/480/240), videoUrl (direct MP4)}]
 *
 * Direct connection (no proxy). Defensive headers on every
 * request: browser-like User-Agent + Referer + cookie jar.
 * Stream URLs are NOT cached — always fresh resolve
 * (tokens carry expiry).
 *
 * UI label is "Free Porn" — the word "redtube" never
 * appears in user-visible strings.
 */
public final class RedtubeClient {

    private static final String TAG = "RedtubeClient";

    public static final String BASE_URL =
            "https://www.redtube.com";

    private static final String LIST_PATH = "/newest";

    /*
     * Redtube video stable ID prefix (for LocalStore
     * resume keys and PlayerActivity progress).
     */
    public static final String ID_PREFIX = "redtube:";

    public static String videoId(String detailUrl) {
        String url = detailUrl == null ? "" : detailUrl.trim();
        if (url.startsWith(ID_PREFIX)) {
            return url;
        }
        return ID_PREFIX + url;
    }

    public static String detailUrlFromId(String videoId) {
        String id = videoId == null ? "" : videoId.trim();
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
    private static final int READ_TIMEOUT_MS = 25000;

    private static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool();

    /*
     * Redtube-only in-memory cookie jar (manual —
     * global CookieManager untouched so ApiClient
     * requests are unaffected).
     */
    private static final List<HttpCookie> COOKIE_JAR =
            new ArrayList<>();

    public static synchronized String getCookieHeader() {
        return currentCookieHeader();
    }

    public static synchronized void clearCookies() {
        COOKIE_JAR.clear();
    }

    private RedtubeClient() {
    }

    public static final class RedtubeVideo {
        public final String title;
        public final String thumbUrl;
        public final String detailUrl;
        public final String duration;

        public RedtubeVideo(
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

    public static final class RedtubeStream {
        public final String url1080;
        public final String url720;
        public final String url480;
        public final String url240;
        public final String cookieHeader;
        public final String referer;

        public RedtubeStream(
                String url1080,
                String url720,
                String url480,
                String url240,
                String cookieHeader,
                String referer
        ) {
            this.url1080 = url1080;
            this.url720 = url720;
            this.url480 = url480;
            this.url240 = url240;
            this.cookieHeader = cookieHeader;
            this.referer = referer;
        }

        /*
         * Default 720p, fallback 480p → 1080p → 240p.
         */
        public String bestUrl() {
            if (url720 != null && !url720.isEmpty()) {
                return url720;
            }
            if (url480 != null && !url480.isEmpty()) {
                return url480;
            }
            if (url1080 != null && !url1080.isEmpty()) {
                return url1080;
            }
            return url240 == null ? "" : url240;
        }

        public boolean hasStream() {
            return !bestUrl().isEmpty();
        }
    }

    public interface PageCallback {
        void onResult(
                List<RedtubeVideo> videos,
                boolean hasMore
        );

        void onError(Exception error);
    }

    public interface StreamCallback {
        void onResult(RedtubeStream stream);

        void onError(Exception error);
    }

    /*
     * Listing page N (1-based). Page 1 = /newest
     * (no ?page param), page N = /newest?page=N.
     */
    public static void fetchPage(
            int page,
            PageCallback callback
    ) {
        final int safePage = Math.max(1, page);

        EXECUTOR.execute(() -> {
            try {
                String url = BASE_URL + LIST_PATH
                        + (safePage > 1
                                ? "?page=" + safePage
                                : "");

                String html = get(url, BASE_URL + "/");
                List<RedtubeVideo> videos =
                        parseListing(html);

                callback.onResult(
                        videos, !videos.isEmpty());
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    /*
     * Search — GET /?search={q}&page=N
     * (same card HTML as listing).
     */
    public static void searchVideos(
            String query,
            int page,
            PageCallback callback
    ) {
        String q = query == null ? "" : query.trim();

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

        final String url = BASE_URL
                + "/?search=" + encoded
                + (safePage > 1
                        ? "&page=" + safePage
                        : "");

        EXECUTOR.execute(() -> {
            try {
                String html = get(url, BASE_URL + "/");
                List<RedtubeVideo> videos =
                        parseListing(html);

                callback.onResult(
                        videos, !videos.isEmpty());
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    /*
     * Detail page → mediaDefinition token →
     * /media/mp4?s={token} JSON → quality URLs.
     * Stream URL is never cached (token expiry).
     */
    public static void resolveStream(
            String detailUrl,
            StreamCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                if (detailUrl == null
                        || detailUrl.trim().isEmpty()) {
                    throw new IllegalStateException(
                            "Detail URL မရှိပါ။"
                    );
                }

                String pageUrl = detailUrl.trim();
                String html = get(pageUrl, BASE_URL + "/");

                String token = extractMediaToken(html);

                if (token == null || token.isEmpty()) {
                    throw new IllegalStateException(
                            "Video link ရှာမတွေ့ပါ။"
                    );
                }

                String mediaUrl =
                        BASE_URL + "/media/mp4?s=" + token;
                String json = get(mediaUrl, pageUrl);

                RedtubeStream stream = parseMediaJson(
                        json, pageUrl,
                        currentCookieHeader());

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
    // Listing parser
    // ------------------------------------------------------------------

    /*
     * Cards: <li id="video_list{id}" ...> (listing)
     *     or <li id="rt_{id}" ...> (search)
     * Title: <a href="/{id}" title="...">
     * Thumb: <img data-o_thumb="..."> (fallback data-src)
     * Duration: tm_video_duration span
     */
    private static final Pattern CARD_ID =
            Pattern.compile(
                    "<li\\b[^>]*?\\bid\\s*=\\s*\""
                            + "(?:video_list|rt_)(\\d+)\"",
                    Pattern.CASE_INSENSITIVE
            );

    private static List<RedtubeVideo> parseListing(
            String html
    ) {
        List<RedtubeVideo> videos = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return videos;
        }

        Matcher idMatcher = CARD_ID.matcher(html);

        while (idMatcher.find()) {
            String videoId = idMatcher.group(1);
            int cardStart = idMatcher.start();
            int cardEnd = html.indexOf("</li>", cardStart);

            if (cardEnd < 0 || cardEnd - cardStart > 20000) {
                cardEnd = Math.min(
                        cardStart + 20000, html.length());
            }

            String card =
                    html.substring(cardStart, cardEnd);
            String detailUrl =
                    BASE_URL + "/" + videoId;

            String title = extractAttr(
                    card,
                    "<a\\b[^>]*?href\\s*=\\s*\"/"
                            + videoId + "\"[^>]*?"
                            + "title\\s*=\\s*\"",
                    "\""
            );

            if (title.isEmpty()) {
                title = extractAttr(
                        card,
                        "title\\s*=\\s*\"",
                        "\""
                );
            }

            title = unescapeHtml(title).trim();

            if (title.isEmpty()) {
                title = "အမည်မသိ ဗီဒီယို";
            }

            String thumb = extractAttr(
                    card, "data-o_thumb\\s*=\\s*\"", "\"");

            if (thumb.isEmpty()) {
                thumb = extractAttr(
                        card, "data-src\\s*=\\s*\"", "\"");
            }

            thumb = thumb.replace("&amp;", "&").trim();

            String duration = "";
            Matcher durMatcher = Pattern.compile(
                    "tm_video_duration\"[^>]*>([^<]+)<"
            ).matcher(card);

            if (durMatcher.find()) {
                duration = durMatcher.group(1).trim();
            }

            boolean duplicate = false;
            for (RedtubeVideo existing : videos) {
                if (existing.detailUrl.equals(detailUrl)) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                videos.add(new RedtubeVideo(
                        title, thumb, detailUrl, duration));
            }
        }

        return videos;
    }

    private static String extractAttr(
            String html,
            String prefixRegex,
            String terminator
    ) {
        Matcher m = Pattern.compile(
                prefixRegex + "([^" + terminator + "]*)"
                        + Pattern.quote(terminator),
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(html);

        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Detail parser — mediaDefinition token
    // ------------------------------------------------------------------

    /*
     * mediaDefinition: [{"format":"hls",...},
     *   {"format":"mp4","videoUrl":"\/media\/mp4?s={token}"}]
     * Returns the {token} part.
     */
    private static String extractMediaToken(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        // Escaped form: \/media\/mp4?s=TOKEN
        Matcher m = Pattern.compile(
                "media\\\\/mp4\\?s=([A-Za-z0-9_\\-=]+)"
        ).matcher(html);

        if (m.find()) {
            return m.group(1);
        }

        // Unescaped form: /media/mp4?s=TOKEN
        m = Pattern.compile(
                "/media/mp4\\?s=([A-Za-z0-9_\\-=]+)"
        ).matcher(html);

        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    /*
     * /media/mp4?s={token} returns JSON:
     * [{"format":"mp4","quality":"1080","videoUrl":"https://...mp4?..."}, ...]
     */
    private static RedtubeStream parseMediaJson(
            String json,
            String referer,
            String cookieHeader
    ) {
        String url1080 = "";
        String url720 = "";
        String url480 = "";
        String url240 = "";

        try {
            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);

                if (!"mp4".equalsIgnoreCase(
                        obj.optString("format"))) {
                    continue;
                }

                String quality =
                        obj.optString("quality", "");
                String videoUrl =
                        obj.optString("videoUrl", "")
                                .replace("\\/", "/")
                                .trim();

                if (videoUrl.isEmpty()) {
                    continue;
                }

                if (quality.contains("1080")
                        && url1080.isEmpty()) {
                    url1080 = videoUrl;
                } else if (quality.contains("720")
                        && url720.isEmpty()) {
                    url720 = videoUrl;
                } else if (quality.contains("480")
                        && url480.isEmpty()) {
                    url480 = videoUrl;
                } else if (quality.contains("240")
                        && url240.isEmpty()) {
                    url240 = videoUrl;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "parseMediaJson failed: "
                    + e.getMessage());
        }

        return new RedtubeStream(
                url1080, url720, url480, url240,
                cookieHeader == null ? "" : cookieHeader,
                referer
        );
    }

    // ------------------------------------------------------------------
    // HTTP layer — UA + Referer + cookie jar
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

            connection.setConnectTimeout(
                    CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty(
                    "User-Agent", USER_AGENT);
            connection.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,"
                            + "application/xml;q=0.9,*/*;q=0.8"
            );
            connection.setRequestProperty(
                    "Accept-Language", "en-US,en;q=0.9");

            if (referer != null && !referer.isEmpty()) {
                connection.setRequestProperty(
                        "Referer", referer);
            }

            String cookieHeader = currentCookieHeader();

            if (!cookieHeader.isEmpty()) {
                connection.setRequestProperty(
                        "Cookie", cookieHeader);
            }

            int status = connection.getResponseCode();

            storeCookies(connection);

            Log.d(TAG, "GET " + url + " -> " + status);

            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "Request failed: " + status);
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

        for (Map.Entry<String, List<String>> entry :
                headers.entrySet()) {
            if (entry.getKey() == null
                    || !entry.getKey().equalsIgnoreCase(
                            "Set-Cookie")) {
                continue;
            }

            for (String value : entry.getValue()) {
                try {
                    List<HttpCookie> parsed =
                            HttpCookie.parse(value);

                    for (HttpCookie cookie : parsed) {
                        final String name =
                                cookie.getName();
                        COOKIE_JAR.removeIf(
                                existing -> existing
                                        .getName()
                                        .equals(name));

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

        COOKIE_JAR.removeIf(cookie -> {
            try {
                return cookie.hasExpired();
            } catch (Exception ignored) {
                return true;
            }
        });

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
        InputStream decoded = input;

        if (encoding != null
                && encoding.equalsIgnoreCase("gzip")) {
            decoded = new GZIPInputStream(input);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        decoded, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[8192];
            int read;

            while ((read = reader.read(
                    buffer, 0, buffer.length)) != -1) {
                result.append(buffer, 0, read);
            }

            return result.toString();
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
