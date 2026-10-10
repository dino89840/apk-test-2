package com.cmflix.nativeapp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/*
 * မြန်မာ detail page — source ရွေးချယ်မှု
 * (MyanmarActivity မှ EXTRA_SOURCE)။
 *
 * - "mmtube"  (Myanmar 1): mmtube.net တိုက်ရိုက် —
 *   single MP4 (HD/SD label)။
 * - "samusar" (Myanmar 2): samusar.com တိုက်ရိုက်
 *   (direct mode) — 1080p/720p/480p quality rows။
 *
 * - 16:9 thumbnail (Glide) + Burmese title
 * - Row တိုင်းတွင် play + download button
 *
 * Stream ကို page ဖွင့်ချင်း resolve လုပ်ပြီး
 * (loading ပြမည်) play/download တွင် ပြန်သုံးမည်။
 * ကြည့်ခြင်း + Download နှစ်မျိုးလုံး VIP only။
 */
public class MyanmarDetailActivity
        extends AppCompatActivity {

    public static final String EXTRA_TITLE =
            "video_title";
    public static final String EXTRA_THUMB =
            "video_thumb";
    public static final String EXTRA_DETAIL_URL =
            "video_detail_url";
    public static final String EXTRA_SOURCE =
            "myanmar_source";

    private String title = "";
    private String thumbUrl = "";
    private String detailUrl = "";
    private String sourceMode =
            MyanmarActivity.SOURCE_MMTUBE;

    private ImageView thumbView;
    private TextView titleView;
    private ProgressBar progress;
    private TextView errorView;
    private LinearLayout sourcesBox;

    private SamusarClient.SamusarStream stream;
    private MmtubeClient.MmtubeStream mmtubeStream;
    private boolean isResolving = false;

    private int pendingActionAfterLogin = 0; // 0=none, 1=play, 2=download
    private String pendingUrlAfterLogin = "";

    private final ActivityResultLauncher<Intent> authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK) {
                            pendingActionAfterLogin = 0;
                            pendingUrlAfterLogin = "";
                            return;
                        }
                        int action = pendingActionAfterLogin;
                        String url = pendingUrlAfterLogin;
                        pendingActionAfterLogin = 0;
                        pendingUrlAfterLogin = "";
                        if (action == 1) {
                            onPlayClick(url);
                        } else if (action == 2) {
                            onDownloadClick(url);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_myanmar_detail
        );

        /*
         * Crash diagnostic — errorView နှင့် progress
         * ကို try block အပြင်မှာ ကြိုရှာထားမည်။
         * onCreate ဘယ်နေရာမှာ crash ဖြစ်ဖြစ်
         * error စာသား ပြနိုင်ရန် (activity ကို
         * crash မဖြစ်စေရ)။
         */
        errorView =
                findViewById(R.id.myanmarDetailError);
        progress =
                findViewById(
                        R.id.myanmarDetailProgress
                );

        try {
            onCreateSafe();
        } catch (Exception fatal) {
            showFatalError(fatal);
        }
    }

    /*
     * onCreate ၏ အမှန်တကယ် body — exception
     * တက်လျှင် onCreate က catch လုပ်ပြီး error
     * ပြမည်, crash မဖြစ်စေရ။
     */
    private void onCreateSafe() {
        Intent intent = getIntent();

        title =
                intent.getStringExtra(EXTRA_TITLE);

        if (title == null) {
            title = "";
        }

        thumbUrl =
                intent.getStringExtra(EXTRA_THUMB);

        if (thumbUrl == null) {
            thumbUrl = "";
        }

        detailUrl =
                intent.getStringExtra(EXTRA_DETAIL_URL);

        if (detailUrl == null) {
            detailUrl = "";
        }

        /*
         * Source mode — MyanmarActivity မှ
         * EXTRA_SOURCE ("mmtube" / "samusar")။
         * Default: mmtube (Myanmar 1)။
         */
        String modeExtra =
                intent.getStringExtra(EXTRA_SOURCE);

        if (
                MyanmarActivity.SOURCE_SAMUSAR
                        .equals(modeExtra)
        ) {
            sourceMode =
                    MyanmarActivity.SOURCE_SAMUSAR;
        } else {
            sourceMode =
                    MyanmarActivity.SOURCE_MMTUBE;
        }

        findViewById(R.id.myanmarDetailBackButton)
                .setOnClickListener(
                        view -> finish()
                );

        thumbView =
                findViewById(R.id.myanmarDetailThumb);
        titleView =
                findViewById(R.id.myanmarDetailTitle);
        sourcesBox =
                findViewById(
                        R.id.myanmarDetailSourcesBox
                );

        titleView.setText(title);

        if (!thumbUrl.isEmpty()) {
            Glide.with(this)
                    .load(thumbUrl)
                    .centerCrop()
                    .into(thumbView);
        }

        resolveStream();
    }

    /*
     * onCreate အတွင်း ဘယ်နေရာမှာ exception
     * တက်တက် — activity crash မဖြစ်စေဘဲ
     * error စာသား ပြမည်။
     */
    private void showFatalError(Exception fatal) {
        String detail =
                "("
                        + fatal.getClass()
                                .getSimpleName()
                        + ": "
                        + fatal.getMessage()
                        + ")";

        try {
            if (progress != null) {
                progress.setVisibility(View.GONE);
            }

            if (errorView != null) {
                errorView.setText(
                        "Error: " + detail
                );
                errorView.setVisibility(
                        View.VISIBLE
                );
            }
        } catch (Exception ignored) {
        }

        try {
            Toast.makeText(
                    this,
                    "Error: " + detail,
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Stream resolve — page ဖွင့်ချင်း တစ်ခါ
    // ------------------------------------------------------------------

    private void resolveStream() {
        if (detailUrl.isEmpty()) {
            showError(
                    "Video link မရှိပါ။"
            );

            return;
        }

        if (!NetworkUtils.isOnline(this)) {
            showError(
                    "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။"
            );

            return;
        }

        isResolving = true;
        progress.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        sourcesBox.removeAllViews();

        if (
                MyanmarActivity.SOURCE_SAMUSAR
                        .equals(sourceMode)
        ) {
            resolveSamusarStream();
        } else {
            resolveMmtubeStream();
        }
    }

    private void resolveSamusarStream() {
        SamusarClient.resolveStream(
                detailUrl,
                new SamusarClient.StreamCallback() {
                    @Override
                    public void onResult(
                            SamusarClient.SamusarStream
                                    result
                    ) {
                        runOnUiThread(() -> {
                            try {
                                isResolving = false;
                                progress.setVisibility(
                                        View.GONE
                                );

                                stream = result;

                                buildSourceRows();
                            } catch (Exception uiError) {
                                isResolving = false;

                                showError(
                                        "Error: ("
                                                + uiError.getClass()
                                                        .getSimpleName()
                                                + ": "
                                                + uiError
                                                        .getMessage()
                                                + ")"
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            try {
                                isResolving = false;
                                progress.setVisibility(
                                        View.GONE
                                );

                                showError(
                                        "Video link ရယူ၍မရပါ။\n"
                                                + "ပြန်စမ်းကြည့်ပါ။"
                                );
                            } catch (Exception uiError) {
                                // activity dying — nothing to show
                            }
                        });
                    }
                }
        );
    }

    private void resolveMmtubeStream() {
        MmtubeClient.resolveStream(
                detailUrl,
                new MmtubeClient.StreamCallback() {
                    @Override
                    public void onResult(
                            MmtubeClient.MmtubeStream
                                    result
                    ) {
                        runOnUiThread(() -> {
                            try {
                                isResolving = false;
                                progress.setVisibility(
                                        View.GONE
                                );

                                mmtubeStream = result;

                                buildSourceRows();
                            } catch (Exception uiError) {
                                isResolving = false;

                                showError(
                                        "Error: ("
                                                + uiError.getClass()
                                                        .getSimpleName()
                                                + ": "
                                                + uiError
                                                        .getMessage()
                                                + ")"
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            try {
                                isResolving = false;
                                progress.setVisibility(
                                        View.GONE
                                );

                                showError(
                                        "Video link ရယူ၍မရပါ။\n"
                                                + "ပြန်စမ်းကြည့်ပါ။"
                                );
                            } catch (Exception uiError) {
                                // activity dying — nothing to show
                            }
                        });
                    }
                }
        );
    }

    private void showError(String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Quality rows — resolved quality များသာ
    // ------------------------------------------------------------------

    /*
     * buildSourceRows crash မဖြစ်စေရ — row
     * တည်ဆောက်ရာတွင် exception တက်လျှင်
     * error ပြမည်။
     */
    private void buildSourceRows() {
        try {
            buildSourceRowsSafe();
        } catch (Exception rowError) {
            showError(
                    "Error: ("
                            + rowError.getClass()
                                    .getSimpleName()
                            + ": "
                            + rowError.getMessage()
                            + ")"
            );
        }
    }

    /*
     * Mmtube source ဟုတ်မဟုတ် — samusar
     * မဟုတ်လျှင် mmtube အဖြစ် သတ်မှတ်သည်။
     */
    private boolean isMmtube() {
        return !MyanmarActivity.SOURCE_SAMUSAR
                .equals(sourceMode);
    }

    private void buildSourceRowsSafe() {
        sourcesBox.removeAllViews();

        /*
         * Mmtube — single direct MP4 (quality
         * variants မရှိ)။ HD flag ရှိလျှင် "HD",
         * မရှိလျှင် "SD" label။
         */
        if (isMmtube()) {
            if (
                    mmtubeStream == null
                            || !mmtubeStream.hasStream()
            ) {
                showError(
                        "Video link ရှာမတွေ့ပါ။"
                );

                return;
            }

            sourcesBox.addView(
                    buildSourceRow(
                            mmtubeStream.isHd
                                    ? "HD"
                                    : "SD",
                            mmtubeStream.bestUrl()
                    )
            );

            return;
        }

        if (stream == null || !stream.hasStream()) {
            showError(
                    "Video link ရှာမတွေ့ပါ။"
            );

            return;
        }

        List<String> labels = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        if (
                stream.url1080 != null &&
                        !stream.url1080.isEmpty()
        ) {
            labels.add("1080p");
            urls.add(stream.url1080);
        }

        if (
                stream.url720 != null &&
                        !stream.url720.isEmpty()
        ) {
            labels.add("720p");
            urls.add(stream.url720);
        }

        if (
                stream.url480 != null &&
                        !stream.url480.isEmpty()
        ) {
            labels.add("480p");
            urls.add(stream.url480);
        }

        for (int i = 0; i < labels.size(); i++) {
            sourcesBox.addView(
                    buildSourceRow(
                            labels.get(i),
                            urls.get(i)
                    )
            );
        }
    }

    /*
     * Quality row — label + play + download။
     * Programmatic (1-3 rows သာ)။
     */
    private View buildSourceRow(
            String label,
            String url
    ) {
        float density =
                getResources()
                        .getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                                .MATCH_PARENT,
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT
                );

        rowParams.topMargin = (int) (6 * density);
        rowParams.bottomMargin = (int) (6 * density);
        row.setLayoutParams(rowParams);

        /*
         * Row background — programmatic
         * (myanmar_source_row_bg.xml နှင့် အတူ:
         * solid #151820, 12dp corners,
         * 1dp stroke #252A33)။
         * Resource lookup crash ကို လုံးဝ
         * ရှောင်ရန် code ဖြင့်သာ တည်ဆောက်သည်။
         */
        try {
            GradientDrawable rowBg =
                    new GradientDrawable();

            rowBg.setColor(0xFF151820);
            rowBg.setCornerRadius(12 * density);
            rowBg.setStroke(
                    (int) (1 * density),
                    0xFF252A33
            );

            row.setBackground(rowBg);
        } catch (Exception ignored) {
        }

        int padding = (int) (12 * density);
        row.setPadding(
                padding, padding, padding, padding
        );

        int iconSize = (int) (28 * density);

        /*
         * Working play button — LEFT side, before
         * the quality label. (The old decorative
         * left icon was removed: it did nothing.)
         */
        ImageView playButton = new ImageView(this);

        playButton.setImageResource(
                android.R.drawable
                        .ic_media_play
        );

        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(
                        iconSize, iconSize
                );

        playButton.setLayoutParams(playParams);
        playButton.setColorFilter(0xFFE8B93E);

        TextView labelView = new TextView(this);
        labelView.setText(label);

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT,
                        1f
                );

        labelParams.setMarginStart(
                (int) (12 * density)
        );

        labelView.setLayoutParams(labelParams);
        labelView.setTextColor(0xFFFFFFFF);
        labelView.setTextSize(16);

        /*
         * selectableItemBackgroundBorderless သည်
         * attr (drawable မဟုတ်) ဖြစ်သောကြောင့်
         * setBackgroundResource ဖြင့် တိုက်ရိုက်
         * သုံး၍မရပါ (NotFoundException crash) —
         * theme ကနေ resolve လုပ်မည်။
         */
        try {
            TypedValue rippleValue =
                    new TypedValue();

            getTheme().resolveAttribute(
                    android.R.attr
                            .selectableItemBackgroundBorderless,
                    rippleValue,
                    true
            );

            playButton.setBackgroundResource(
                    rippleValue.resourceId
            );
        } catch (Exception ignored) {
            // ripple မရှိလည်း ခလုတ် အလုပ်လုပ်သည်
        }

        playButton.setClickable(true);
        playButton.setFocusable(true);
        playButton.setContentDescription(
                "ဖွင့်ရန်"
        );
        playButton.setOnClickListener(
                view -> onPlayClick(url)
        );

        ImageView downloadButton = new ImageView(this);

        downloadButton.setImageResource(
                android.R.drawable
                        .stat_sys_download
        );

        LinearLayout.LayoutParams dlParams =
                new LinearLayout.LayoutParams(
                        iconSize, iconSize
                );

        downloadButton.setLayoutParams(dlParams);
        downloadButton.setColorFilter(0xFFE8B93E);

        /*
         * Attr ကို theme ကနေ resolve လုပ်မည်
         * (playButton နှင့် အတူ — direct
         * setBackgroundResource(attr) က crash)။
         */
        try {
            TypedValue rippleValue =
                    new TypedValue();

            getTheme().resolveAttribute(
                    android.R.attr
                            .selectableItemBackgroundBorderless,
                    rippleValue,
                    true
            );

            downloadButton.setBackgroundResource(
                    rippleValue.resourceId
            );
        } catch (Exception ignored) {
        }

        downloadButton.setClickable(true);
        downloadButton.setFocusable(true);
        downloadButton.setContentDescription(
                "ဒေါင်းလုဒ်လုပ်ရန်"
        );
        downloadButton.setOnClickListener(
                view -> onDownloadClick(url)
        );

        row.addView(playButton);
        row.addView(labelView);
        row.addView(downloadButton);

        return row;
    }

    // ------------------------------------------------------------------
    // Play — VIP only
    // ------------------------------------------------------------------

    /*
     * လက်ရှိ source အလိုက် header များ —
     * mmtube နှင့် samusar နှစ်မျိုးလုံး
     * detail-page Referer + session Cookie +
     * browser UA ပို့သည်။
     */
    private String currentReferer() {
        if (isMmtube()) {
            return mmtubeStream == null
                    ? ""
                    : mmtubeStream.referer;
        }

        return stream == null ? "" : stream.referer;
    }

    private String currentCookie() {
        if (isMmtube()) {
            return mmtubeStream == null
                    ? ""
                    : mmtubeStream.cookieHeader;
        }

        return stream == null ? "" : stream.cookieHeader;
    }

    private String currentUserAgent() {
        if (isMmtube()) {
            return MmtubeClient.USER_AGENT;
        }

        return SamusarClient.USER_AGENT;
    }

    private boolean hasResolvedStream() {
        if (isMmtube()) {
            return mmtubeStream != null
                    && mmtubeStream.hasStream();
        }

        return stream != null && stream.hasStream();
    }

    private void onPlayClick(String url) {
        if (!SessionManager.isLoggedIn()) {
            pendingActionAfterLogin = 1;
            pendingUrlAfterLogin = url;
            authLauncher.launch(new Intent(this, AuthActivity.class));

            return;
        }

        if (!SessionManager.isVipActive()) {
            PremiumDialog.show(this);

            return;
        }

        if (isResolving) {
            return;
        }

        openPlayer(url);
    }

    private void openPlayer(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(
                    this,
                    "Video link မရှိပါ။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!hasResolvedStream()) {
            Toast.makeText(
                    this,
                    "Video link မရှိပါ။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent intent =
                new Intent(
                        this,
                        PlayerActivity.class
                );

        intent.putExtra("video_url", url);
        intent.putExtra("video_type", "mp4");

        /*
         * Myanmar videos are mostly vertical (reel-style):
         * start playback in portrait, user can rotate
         * via the player rotate button if desired.
         * Horror/18+ keep the default landscape behavior.
         */
        intent.putExtra("video_orientation", "portrait");

        /*
         * Resume support — "mmtube:" / "samusar:"
         * prefix (LocalStore)။
         */
        intent.putExtra(
                "title_id",
                isMmtube()
                        ? MmtubeClient.videoId(detailUrl)
                        : SamusarClient.videoId(detailUrl)
        );
        intent.putExtra("video_title", title);
        intent.putExtra("video_thumb", thumbUrl);

        /*
         * Tokenized URL headers —
         * source အလိုက် Referer/Cookie/UA။
         */
        intent.putExtra(
                "video_referer",
                currentReferer()
        );
        intent.putExtra(
                "video_user_agent",
                currentUserAgent()
        );
        intent.putExtra(
                "video_cookie",
                currentCookie()
        );

        startActivity(intent);
    }

    // ------------------------------------------------------------------
    // Download — VIP only, DownloadManager + headers
    // ------------------------------------------------------------------

    private void onDownloadClick(String url) {
        if (!SessionManager.isLoggedIn()) {
            pendingActionAfterLogin = 2;
            pendingUrlAfterLogin = url;
            authLauncher.launch(new Intent(this, AuthActivity.class));

            return;
        }

        if (!SessionManager.isVipActive()) {
            PremiumDialog.show(this);

            return;
        }

        if (isResolving) {
            return;
        }

        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(
                    this,
                    "Download လုပ်ရန် အင်တာနက်ချိတ်ဆက်ပါ။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        enqueueDownload(url);
    }

    private void enqueueDownload(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(
                    this,
                    "Download link မရှိပါ။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!hasResolvedStream()) {
            Toast.makeText(
                    this,
                    "Download link မရှိပါ။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String fileName = buildFileName(title);

        try {
            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            /*
             * Session headers —
             * source အလိုက် Referer/Cookie/UA။
             */
            request.addRequestHeader(
                    "User-Agent",
                    currentUserAgent()
            );

            String referer = currentReferer();

            if (!referer.isEmpty()) {
                request.addRequestHeader(
                        "Referer",
                        referer
                );
            }

            String cookie = currentCookie();

            if (!cookie.isEmpty()) {
                request.addRequestHeader(
                        "Cookie",
                        cookie
                );
            }

            request.setTitle(title);
            request.setDescription("CM FLIX");

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "CMFLIX/" + fileName
            );

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );

            if (manager == null) {
                throw new IllegalStateException(
                        "DownloadManager မရှိပါ။"
                );
            }

            manager.enqueue(request);

            Toast.makeText(
                    this,
                    "Download စတင်ပါပြီ။",
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "Download စတင်၍မရပါ။",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private static String buildFileName(String title) {
        String safe =
                title == null ? "" : title.trim();

        safe =
                safe.replaceAll(
                        "[\\\\/:*?\"<>|\\p{Cntrl}]",
                        "_"
                ).trim();

        if (safe.isEmpty()) {
            safe = "cmflix_myanmar_video";
        }

        if (safe.length() > 80) {
            safe = safe.substring(0, 80).trim();
        }

        if (
                !safe.toLowerCase()
                        .endsWith(".mp4")
        ) {
            safe = safe + ".mp4";
        }

        return safe;
    }
}