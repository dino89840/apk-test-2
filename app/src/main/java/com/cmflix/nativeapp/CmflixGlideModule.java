package com.cmflix.nativeapp;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.Headers;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;
import java.util.Locale;

/*
 * Glide AppGlideModule — thumbnail image request များ
 * အတွက် defensive headers။
 *
 * Image CDN များသည် လက်ရှိတွင် header မလိုသော်လည်း,
 * site က နောင် header တောင်းလာလျှင် APK rebuild
 * မလိုစေရန် URL domain အလိုက် header များ
 * ကြိုထည့်ထားသည်:
 *
 * - mmtube.net       → MmtubeClient UA + Referer + Cookie
 * - samusar.com      → SamusarClient UA + Referer + Cookie
 *   (+ proxy host — server-configurable)
 * - mmlovetv.com     → MmlovetvClient UA + Referer + Cookie
 *   img.mmlovetv.com → (ထို header များအတိုင်း)
 * - javtiful domain  → JavtifulClient UA + Referer + Cookie
 * - အခြား            → generic browser UA သာ
 *
 * Cookie များသည် client တစ်ခုချင်းစီ၏ manual
 * cookie jar မှ ရသည် (global CookieManager
 * မထိပါ — ApiClient လုံးဝ မထိခိုက်စေရန်)။
 */
@GlideModule
public final class CmflixGlideModule extends AppGlideModule {

    private static final String TAG = "CmflixGlideModule";

    @Override
    public void registerComponents(
            @NonNull Context context,
            @NonNull Glide glide,
            @NonNull Registry registry
    ) {
        try {
            /*
             * String URL model အတွက် header-adding
             * loader ကို prepend လုပ်သည် — Glide
             * .load("https://...") ခေါ်ဆိုမှုတိုင်း
             * ဒီကနေ ဖြတ်မည်။
             */
            registry.prepend(
                    String.class,
                    InputStream.class,
                    new HeaderedStringLoader.Factory()
            );

            Log.d(TAG, "headered String loader registered");
        } catch (Exception e) {
            Log.w(
                    TAG,
                    "failed to register headered loader: "
                            + e.getMessage()
            );
        }
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }

    /*
     * String URL → header ပါသော GlideUrl → default
     * GlideUrl loader သို့ delegate။
     */
    private static final class HeaderedStringLoader
            implements ModelLoader<String, InputStream> {

        private final ModelLoader<GlideUrl, InputStream>
                concreteLoader;

        HeaderedStringLoader(
                ModelLoader<GlideUrl, InputStream>
                        concreteLoader
        ) {
            this.concreteLoader = concreteLoader;
        }

        @Override
        public boolean handles(
                @NonNull String model
        ) {
            return model.startsWith("http://")
                    || model.startsWith("https://");
        }

        @Nullable
        @Override
        public LoadData<InputStream> buildLoadData(
                @NonNull String model,
                int width,
                int height,
                @NonNull Options options
        ) {
            GlideUrl glideUrl =
                    new GlideUrl(model, buildHeaders(model));

            return concreteLoader.buildLoadData(
                    glideUrl, width, height, options
            );
        }

        static final class Factory
                implements ModelLoaderFactory<
                        String, InputStream> {

            @Override
            @NonNull
            public ModelLoader<String, InputStream> build(
                    @NonNull
                    MultiModelLoaderFactory multiFactory
            ) {
                return new HeaderedStringLoader(
                        multiFactory.build(
                                GlideUrl.class,
                                InputStream.class
                        )
                );
            }

            @Override
            public void teardown() {
            }
        }
    }

    /*
     * URL domain အလိုက် headers တည်ဆောက်သည်။
     */
    private static Headers buildHeaders(String url) {
        if (url == null || url.isEmpty()) {
            return LazyHeaders.DEFAULT;
        }

        String lower =
                url.toLowerCase(Locale.US);

        String userAgent;
        String referer;
        String cookie;

        if (
                lower.contains("mmlovetv.com")
        ) {
            userAgent = MmlovetvClient.USER_AGENT;
            referer = MmlovetvClient.BASE_URL + "/";
            cookie = safeCookie(
                    MmlovetvClient::getCookieHeader
            );
        } else if (
                lower.contains("mmtube.net")
        ) {
            userAgent = MmtubeClient.USER_AGENT;
            referer = MmtubeClient.BASE_URL + "/";
            cookie = safeCookie(
                    MmtubeClient::getCookieHeader
            );
        } else if (
                lower.contains("samusar.com")
                        || isSamusarProxyHost(lower)
        ) {
            userAgent = SamusarClient.USER_AGENT;
            referer = safeBaseUrl(
                    SamusarClient::getBaseUrl
            );
            cookie = safeCookie(
                    SamusarClient::getCookieHeader
            );
        } else if (
                isJavtifulHost(lower)
        ) {
            userAgent = JavtifulClient.USER_AGENT;
            referer = safeBaseUrl(
                    JavtifulClient::getBaseUrl
            );
            cookie = safeCookie(
                    JavtifulClient::getCookieHeader
            );
        } else {
            /*
             * အခြား image host — generic browser
             * UA သာ (defensive)။
             */
            userAgent = MmtubeClient.USER_AGENT;
            referer = "";
            cookie = "";
        }

        LazyHeaders.Builder builder =
                new LazyHeaders.Builder();

        if (!userAgent.isEmpty()) {
            builder.addHeader("User-Agent", userAgent);
        }

        if (!referer.isEmpty()) {
            builder.addHeader("Referer", referer);
        }

        if (!cookie.isEmpty()) {
            builder.addHeader("Cookie", cookie);
        }

        return builder.build();
    }

    private interface StringSupplier {
        String get();
    }

    private static String safeCookie(
            StringSupplier supplier
    ) {
        try {
            String value = supplier.get();

            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safeBaseUrl(
            StringSupplier supplier
    ) {
        try {
            String value = supplier.get();

            if (value == null || value.isEmpty()) {
                return "";
            }

            return value.endsWith("/")
                    ? value
                    : value + "/";
        } catch (Exception ignored) {
            return "";
        }
    }

    /*
     * Samusar proxy host (server-configurable) —
     * D1 app_samusar_proxy / hardcoded default။
     */
    private static boolean isSamusarProxyHost(
            String lowerUrl
    ) {
        try {
            String proxy = SamusarClient.getBaseUrl();

            if (proxy == null || proxy.isEmpty()) {
                return false;
            }

            String host =
                    proxy.toLowerCase(Locale.US)
                            .replace("https://", "")
                            .replace("http://", "")
                            .split("/")[0];

            return !host.isEmpty()
                    && lowerUrl.contains(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    /*
     * Javtiful host (server-configurable BASE_URL)။
     */
    private static boolean isJavtifulHost(
            String lowerUrl
    ) {
        try {
            String base = JavtifulClient.getBaseUrl();

            if (base == null || base.isEmpty()) {
                return false;
            }

            String host =
                    base.toLowerCase(Locale.US)
                            .replace("https://", "")
                            .replace("http://", "")
                            .split("/")[0];

            return !host.isEmpty()
                    && lowerUrl.contains(host);
        } catch (Exception ignored) {
            return false;
        }
    }
}
