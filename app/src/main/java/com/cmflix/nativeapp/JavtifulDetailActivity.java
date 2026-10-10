package com.cmflix.nativeapp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
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

import java.util.List;

/*
 * Javtiful detail page.
 *
 * - 16:9 thumbnail + title (+ duration)
 * - Single Play button + single Download button
 *   (javtiful has ONE quality — no 480p/720p rows)
 * - Play + Download နှစ်မျိုးလုံး VIP only
 * - PIN မရှိပါ
 * - Landscape playback (video_orientation extra
 *   မထည့်ပါ — default landscape)
 * - Download: in-APK DownloadManager
 */
public class JavtifulDetailActivity
        extends AppCompatActivity {

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

    private JavtifulClient.JavtifulStream stream;
    private boolean isResolving = false;

    private int pendingActionAfterLogin = 0; // 0=none, 1=play, 2=download

    private final ActivityResultLauncher<Intent> authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK) {
                            pendingActionAfterLogin = 0;
                            return;
                        }
                        int action = pendingActionAfterLogin;
                        pendingActionAfterLogin = 0;
                        if (action == 1) {
                            onPlayClick();
                        } else if (action == 2) {
                            onDownloadClick();
                        }
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
                            + ": " + fatal.getMessage() + ")"
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

    // ------------------------------------------------------------------
    // Stream resolve
    // ------------------------------------------------------------------

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

        JavtifulClient.resolveStream(
                detailUrl,
                new JavtifulClient.StreamCallback() {
                    @Override
                    public void onResult(
                            JavtifulClient.JavtifulStream
                                    result,
                            List<JavtifulClient.JavtifulActress>
                                    actresses
                    ) {
                        runOnUiThread(() -> {
                            isResolving = false;
                            progress.setVisibility(
                                    View.GONE
                            );

                            stream = result;

                            showActresses(actresses);

                            if (
                                    stream != null &&
                                            stream.hasStream()
                            ) {
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

    // ------------------------------------------------------------------
    // Actresses — horizontal row, tap → filmography
    // ------------------------------------------------------------------

    private void showActresses(
            List<JavtifulClient.JavtifulActress> actresses
    ) {
        View label =
                findViewById(
                        R.id.javtifulDetailActressLabel
                );
        View scroll =
                findViewById(
                        R.id.javtifulDetailActressScroll
                );
        LinearLayout row =
                findViewById(
                        R.id.javtifulDetailActressRow
                );

        row.removeAllViews();

        if (actresses == null || actresses.isEmpty()) {
            label.setVisibility(View.GONE);
            scroll.setVisibility(View.GONE);
            return;
        }

        label.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.VISIBLE);

        LayoutInflater inflater =
                LayoutInflater.from(this);

        for (
                JavtifulClient.JavtifulActress actress
                        : actresses
        ) {
            View item =
                    inflater.inflate(
                            R.layout.item_javtiful_actress,
                            row,
                            false
                    );

            ImageView photo =
                    item.findViewById(R.id.actressPhoto);
            TextView name =
                    item.findViewById(R.id.actressName);

            name.setText(actress.name);

            if (
                    actress.photoUrl != null &&
                            !actress.photoUrl.isEmpty()
            ) {
                Glide.with(this)
                        .load(actress.photoUrl)
                        .circleCrop()
                        .into(photo);
            }

            item.setOnClickListener(
                    view -> openActress(actress)
            );

            row.addView(item);
        }
    }

    private void openActress(
            JavtifulClient.JavtifulActress actress
    ) {
        if (actress == null) {
            return;
        }

        try {
            Intent intent =
                    new Intent(
                            this,
                            JavtifulActivity.class
                    );

            intent.putExtra(
                    JavtifulActivity.EXTRA_ACTRESS_URL,
                    actress.pageUrl
            );
            intent.putExtra(
                    JavtifulActivity.EXTRA_ACTRESS_NAME,
                    actress.name
            );

            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "ဖွင့်၍မရပါ။",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ------------------------------------------------------------------
    // Play — VIP only, landscape (default)
    // ------------------------------------------------------------------

    private void onPlayClick() {
        if (!SessionManager.isLoggedIn()) {
            pendingActionAfterLogin = 1;
            authLauncher.launch(new Intent(this, AuthActivity.class));
            return;
        }

        if (!SessionManager.isVipActive()) {
            PremiumDialog.show(this);
            return;
        }

        if (isResolving || stream == null) {
            return;
        }

        String url = stream.url;

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

        /*
         * Javtiful videos are landscape — do NOT set
         * video_orientation=portrait (default landscape
         * behavior, same as Horror/18+).
         */

        intent.putExtra("video_title", title);
        intent.putExtra("video_thumb", thumbUrl);

        /*
         * NOTE: title_id မထည့်ပါ — javtiful progress
         * store သီးသန့် မရှိသေးသောကြောင့် backend
         * history ထဲ ရောမဝင်စေရန်။
         */

        if (
                stream.referer != null &&
                        !stream.referer.isEmpty()
        ) {
            intent.putExtra(
                    "video_referer", stream.referer
            );
        }

        intent.putExtra(
                "video_user_agent",
                JavtifulClient.USER_AGENT
        );

        startActivity(intent);
    }

    // ------------------------------------------------------------------
    // Download — VIP only, in-APK DownloadManager
    // ------------------------------------------------------------------

    private void onDownloadClick() {
        if (!SessionManager.isLoggedIn()) {
            pendingActionAfterLogin = 2;
            authLauncher.launch(new Intent(this, AuthActivity.class));
            return;
        }

        if (!SessionManager.isVipActive()) {
            PremiumDialog.show(this);
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

        String url = stream.url;

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
                    JavtifulClient.USER_AGENT
            );

            if (
                    stream.referer != null &&
                            !stream.referer.isEmpty()
            ) {
                request.addRequestHeader(
                        "Referer", stream.referer
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
            safe = "cmflix_javtiful_video";
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