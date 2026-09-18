package com.cmflix.nativeapp;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import java.net.HttpURLConnection;
import java.net.URL;



import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.json.JSONArray;
import org.json.JSONObject;

public class DetailActivity extends AppCompatActivity {

    private ImageView backdrop;
private ImageView poster;

private TextView title;
private TextView detailVipBadge;

private TextView meta;
private TextView overview;
private TextView episodesLabel;


    private Button playButton;
    private Button downloadButton;
    private ImageButton favoriteButton;
    private Button shareButton;
    private Button telegramButton;

    private LinearLayout genresContainer;
    private LinearLayout episodesContainer;

    private View genresScroll;

    private String firstVideoUrl = "";
    private String firstVideoType = "auto";
    private String firstEpisodeId = "";

    private String titleId = "";
    private String titleCategory = "";
    private String currentTitleName = "";

    private boolean isFavorite = false;
    private boolean favoriteLoading = false;
    private boolean addFavoriteAfterLogin = false;

    private boolean loginRequestedForPlayback = false;


    private final ActivityResultLauncher<Intent> authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        updateFavoriteText();

                        if (result.getResultCode() != RESULT_OK) {
    addFavoriteAfterLogin = false;
    loginRequestedForPlayback = false;
    return;
}


                        /*
                         * Favorite ထည့်ဖို့ Login ဝင်ထားတာဆိုရင်
                         * checkFavorite() ကို အရင်မခေါ်ပါ။
                         *
                         * checkFavorite() က favoriteLoading = true
                         * လုပ်တာကြောင့် setFavorite() မလုပ်ဖြစ်နိုင်ပါ။
                         */
                        if (addFavoriteAfterLogin) {
                            addFavoriteAfterLogin = false;
                            setFavorite(true);
                        } else {
                            checkFavorite();
                        }

                        if (loginRequestedForPlayback) {
    /*
     * Playback အတွက် Login ဝင်ခဲ့ခြင်း state ကိုသာ
     * reset လုပ်မယ်။
     *
     * AuthActivity က "Login အောင်မြင်ပါသည်။"
     * Toast ပြပြီးသားဖြစ်သောကြောင့်
     * "PLAY ကို ထပ်နှိပ်ပါ" Toast မပြတော့ပါ။
     */
    loginRequestedForPlayback = false;
}

                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApiClient.initialize(this);
        setContentView(R.layout.activity_detail);

        bindViews();
        setupClickListeners();

        String slug = getIntent().getStringExtra("slug");

        if (slug == null || slug.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Movie slug မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        playButton.setVisibility(View.GONE);

        shareButton.setEnabled(false);
        shareButton.setAlpha(0.5f);

        updateFavoriteText();

        loadTitle(slug);
    }

    private void bindViews() {
    backdrop = findViewById(R.id.backdrop);
    poster = findViewById(R.id.detailPoster);

    title = findViewById(R.id.detailTitle);
    detailVipBadge = findViewById(
            R.id.detailVipBadge
    );

    meta = findViewById(R.id.detailMeta);
    overview = findViewById(R.id.detailOverview);

    episodesLabel = findViewById(R.id.episodesLabel);


    playButton = findViewById(R.id.playButton);
    downloadButton = findViewById(R.id.downloadButton);
    favoriteButton = findViewById(R.id.favoriteButton);
    shareButton = findViewById(R.id.shareButton);
    telegramButton = findViewById(R.id.telegramButton);

    genresScroll = findViewById(R.id.genresScroll);

    genresContainer = findViewById(R.id.genresContainer);
    episodesContainer = findViewById(R.id.episodesContainer);
}



    private void setupClickListeners() {
    TextView backButton =
            findViewById(R.id.detailBackButton);

    backButton.setOnClickListener(
            view -> finish()
    );

    favoriteButton.setOnClickListener(view -> {
        if (titleId.isEmpty() || favoriteLoading) {
            return;
        }

        if (!SessionManager.isLoggedIn()) {
            addFavoriteAfterLogin = true;

            authLauncher.launch(
                    new Intent(
                            DetailActivity.this,
                            AuthActivity.class
                    )
            );

            return;
        }

        setFavorite(!isFavorite);
    });

    shareButton.setOnClickListener(
            view -> shareCurrentTitle()
    );

    telegramButton.setOnClickListener(
            view -> openTelegramContact()
    );

    playButton.setOnClickListener(view ->
            playVideo(
                    firstVideoUrl,
                    firstVideoType,
                    firstEpisodeId
            )
    );

    downloadButton.setOnClickListener(
            view -> requestDownload()
    );
}

    private void requestDownload() {
        boolean downloadableCategory =
                "series".equalsIgnoreCase(
                        titleCategory
                ) ||
                "lugyi".equalsIgnoreCase(
                        titleCategory
                );

        if (!downloadableCategory) {
    Toast.makeText(
            this,
            "ဒီဇာတ်ကားမှာ Download မရပါ။",
            Toast.LENGTH_SHORT
    ).show();

    return;
}

if (!NetworkUtils.isOnline(this)) {
    Toast.makeText(
            this,
            "Download link ထုတ်ရန် အင်တာနက်ချိတ်ဆက်ပါ။",
            Toast.LENGTH_LONG
    ).show();

    return;
}

if (!SessionManager.isLoggedIn()) {

            authLauncher.launch(
                    new Intent(
                            this,
                            AuthActivity.class
                    )
            );

            return;
        }

        if (
                SessionManager.getVipUntil()
                        <= System.currentTimeMillis()
        ) {
            PremiumDialog.show(this);
            return;
        }

        if (titleId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Title ID မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        downloadButton.setEnabled(false);
        downloadButton.setAlpha(0.6f);
        downloadButton.setText(
                "Preparing download…"
        );

        JSONObject body = new JSONObject();

        try {
            body.put(
                    "titleId",
                    titleId
            );
        } catch (Exception error) {
            restoreDownloadButton();
            return;
        }

        ApiClient.post(
                "download",
                body,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(
                            JSONObject json
                    ) {
                        String gatewayUrl =
                                json.optString(
                                        "downloadUrl",
                                        ""
                                ).trim();

                        String fileName =
                                json.optString(
                                        "fileName",
                                        buildLocalFileName(
                                                currentTitleName,
                                                gatewayUrl
                                        )
                                );

                        if (gatewayUrl.isEmpty()) {
                            runOnUiThread(() -> {
                                restoreDownloadButton();

                                Toast.makeText(
                                        DetailActivity.this,
                                        "Download link မရပါ။",
                                        Toast.LENGTH_LONG
                                ).show();
                            });

                            return;
                        }

                        /*
 * Download API ကပြန်ပေးသော gateway/signed URL ကို
 * redirect လိုက်ပြီး final URL ကိုရယူမယ်။
 *
 * Resolve မအောင်မြင်လျှင် gateway URL သို့မဟုတ်
 * video_url ကို fallback မလုပ်ဘဲ fail closed လုပ်မယ်။
 */
final String finalDownloadUrl;

try {
    String resolvedUrl =
            resolveFinalDownloadUrl(
                    gatewayUrl
            );

    if (
            resolvedUrl == null ||
            resolvedUrl.trim().isEmpty() ||
            !isSafeDownloadUrl(
                    resolvedUrl.trim()
            )
    ) {
        throw new SecurityException(
                "Invalid download URL"
        );
    }

    finalDownloadUrl =
            resolvedUrl.trim();

} catch (Exception error) {
    runOnUiThread(() -> {
        restoreDownloadButton();

        Toast.makeText(
                DetailActivity.this,
                NetworkUtils.isOnline(
                        DetailActivity.this
                )
                        ? "Download link ပြင်ဆင်၍မရပါ။ ပြန်စမ်းပါ။"
                        : "အင်တာနက်ချိတ်ဆက်မှု ပြတ်တောက်သွားပါသည်။",
                Toast.LENGTH_LONG
        ).show();
    });

    return;
}

final String finalFileName =
        fileName == null ||
        fileName.trim().isEmpty()
                ? buildLocalFileName(
                        currentTitleName,
                        finalDownloadUrl
                )
                : fileName.trim();

runOnUiThread(() -> {
    restoreDownloadButton();

    showDownloadChooser(
            finalDownloadUrl,
            finalFileName
    );
});

                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        runOnUiThread(() -> {
                            restoreDownloadButton();

                            String message =
                                    safeMessage(error);

                            String lower =
                                    message.toLowerCase(
                                            java.util.Locale.US
                                    );

                            if (
                                    lower.contains("vip") ||
                                    lower.contains("premium") ||
                                    lower.contains("403") ||
                                    lower.contains("expired")
                            ) {
                                SessionManager.saveVipState(
        0L,
        0,
        "free"
);


                                PremiumDialog.show(
                                        DetailActivity.this
                                );

                                return;
                            }

                            Toast.makeText(
                                    DetailActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }
    private String resolveFinalDownloadUrl(
            String originalUrl
    ) throws Exception {
        if (
                originalUrl == null ||
                originalUrl.trim().isEmpty()
        ) {
            return "";
        }

        String currentUrl =
        originalUrl.trim();

if (!isSafeDownloadUrl(currentUrl)) {
    throw new SecurityException(
            "Unsafe download URL"
    );
}


        /*
         * Redirect loop မဖြစ်အောင်
         * အများဆုံး ၈ ဆင့်ပဲလိုက်မယ်။
         */
        for (int redirectCount = 0;
             redirectCount < 8;
             redirectCount++) {

            HttpURLConnection connection = null;

            try {
                connection =
                        (HttpURLConnection)
                                new URL(
                                        currentUrl
                                ).openConnection();

                /*
                 * Java က အလိုအလျောက် redirect လိုက်မသွားစေဘဲ
                 * Location header ကို ကိုယ်တိုင်ဖတ်မယ်။
                 */
                connection.setInstanceFollowRedirects(
                        false
                );

                connection.setRequestMethod(
                        "GET"
                );

                connection.setConnectTimeout(
                        15000
                );

                connection.setReadTimeout(
                        15000
                );

                connection.setUseCaches(
                        false
                );

                connection.setRequestProperty(
                        "Accept",
                        "*/*"
                );

                connection.setRequestProperty(
                        "User-Agent",
                        "CMFLIX-Native-Android"
                );

                /*
                 * Video file တစ်ခုလုံးမဆွဲဘဲ
                 * header/redirect သိဖို့ ပထမ byte ပဲတောင်းမယ်။
                 */
                connection.setRequestProperty(
                        "Range",
                        "bytes=0-0"
                );

                int statusCode =
                        connection.getResponseCode();

                boolean redirected =
                        statusCode ==
                                HttpURLConnection.HTTP_MOVED_PERM ||
                        statusCode ==
                                HttpURLConnection.HTTP_MOVED_TEMP ||
                        statusCode ==
                                HttpURLConnection.HTTP_SEE_OTHER ||
                        statusCode == 307 ||
                        statusCode == 308;

                if (!redirected) {
                    /*
                     * 200/206 ရောက်ပြီဆိုရင်
                     * လက်ရှိ URL က final URL ဖြစ်တယ်။
                     */
                    return currentUrl;
                }

                String location =
                        connection.getHeaderField(
                                "Location"
                        );

                if (
                        location == null ||
                        location.trim().isEmpty()
                ) {
                    return currentUrl;
                }

                /*
                 * Location က relative URL ဖြစ်နေရင်လည်း
                 * absolute URL ပြောင်းပေးမယ်။
                 */
                URL base =
                        new URL(
                                currentUrl
                        );

                String redirectedUrl =
        new URL(
                base,
                location.trim()
        ).toString();

if (!isSafeDownloadUrl(redirectedUrl)) {
    throw new SecurityException(
            "Unsafe redirect URL"
    );
}

currentUrl = redirectedUrl;


            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        return currentUrl;
    }

    private void restoreDownloadButton() {
        downloadButton.setEnabled(true);
        downloadButton.setAlpha(1f);
        downloadButton.setText(
                "↓  DOWNLOAD"
        );
    }

    private void showDownloadChooser(
        String url,
        String fileName
) {
    boolean admInstalled =
            isPackageInstalled(
                    "com.dv.adm"
            ) ||
            isPackageInstalled(
                    "com.dv.adm.pay"
            );

    Dialog dialog =
            new Dialog(this);

    dialog.setContentView(
            R.layout.dialog_download_chooser
    );

    dialog.setCancelable(true);
    dialog.setCanceledOnTouchOutside(true);

    android.view.Window window =
            dialog.getWindow();

    if (window != null) {
        window.setBackgroundDrawable(
                new ColorDrawable(
                        Color.TRANSPARENT
                )
        );

        window.addFlags(
                android.view.WindowManager
                        .LayoutParams
                        .FLAG_DIM_BEHIND
        );

        android.view.WindowManager.LayoutParams attributes =
                window.getAttributes();

        attributes.dimAmount = 0.82f;
        window.setAttributes(attributes);
    }

    TextView browserButton =
            dialog.findViewById(
                    R.id.downloadBrowserButton
            );

    TextView admButton =
            dialog.findViewById(
                    R.id.downloadAdmButton
            );

    TextView cancelButton =
            dialog.findViewById(
                    R.id.downloadCancelButton
            );

    String safeMovieTitle =
            currentTitleName == null ||
            currentTitleName.trim().isEmpty()
                    ? "CMFLIX Movie"
                    : currentTitleName.trim();

    String safeFileName =
            fileName == null ||
            fileName.trim().isEmpty()
                    ? buildLocalFileName(
                            safeMovieTitle,
                            url
                    )
                    : fileName.trim();

    /*
     * ADM မရှိလျှင် အသုံးမဝင်သော disabled box
     * မပြဘဲ ADM button ကို ဖျောက်ထားမည်။
     */
    admButton.setVisibility(
            admInstalled
                    ? View.VISIBLE
                    : View.GONE
    );

    browserButton.setOnClickListener(view -> {
        dialog.dismiss();

        openBrowserDownload(
                url,
                safeFileName
        );
    });

    admButton.setOnClickListener(view -> {
        dialog.dismiss();

        openAdmDownload(
                url,
                safeFileName
        );
    });

    cancelButton.setOnClickListener(
            view -> dialog.dismiss()
    );

    dialog.show();

    if (window != null) {
        int screenWidth =
                getResources()
                        .getDisplayMetrics()
                        .widthPixels;

        int dialogWidth =
                Math.min(
                        (int) (
                                screenWidth * 0.90f
                        ),
                        dp(420)
                );

        window.setLayout(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
}


    private void openBrowserDownload(
            String url,
            String fileName
    ) {
        try {
            Intent intent =
                    createDownloadIntent(
                            url,
                            fileName
                    );

            startActivity(
        Intent.createChooser(
                intent,
                "Browser ရွေးပါ"
        )
);

/*
 * External browser/downloader chooser ကို
 * အောင်မြင်စွာဖွင့်ပြီးမှ local history မှတ်မယ်။
 */
LocalStore.recordDownload(titleId);

        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "Download link ဖွင့်၍မရပါ။",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void openAdmDownload(
            String url,
            String fileName
    ) {
        String[] packages = {
                "com.dv.adm",
                "com.dv.adm.pay"
        };

        for (String packageName : packages) {
            if (!isPackageInstalled(packageName)) {
                continue;
            }

            /*
             * ပထမဆုံး ADM ရဲ့ VIEW intent-filter နဲ့ဖွင့်မယ်။
             */
            try {
                Intent intent =
                        createDownloadIntent(
                                url,
                                fileName
                        );

                intent.setPackage(
                        packageName
                );

                startActivity(intent);
LocalStore.recordDownload(titleId);
return;


            } catch (Exception ignored) {
                /*
                 * ADM version တချို့မှာ VIEW intent-filter
                 * မတူနိုင်လို့ editor activity ကို fallback စမ်းမယ်။
                 */
            }

            try {
                Intent fallbackIntent =
                        createDownloadIntent(
                                url,
                                fileName
                        );

                fallbackIntent.setClassName(
                        packageName,
                        "com.dv.get.AEditor"
                );

                startActivity(fallbackIntent);
LocalStore.recordDownload(titleId);
return;


            } catch (Exception ignored) {
                // နောက် ADM package ကိုဆက်စမ်းမယ်။
            }
        }

        Toast.makeText(
                this,
                "ADM ကိုဖွင့်၍မရပါ။ ADM ကို update လုပ်ပြီး ပြန်စမ်းပါ။",
                Toast.LENGTH_LONG
        ).show();
    }

    private Intent createDownloadIntent(
            String url,
            String fileName
    ) {
        Uri uri = Uri.parse(url);

        Intent intent =
                new Intent(
                        Intent.ACTION_VIEW,
                        uri
                );

        intent.addCategory(
                Intent.CATEGORY_BROWSABLE
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
        );

        intent.putExtra(
                Intent.EXTRA_TITLE,
                fileName
        );

        intent.putExtra(
                "android.intent.extra.TITLE",
                fileName
        );

        intent.putExtra(
                "suggested_filename",
                fileName
        );

        intent.setClipData(
                ClipData.newPlainText(
                        fileName,
                        url
                )
        );

        return intent;
    }

    private boolean isPackageInstalled(
            String packageName
    ) {
        try {
            getPackageManager()
                    .getPackageInfo(
                            packageName,
                            0
                    );

            return true;
        } catch (
                PackageManager.NameNotFoundException error
        ) {
            return false;
        }
    }

    private String buildLocalFileName(
            String movieTitle,
            String url
    ) {
        String safeTitle =
                movieTitle == null
                        ? "movie"
                        : movieTitle
                        .replaceAll(
                                "[\\\\/:*?\"<>|]",
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (safeTitle.isEmpty()) {
            safeTitle = "movie";
        }

        String extension = ".mp4";

        try {
            String path =
                    Uri.parse(url)
                            .getLastPathSegment();

            if (
                    path != null &&
                    path.matches(
                            ".*\\.[A-Za-z0-9]{2,6}$"
                    )
            ) {
                extension =
                        path.substring(
                                path.lastIndexOf(".")
                        );
            }
        } catch (Exception ignored) {
        }

        return safeTitle + extension;
    }


    private void loadTitle(String slug) {
        ApiClient.getCached(
        "titles/" +
                ApiClient.encode(slug),
        6L * 60L * 60L * 1000L,
        new ApiClient.Callback() {


                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() ->
                                bind(json.optJSONObject("item"))
                        );
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            overview.setText(
                                    "Load failed: " + safeMessage(error)
                            );

                            playButton.setVisibility(View.GONE);
                            shareButton.setEnabled(false);
                            shareButton.setAlpha(0.5f);
                        });
                    }
                }
        );
    }

    private void bind(JSONObject item) {
        if (item == null) {
            overview.setText("Movie not found.");
            playButton.setVisibility(View.GONE);
            return;
        }

        titleId = item.optString("id", "");

        titleCategory = item.optString(
        "category",
        ""
);

String currentTitle = item.optString(
        "title",
        ""
);

currentTitleName = currentTitle;
title.setText(currentTitle);

/*
 * TMDB metadata သုံးထားသော Movies category မှာသာ
 * year, rating နှင့် genres ကိုပြပါမယ်။
 *
 * Free 18+ (series) နှင့် VIP 18+ (lugyi) မှာ
 * API က null/0 တန်ဖိုးတွေ ပြန်လာနိုင်သောကြောင့်
 * metadata အားလုံးကိုဖျောက်ထားပါမယ်။
 */
boolean movieTitle =
        "movies".equalsIgnoreCase(
                titleCategory
        ) ||
        "movie".equalsIgnoreCase(
                titleCategory
        );

boolean vipTitle =
        "lugyi".equalsIgnoreCase(
                titleCategory
        );

/*
 * VIP badge ကို title ဘေးမှာမပြတော့ဘဲ
 * title အောက် metadata row ထဲမှာပြမယ်။
 */
detailVipBadge.setVisibility(
        vipTitle
                ? View.VISIBLE
                : View.GONE
);

/*
 * Detail API ကရပြီးသား item ကို device ထဲမှာပဲ
 * Recently Viewed အဖြစ်သိမ်းမယ်။
 */
LocalStore.rememberRecentlyViewed(item);

String year =
        cleanMetadataValue(
                item,
                "year"
        );

String rating =
        cleanMetadataValue(
                item,
                "rating"
        );

String genres =
        cleanMetadataValue(
                item,
                "genres"
        );

/*
 * 0, 0.0 rating တွေကို valid rating အဖြစ်မပြပါ။
 */
if (isZeroMetadataValue(rating)) {
    rating = "";
}

/*
 * 0 year ကိုလည်း UI မှာမပြပါ။
 */
if (isZeroMetadataValue(year)) {
    year = "";
}

StringBuilder metaText =
        new StringBuilder();

if (movieTitle && !year.isEmpty()) {
    metaText.append(year);
}

if (movieTitle && !rating.isEmpty()) {
    if (metaText.length() > 0) {
        metaText.append("  •  ");
    }

    metaText
            .append("★ ")
            .append(rating);
}

String finalMetaText =
        metaText.toString();

meta.setText(finalMetaText);

meta.setVisibility(
        movieTitle &&
        !finalMetaText.isEmpty()
                ? View.VISIBLE
                : View.GONE
);

/*
 * Movies category မှာပဲ genres ပြပါမယ်။
 * series/lugyi ဖြစ်လျှင် empty string ပို့ပြီး
 * label နဲ့ scroll container နှစ်ခုလုံးဖျောက်မယ်။
 */
bindGenres(
        movieTitle
                ? genres
                : ""
);


        shareButton.setEnabled(!currentTitle.trim().isEmpty());
        shareButton.setAlpha(
                currentTitle.trim().isEmpty()
                        ? 0.5f
                        : 1f
        );

        overview.setText(
                item.optString(
                        "overview",
                        "ဇာတ်လမ်းအကျဉ်း မရှိသေးပါ။"
                )
        );

        String backdropUrl =
        item.optString(
                "backdrop_url",
                ""
        ).trim();

String posterUrl =
        item.optString(
                "poster_url",
                ""
        ).trim();

/*
 * Backdrop URL မရှိလျှင် poster URL ကို
 * backdrop အဖြစ် fallback သုံးပါမယ်။
 */
String backdropImageUrl =
        backdropUrl.isEmpty()
                ? posterUrl
                : backdropUrl;

/*
 * Backdrop ကို dark placeholder ကနေ
 * 650ms အတွင်း နူးညံ့စွာပေါ်လာစေပါမယ်။
 */
Glide.with(this)
        .load(backdropImageUrl)
        .centerCrop()
        .placeholder(R.color.card_bg)
        .error(R.color.card_bg)
        .transition(
                DrawableTransitionOptions
                        .withCrossFade(650)
        )
        .into(backdrop);

/*
 * Detail poster ကိုလည်း dark placeholder ကနေ
 * 650ms အတွင်း fade-in ပုံစံနဲ့ပေါ်လာစေပါမယ်။
 */
if (posterUrl.isEmpty()) {
    poster.setVisibility(View.GONE);

    Glide.with(this)
            .clear(poster);
} else {
    poster.setVisibility(View.VISIBLE);

    Glide.with(this)
            .load(posterUrl)
            .centerCrop()
            .placeholder(R.color.card_bg)
            .error(R.color.card_bg)
            .transition(
                    DrawableTransitionOptions
                            .withCrossFade(650)
            )
            .into(poster);
}



        episodesContainer.removeAllViews();

        episodesLabel.setVisibility(View.GONE);
        episodesContainer.setVisibility(View.GONE);

        firstVideoUrl = "";
        firstVideoType = "auto";
        firstEpisodeId = "";

        /*
         * series category ကို Free 18+ အဖြစ်
         * standalone title ပုံစံသုံးမယ်။
         */
        bindMovie(item);

        updateDownloadVisibility();

        if (SessionManager.isLoggedIn()) {
            checkFavorite();
        } else {
            isFavorite = false;
            updateFavoriteText();
        }
    }

    private void bindMovie(JSONObject item) {
        episodesLabel.setVisibility(View.GONE);
        episodesContainer.setVisibility(View.GONE);

        firstEpisodeId = "";

        firstVideoUrl = item.optString(
                "video_url",
                ""
        );

        firstVideoType = item.optString(
                "video_type",
                "auto"
        );

        boolean hasVideo = item.optBoolean(
                "has_video",
                !firstVideoUrl.isEmpty()
        );

        if (hasVideo) {
    playButton.setEnabled(true);
    playButton.setVisibility(View.VISIBLE);
    updatePlayButtonText();
} else {
    playButton.setVisibility(View.GONE);
}

    }

    private void bindSeries(JSONObject item) {
        /*
         * မူရင်းကုဒ်ရဲ့ compile error ဖြစ်နေတဲ့နေရာကို
         * ဒီလို ခွဲရေးရပါမယ်။
         */
        firstEpisodeId = "";
        firstVideoUrl = "";
        firstVideoType = "auto";

        JSONArray episodes = item.optJSONArray("episodes");

        if (episodes == null || episodes.length() == 0) {
            episodesLabel.setVisibility(View.GONE);
            episodesContainer.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            return;
        }

        episodesLabel.setVisibility(View.VISIBLE);
        episodesContainer.setVisibility(View.VISIBLE);

        for (int index = 0; index < episodes.length(); index++) {
            JSONObject episode = episodes.optJSONObject(index);

            if (episode == null) {
                continue;
            }

            final String videoUrl = episode.optString(
                    "video_url",
                    ""
            );

            final String episodeId = episode.optString(
                    "id",
                    ""
            );

            final String videoType = episode.optString(
                    "video_type",
                    "auto"
            );

            boolean hasVideo = episode.optBoolean(
                    "has_video",
                    !videoUrl.isEmpty()
            );

            if (firstEpisodeId.isEmpty() && hasVideo) {
                firstEpisodeId = episodeId;
                firstVideoUrl = videoUrl;
                firstVideoType = videoType;
            }

            View episodeView = LayoutInflater
                    .from(this)
                    .inflate(
                            R.layout.item_episode,
                            episodesContainer,
                            false
                    );

            if (!(episodeView instanceof TextView)) {
                /*
                 * item_episode.xml ရဲ့ root view က TextView
                 * မဟုတ်ရင် ClassCastException မဖြစ်အောင်ပါ။
                 */
                continue;
            }

            TextView episodeButton = (TextView) episodeView;

            String label =
                    "S" +
                            episode.optInt(
                                    "season_number",
                                    1
                            ) +
                            " • E" +
                            episode.optInt(
                                    "episode_number",
                                    1
                            );

            String episodeTitle = episode.optString(
                    "episode_title",
                    ""
            );

            if (!episodeTitle.isEmpty()) {
                label += "  " + episodeTitle;
            }

            episodeButton.setText(label);
            episodeButton.setEnabled(hasVideo);
            episodeButton.setAlpha(hasVideo ? 1f : 0.5f);

            if (hasVideo) {
                episodeButton.setOnClickListener(view ->
                        playVideo(
                                videoUrl,
                                videoType,
                                episodeId
                        )
                );
            } else {
                episodeButton.setOnClickListener(null);
            }

            episodesContainer.addView(episodeButton);
        }

        if (!firstVideoUrl.isEmpty()) {
            if ("lugyi".equalsIgnoreCase(titleCategory)) {
                playButton.setText("VIP PLAY FIRST EPISODE");
            } else {
                playButton.setText("PLAY FIRST EPISODE");
            }

            playButton.setEnabled(true);
            playButton.setVisibility(View.VISIBLE);
        } else {
            playButton.setVisibility(View.GONE);
        }
    }
    private void updateDownloadVisibility() {
        boolean downloadableCategory =
                "series".equalsIgnoreCase(
                        titleCategory
                ) ||
                "lugyi".equalsIgnoreCase(
                        titleCategory
                );

        downloadButton.setVisibility(
                downloadableCategory
                        ? View.VISIBLE
                        : View.GONE
        );

        downloadButton.setText(
                downloadableCategory
                        ? "↓  DOWNLOAD"
                        : ""
        );

        downloadButton.setEnabled(
                downloadableCategory
        );

        downloadButton.setAlpha(
                downloadableCategory
                        ? 1f
                        : 0.5f
        );
    }

    private void checkFavorite() {
    if (
            !SessionManager.isLoggedIn() ||
            titleId.isEmpty()
    ) {
        isFavorite = false;
        updateFavoriteText();
        return;
    }

    favoriteLoading = true;
    favoriteButton.setEnabled(false);
    favoriteButton.setAlpha(0.55f);

    ApiClient.get(
            "favorites/" +
                    ApiClient.encode(titleId),
            new ApiClient.Callback() {
                @Override
                public void onSuccess(
                        JSONObject json
                ) {
                    runOnUiThread(() -> {
                        favoriteLoading = false;
                        favoriteButton.setEnabled(true);

                        isFavorite =
                                json.optBoolean(
                                        "favorite",
                                        false
                                );

                        updateFavoriteText();
                    });
                }

                @Override
                public void onError(
                        Exception error
                ) {
                    runOnUiThread(() -> {
                        favoriteLoading = false;
                        favoriteButton.setEnabled(true);
                        updateFavoriteText();
                    });
                }
            }
    );
}

private boolean isSafeDownloadUrl(
        String value
) {
    try {
        Uri uri = Uri.parse(value);

        String scheme =
                uri.getScheme();

        String host =
                uri.getHost();

        if (
                !"https".equalsIgnoreCase(
                        scheme
                ) ||
                host == null ||
                host.trim().isEmpty()
        ) {
            return false;
        }

        String normalizedHost =
                host.toLowerCase(
                        java.util.Locale.US
                );

        if (
                "localhost".equals(
                        normalizedHost
                ) ||
                normalizedHost.endsWith(
                        ".localhost"
                ) ||
                normalizedHost.startsWith(
                        "127."
                ) ||
                "0.0.0.0".equals(
                        normalizedHost
                ) ||
                "::1".equals(
                        normalizedHost
                )
        ) {
            return false;
        }

        return true;
    } catch (Exception error) {
        return false;
    }
}

    private void setFavorite(boolean shouldFavorite) {
        if (favoriteLoading || titleId.isEmpty()) {
            return;
        }

        if (!SessionManager.isLoggedIn()) {
            addFavoriteAfterLogin = true;

            authLauncher.launch(
                    new Intent(
                            this,
                            AuthActivity.class
                    )
            );

            return;
        }

        favoriteLoading = true;
        favoriteButton.setEnabled(false);
        favoriteButton.setAlpha(0.55f);

        ApiClient.Callback callback =
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            favoriteLoading = false;
                            favoriteButton.setEnabled(true);

                            isFavorite = shouldFavorite;
                            updateFavoriteText();

                            Toast.makeText(
                                    DetailActivity.this,
                                    shouldFavorite
                                            ? "Favorite မှတ်ပြီးပါပြီ။"
                                            : "Favorite မှ ဖယ်ရှားပြီးပါပြီ။",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            favoriteLoading = false;
                            favoriteButton.setEnabled(true);
                            updateFavoriteText();

                            Toast.makeText(
                                    DetailActivity.this,
                                    safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                };

        String path =
                "favorites/" +
                        ApiClient.encode(titleId);

        if (shouldFavorite) {
            ApiClient.post(
                    path,
                    new JSONObject(),
                    callback
            );
        } else {
            ApiClient.delete(
                    path,
                    callback
            );
        }
    }

    private void updateFavoriteText() {
        favoriteButton.setImageResource(
                isFavorite
                        ? R.drawable.ic_favorite_filled
                        : R.drawable.ic_favorite_border
        );

        favoriteButton.setContentDescription(
                isFavorite
                        ? "Remove from favorites"
                        : "Add to favorites"
        );

        favoriteButton.setAlpha(
                favoriteLoading
                        ? 0.55f
                        : 1f
        );
    }
/*
 * JSONObject.NULL, "null", "undefined", "N/A"
 * စသော အသုံးမဝင်သည့် metadata များကို
 * screen ပေါ်မပြမီ empty string ပြောင်းပေးမည်။
 */
private String cleanMetadataValue(
        JSONObject item,
        String key
) {
    if (
            item == null ||
            key == null ||
            !item.has(key) ||
            item.isNull(key)
    ) {
        return "";
    }

    String value =
            item.optString(
                    key,
                    ""
            ).trim();

    if (value.isEmpty()) {
        return "";
    }

    String normalized =
            value.toLowerCase(
                    java.util.Locale.US
            );

    if (
            "null".equals(normalized) ||
            "undefined".equals(normalized) ||
            "n/a".equals(normalized) ||
            "na".equals(normalized) ||
            "none".equals(normalized) ||
            "-".equals(normalized)
    ) {
        return "";
    }

    return value;
}

/*
 * "0", "0.0", "0.00" စတဲ့တန်ဖိုးတွေကို
 * metadata မရှိခြင်းအဖြစ် သတ်မှတ်မယ်။
 */
private boolean isZeroMetadataValue(
        String value
) {
    if (
            value == null ||
            value.trim().isEmpty()
    ) {
        return false;
    }

    try {
        return Double.parseDouble(
                value.trim()
        ) == 0d;
    } catch (NumberFormatException ignored) {
        return false;
    }
}

    private void bindGenres(String genres) {
    genresContainer.removeAllViews();

    /*
     * Genre data မရှိလျှင် genre chip container ကိုဖျောက်မည်။
     * "Genres" label ကို XML မှဖယ်ထားသောကြောင့်
     * HorizontalScrollView ကိုသာ ထိန်းချုပ်ရန်လိုသည်။
     */
    if (genres == null || genres.trim().isEmpty()) {
        genresScroll.setVisibility(View.GONE);
        return;
    }

    String[] genreItems = genres.split(",");

    for (String genreValue : genreItems) {
        String genre = genreValue.trim();

        if (genre.isEmpty()) {
            continue;
        }

        TextView chip = new TextView(this);

        chip.setText(genre);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(13);
        chip.setSingleLine(true);

        chip.setPadding(
                dp(14),
                dp(8),
                dp(14),
                dp(8)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setShape(
                GradientDrawable.RECTANGLE
        );

        background.setColor(
                Color.parseColor("#1A1D24")
        );

        background.setCornerRadius(
                dp(50)
        );

        background.setStroke(
                dp(1),
                Color.parseColor("#3A404C")
        );

        chip.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMarginEnd(dp(8));

        genresContainer.addView(
                chip,
                params
        );
    }

    /*
     * Valid genre တစ်ခုမှမရှိလျှင် container ကိုဖျောက်မည်။
     * ရှိလျှင် Action, Drama စသည့် chip များကိုပြမည်။
     */
    genresScroll.setVisibility(
            genresContainer.getChildCount() > 0
                    ? View.VISIBLE
                    : View.GONE
    );
}


    private void shareCurrentTitle() {
        String currentTitle =
                title.getText()
                        .toString()
                        .trim();

        if (currentTitle.isEmpty()) {
            return;
        }

        String shareText =
                "CMFLIX မှာ \"" +
                        currentTitle +
                        "\" ကို ကြည့်ရှုပါ။";

        Intent shareIntent =
                new Intent(Intent.ACTION_SEND);

        shareIntent.setType("text/plain");

        shareIntent.putExtra(
                Intent.EXTRA_SUBJECT,
                currentTitle
        );

        shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                shareText
        );

        startActivity(
                Intent.createChooser(
                        shareIntent,
                        "Share movie"
                )
        );
    }

    private void openTelegramContact() {
        try {
            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/iqowoq")
                    );

            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "Telegram contact ကို ဖွင့်၍မရပါ။",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    private void playVideo(
        String url,
        String type,
        String episodeId
) {
    if ("lugyi".equalsIgnoreCase(titleCategory)) {
        if (!SessionManager.isLoggedIn()) {
            loginRequestedForPlayback = true;

            authLauncher.launch(
                    new Intent(
                            this,
                            AuthActivity.class
                    )
            );

            return;
        }

        /*
         * Cached VIP သက်တမ်းမရှိ/ကုန်နေပါက
         * server ကို request မပို့ခင် Premium dialog ပြမယ်။
         */
        if (
                SessionManager.getVipUntil()
                        <= System.currentTimeMillis()
        ) {
            PremiumDialog.show(this);
            return;
        }

        requestProtectedPlayback(episodeId);
        return;
    }

    openPlayer(url, type);
}


    private void requestProtectedPlayback(String episodeId) {
        if (titleId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Title ID မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        playButton.setEnabled(false);
playButton.setAlpha(0.65f);


        JSONObject body = new JSONObject();

        try {
            body.put(
                    "titleId",
                    titleId
            );

            if (episodeId != null && !episodeId.isEmpty()) {
                body.put(
                        "episodeId",
                        episodeId
                );
            }
        } catch (Exception error) {
            restorePlayButtonText();

            Toast.makeText(
                    this,
                    safeMessage(error),
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        ApiClient.post(
                "play",
                body,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            restorePlayButtonText();

                            String videoUrl =
                                    json.optString(
                                            "videoUrl",
                                            ""
                                    );

                            String videoType =
                                    json.optString(
                                            "videoType",
                                            "auto"
                                    );

                            if (videoUrl.isEmpty()) {
                                Toast.makeText(
                                        DetailActivity.this,
                                        "Video link မရပါ။",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            openPlayer(
                                    videoUrl,
                                    videoType
                            );
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            restorePlayButtonText();

                            Toast.makeText(
                                    DetailActivity.this,
                                    safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void restorePlayButtonText() {
    playButton.setEnabled(true);
    playButton.setAlpha(1f);
    updatePlayButtonText();
}

private void updatePlayButtonText() {
    if (
            playButton == null ||
            titleId == null ||
            titleId.isEmpty()
    ) {
        return;
    }

    boolean vip =
            "lugyi".equalsIgnoreCase(
                    titleCategory
            );

    boolean isSeries =
            episodesContainer.getVisibility() ==
                    View.VISIBLE;

    long resumePosition =
            LocalStore.getResumePosition(
                    titleId
            );

    if (resumePosition > 0L) {
        playButton.setText(
                (vip ? "VIP " : "") +
                        "RESUME • " +
                        formatWatchTime(
                                resumePosition
                        )
        );

        return;
    }

    if (vip) {
        playButton.setText(
                isSeries
                        ? "VIP PLAY FIRST EPISODE"
                        : "VIP PLAY"
        );
    } else {
        playButton.setText(
                isSeries
                        ? "PLAY FIRST EPISODE"
                        : "PLAY"
        );
    }
}

private String formatWatchTime(long value) {
    long seconds =
            Math.max(0L, value / 1000L);

    long hours = seconds / 3600L;
    long minutes = (seconds % 3600L) / 60L;
    long remaining = seconds % 60L;

    if (hours > 0L) {
        return String.format(
                java.util.Locale.US,
                "%d:%02d:%02d",
                hours,
                minutes,
                remaining
        );
    }

    return String.format(
            java.util.Locale.US,
            "%02d:%02d",
            minutes,
            remaining
    );
}


    private void openPlayer(
            String url,
            String type
    ) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Video link မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent =
                new Intent(
                        this,
                        PlayerActivity.class
                );

        intent.putExtra(
                "video_url",
                url
        );

        intent.putExtra(
                "video_type",
                type == null || type.trim().isEmpty()
                        ? "auto"
                        : type
        );

        intent.putExtra(
        "title",
        title.getText().toString()
);

intent.putExtra(
        "title_id",
        titleId
);

startActivity(intent);

    }
@Override
protected void onResume() {
    super.onResume();

    if (
            playButton != null &&
            playButton.getVisibility() ==
                    View.VISIBLE
    ) {
        updatePlayButtonText();
    }
}

    private String safeMessage(
        Exception error
) {
    Throwable current = error;

    while (current != null) {
        if (
                current instanceof
                        java.net.UnknownHostException ||
                current instanceof
                        java.net.SocketTimeoutException ||
                current instanceof
                        java.net.ConnectException ||
                current instanceof
                        java.net.NoRouteToHostException ||
                current instanceof
                        javax.net.ssl.SSLException
        ) {
            return "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။";
        }

        current = current.getCause();
    }

    if (
            error == null ||
            error.getMessage() == null ||
            error.getMessage()
                    .trim()
                    .isEmpty()
    ) {
        return "Request မအောင်မြင်ပါ။";
    }

    String message =
            error.getMessage().trim();

    String lower =
            message.toLowerCase(
                    java.util.Locale.US
            );

    if (
            lower.contains("unable to resolve host") ||
            lower.contains("no address associated") ||
            lower.contains("failed to connect")
    ) {
        return "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။";
    }

    return message;
}

}
