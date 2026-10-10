package com.cmflix.nativeapp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

/*
 * "Free Porn" detail page (redtube.com).
 * (UI label is "Free Porn"; the word "redtube" never
 * appears in user-visible strings.)
 *
 * - 16:9 thumbnail + title (+ duration)
 * - Single Play button + single Download button
 *   (best quality auto-selected)
 * - Play + Download: FREE (no VIP), LOGIN required.
 *   Not logged in → AuthActivity prompt; after login
 *   returns, user must tap Play/Download again manually
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
    private View buttonsBox;

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
        buttonsBox =
                findViewById(R.id.javtifulDetailButtons);

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

        findViewById(R.id.javtifulDetailPlay)
                .setOnClickListener(
                        view -> onPlayClick()
                );

        findViewById(R.id.javtifulDetailDownload)
                .setOnClickListener(
                        view -> onDownloadClick()
                );

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
        buttonsBox.setVisibility(View.GONE);

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
                                buttonsBox.setVisibility(
                                        View.VISIBLE
                                );
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
        buttonsBox.setVisibility(View.GONE);
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void onPlayClick() {
        // Free Porn: login required, VIP NOT required.
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

        String url = stream.bestUrl();

        if (url.isEmpty()) {
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

    private void onDownloadClick() {
        // Free Porn: login required, VIP NOT required.
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

        String url = stream.bestUrl();

        if (url.isEmpty()) {
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
