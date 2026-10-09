package com.cmflix.nativeapp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/*
 * မြန်မာ (samusar) detail page — reference APK ပုံစံ။
 *
 * - 16:9 thumbnail (Glide) + Burmese title
 * - "Available sources" — resolved quality
 *   (1080p/720p/480p) တစ်ခုချင်းစီအတွက် row
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

    private String title = "";
    private String thumbUrl = "";
    private String detailUrl = "";

    private ImageView thumbView;
    private TextView titleView;
    private ProgressBar progress;
    private TextView errorView;
    private LinearLayout sourcesBox;

    private SamusarClient.SamusarStream stream;
    private boolean isResolving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_myanmar_detail
        );

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

        findViewById(R.id.myanmarDetailBackButton)
                .setOnClickListener(
                        view -> finish()
                );

        thumbView =
                findViewById(R.id.myanmarDetailThumb);
        titleView =
                findViewById(R.id.myanmarDetailTitle);
        progress =
                findViewById(
                        R.id.myanmarDetailProgress
                );
        errorView =
                findViewById(R.id.myanmarDetailError);
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

        SamusarClient.resolveStream(
                detailUrl,
                new SamusarClient.StreamCallback() {
                    @Override
                    public void onResult(
                            SamusarClient.SamusarStream
                                    result
                    ) {
                        runOnUiThread(() -> {
                            isResolving = false;
                            progress.setVisibility(
                                    View.GONE
                            );

                            stream = result;

                            buildSourceRows();
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
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Quality rows — resolved quality များသာ
    // ------------------------------------------------------------------

    private void buildSourceRows() {
        sourcesBox.removeAllViews();

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

        row.setBackgroundResource(
                R.drawable.myanmar_source_row_bg
        );

        int padding = (int) (12 * density);
        row.setPadding(
                padding, padding, padding, padding
        );

        ImageView playIcon = new ImageView(this);

        playIcon.setImageResource(
                android.R.drawable
                        .ic_media_play
        );

        int iconSize = (int) (28 * density);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        iconSize, iconSize
                );

        playIcon.setLayoutParams(iconParams);
        playIcon.setColorFilter(0xFFE8B93E);

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

        ImageView playButton = new ImageView(this);

        playButton.setImageResource(
                android.R.drawable
                        .ic_media_play
        );

        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(
                        iconSize, iconSize
                );

        playParams.setMarginEnd(
                (int) (16 * density)
        );

        playButton.setLayoutParams(playParams);
        playButton.setColorFilter(0xFFE8B93E);
        playButton.setBackgroundResource(
                android.R.attr
                        .selectableItemBackgroundBorderless
        );
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
        downloadButton.setBackgroundResource(
                android.R.attr
                        .selectableItemBackgroundBorderless
        );
        downloadButton.setClickable(true);
        downloadButton.setFocusable(true);
        downloadButton.setContentDescription(
                "ဒေါင်းလုဒ်လုပ်ရန်"
        );
        downloadButton.setOnClickListener(
                view -> onDownloadClick(url)
        );

        row.addView(playIcon);
        row.addView(labelView);
        row.addView(playButton);
        row.addView(downloadButton);

        return row;
    }

    // ------------------------------------------------------------------
    // Play — VIP only
    // ------------------------------------------------------------------

    private void onPlayClick(String url) {
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

        if (stream == null) {
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
         * Resume support — MyanmarActivity နှင့်
         * အတူ "samusar:" prefix (LocalStore)။
         */
        intent.putExtra(
                "title_id",
                SamusarClient.videoId(detailUrl)
        );
        intent.putExtra("video_title", title);
        intent.putExtra("video_thumb", thumbUrl);

        /*
         * Samusar tokenized URL headers —
         * MyanmarActivity.openPlayer နှင့် အတူ။
         */
        intent.putExtra(
                "video_referer",
                stream.referer
        );
        intent.putExtra(
                "video_user_agent",
                SamusarClient.USER_AGENT
        );
        intent.putExtra(
                "video_cookie",
                stream.cookieHeader
        );

        startActivity(intent);
    }

    // ------------------------------------------------------------------
    // Download — VIP only, DownloadManager + headers
    // ------------------------------------------------------------------

    private void onDownloadClick(String url) {
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

        if (stream == null) {
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
             * Samusar session headers —
             * MyanmarActivity.enqueueDownload
             * နှင့် အတူ။
             */
            request.addRequestHeader(
                    "User-Agent",
                    SamusarClient.USER_AGENT
            );

            if (
                    stream.referer != null &&
                            !stream.referer.isEmpty()
            ) {
                request.addRequestHeader(
                        "Referer",
                        stream.referer
                );
            }

            if (
                    stream.cookieHeader != null &&
                            !stream.cookieHeader.isEmpty()
            ) {
                request.addRequestHeader(
                        "Cookie",
                        stream.cookieHeader
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