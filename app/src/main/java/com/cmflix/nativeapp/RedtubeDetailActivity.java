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
 * "Free Porn" detail page (redtube.com).
 * (UI label is "Free Porn"; the word "redtube" never
 * appears in user-visible strings.)
 *
 * - 16:9 thumbnail + title (+ duration)
 * - Quality rows (1080p / 720p / 480p / 240p —
 *   whichever the site provides): each row has
 *   Play + Download for that quality.
 * - Play + Download: FREE (no VIP), LOGIN required.
 *   Not logged in → AuthActivity prompt; after login
 *   returns, user must tap again manually
 *   (no auto-resume).
 * - Download: in-APK DownloadManager
 */
public class RedtubeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "video_title";
    public static final String EXTRA_THUMB = "video_thumb";
    public static final String EXTRA_DETAIL_URL =
            "video_detail_url";
    public static final String EXTRA_DURATION =
            "video_duration";

    private String title = "";
    private String thumbUrl = "";
    private String detailUrl = "";
    private String duration = "";

    private ProgressBar progress;
    private TextView errorView;
    private LinearLayout sourcesBox;

    private RedtubeClient.RedtubeStream stream;
    private boolean isResolving = false;

    private int pendingActionAfterLogin = 0;

    private final ActivityResultLauncher<Intent> authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    result -> {
                        // Login ပြီးရင် auto play/download
                        // မလုပ်ပါ — user က ကိုယ်တိုင် ပြန်နှိပ်ရမည်။
                        pendingActionAfterLogin = 0;
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_javtiful_detail);

        errorView =
                findViewById(R.id.javtifulDetailError);
        progress =
                findViewById(R.id.javtifulDetailProgress);
        sourcesBox =
                findViewById(R.id.javtifulDetailSources);

        try {
            onCreateSafe();
        } catch (Exception fatal) {
            showError(
                    "Error: ("
                            + fatal.getClass().getSimpleName()
                            + ": " + fatal.getMessage()
                            + ")"
            );
        }
    }

    private void onCreateSafe() {
        Intent intent = getIntent();

        title = stringExtra(intent, EXTRA_TITLE);
        thumbUrl = stringExtra(intent, EXTRA_THUMB);
        detailUrl = stringExtra(intent, EXTRA_DETAIL_URL);
        duration = stringExtra(intent, EXTRA_DURATION);

        findViewById(R.id.javtifulDetailBackButton)
                .setOnClickListener(view -> finish());

        TextView titleView =
                findViewById(R.id.javtifulDetailTitle);
        titleView.setText(title);

        TextView durationView =
                findViewById(R.id.javtifulDetailDuration);

        if (!duration.isEmpty()) {
            durationView.setText(duration);
            durationView.setVisibility(View.VISIBLE);
        } else {
            durationView.setVisibility(View.GONE);
        }

        ImageView thumbView =
                findViewById(R.id.javtifulDetailThumb);

        if (!thumbUrl.isEmpty()) {
            Glide.with(this)
                    .load(thumbUrl)
                    .centerCrop()
                    .into(thumbView);
        }

        // Single Play/Download buttons are not used
        // for Free Porn — quality rows are built
        // after the stream resolves.
        View buttonsBox =
                findViewById(R.id.javtifulDetailButtons);
        if (buttonsBox != null) {
            buttonsBox.setVisibility(View.GONE);
        }

        resolveStream();
    }

    private static String stringExtra(
            Intent intent,
            String key
    ) {
        String value = intent.getStringExtra(key);
        return value == null ? "" : value;
    }

    private void resolveStream() {
        if (detailUrl.isEmpty()) {
            showError("Video link မရှိပါ။");
            return;
        }

        if (!NetworkUtils.isOnline(this)) {
            showError("အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။");
            return;
        }

        isResolving = true;
        progress.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        sourcesBox.setVisibility(View.GONE);

        RedtubeClient.resolveStream(
                detailUrl,
                new RedtubeClient.StreamCallback() {
                    @Override
                    public void onResult(
                            RedtubeClient.RedtubeStream
                                    result
                    ) {
                        runOnUiThread(() -> {
                            isResolving = false;
                            progress.setVisibility(
                                    View.GONE
                            );

                            stream = result;

                            if (stream != null
                                    && stream.hasStream()) {
                                buildQualityRows();
                            } else {
                                showError(
                                        "Video link ရှာမတွေ့ပါ။"
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            isResolving = false;
                            progress.setVisibility(
                                    View.GONE
                            );

                            showError(
                                    "Video link ရယူ၍မရပါ။\n"
                                            + "ပြန်စမ်းကြည့်ပါ။"
                            );
                        });
                    }
                }
        );
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        sourcesBox.setVisibility(View.GONE);
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Quality rows
    // ------------------------------------------------------------------

    private void buildQualityRows() {
        try {
            buildQualityRowsSafe();
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

    private void buildQualityRowsSafe() {
        sourcesBox.removeAllViews();

        if (stream == null || !stream.hasStream()) {
            showError("Video link ရှာမတွေ့ပါ။");
            return;
        }

        List<String> labels = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        if (stream.url1080 != null
                && !stream.url1080.isEmpty()) {
            labels.add("1080p");
            urls.add(stream.url1080);
        }

        if (stream.url720 != null
                && !stream.url720.isEmpty()) {
            labels.add("720p");
            urls.add(stream.url720);
        }

        if (stream.url480 != null
                && !stream.url480.isEmpty()) {
            labels.add("480p");
            urls.add(stream.url480);
        }

        if (stream.url240 != null
                && !stream.url240.isEmpty()) {
            labels.add("240p");
            urls.add(stream.url240);
        }

        for (int i = 0; i < labels.size(); i++) {
            sourcesBox.addView(
                    buildQualityRow(
                            labels.get(i),
                            urls.get(i)
                    )
            );
        }

        sourcesBox.setVisibility(View.VISIBLE);
    }

    /*
     * Quality row — play button + label + download
     * button (same style as Myanmar source rows).
     */
    private View buildQualityRow(
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

        ImageView playButton = new ImageView(this);
        playButton.setImageResource(
                android.R.drawable.ic_media_play
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
                android.R.drawable.stat_sys_download
        );

        LinearLayout.LayoutParams dlParams =
                new LinearLayout.LayoutParams(
                        iconSize, iconSize
                );

        downloadButton.setLayoutParams(dlParams);
        downloadButton.setColorFilter(0xFFE8B93E);

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
    // Play / Download — FREE (no VIP), LOGIN required
    // ------------------------------------------------------------------

    private void onPlayClick(String url) {
        if (!SessionManager.isLoggedIn()) {
            pendingActionAfterLogin = 1;
            authLauncher.launch(
                    new Intent(this, AuthActivity.class)
            );
            return;
        }

        if (isResolving || stream == null) {
            return;
        }

        if (url == null || url.isEmpty()) {
            Toast.makeText(
                    this,
                    "Video link မရှိပါ။",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Intent intent =
                new Intent(this, PlayerActivity.class);

        intent.putExtra("video_url", url);
        intent.putExtra("video_type", "mp4");
        intent.putExtra("video_title", title);
        intent.putExtra("video_thumb", thumbUrl);
        intent.putExtra(
                "title_id",
                RedtubeClient.videoId(detailUrl)
        );

        if (stream.referer != null
                && !stream.referer.isEmpty()) {
            intent.putExtra(
                    "video_referer", stream.referer
            );
        }

        if (stream.cookieHeader != null
                && !stream.cookieHeader.isEmpty()) {
            intent.putExtra(
                    "video_cookie", stream.cookieHeader
            );
        }

        intent.putExtra(
                "video_user_agent",
                RedtubeClient.USER_AGENT
        );

        startActivity(intent);
    }

    private void onDownloadClick(String url) {
        if (!SessionManager.isLoggedIn()) {
            pendingActionAfterLogin = 2;
            authLauncher.launch(
                    new Intent(this, AuthActivity.class)
            );
            return;
        }

        if (isResolving || stream == null) {
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

        if (url == null || url.isEmpty()) {
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

            request.addRequestHeader(
                    "User-Agent",
                    RedtubeClient.USER_AGENT
            );

            if (stream.referer != null
                    && !stream.referer.isEmpty()) {
                request.addRequestHeader(
                        "Referer", stream.referer
                );
            }

            if (stream.cookieHeader != null
                    && !stream.cookieHeader.isEmpty()) {
                request.addRequestHeader(
                        "Cookie", stream.cookieHeader
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
                    (DownloadManager) getSystemService(
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

        safe = safe.replaceAll(
                "[\\\\/:*?\"<>|\\p{Cntrl}]", "_"
        ).trim();

        if (safe.isEmpty()) {
            safe = "cmflix_freeporn_video";
        }

        if (safe.length() > 80) {
            safe = safe.substring(0, 80).trim();
        }

        if (!safe.toLowerCase().endsWith(".mp4")) {
            safe = safe + ".mp4";
        }

        return safe;
    }
}
