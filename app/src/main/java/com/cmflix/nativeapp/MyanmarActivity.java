package com.cmflix.nativeapp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/*
 * မြန်မာ category — samusar.com တိုက်ရိုက် scrape။
 *
 * - Cover ပုံများ (landscape) ကို 16:9 card grid ဖြင့် ပြသည်။
 * - ကြည့်ခြင်း + Download နှစ်မျိုးလုံး VIP only။
 * - PIN မရှိပါ (user ဆုံးဖြတ်ချက်)။
 * - Stream URL များကို cache လုံးဝ မလုပ်ပါ —
 *   play/download မလုပ်မီ fresh resolve အမြဲလုပ်သည်။
 * - Kill-switch ကို MainActivity bottom-nav tab
 *   visibility မှ ကိုင်တွယ်သည် (ဒီ Activity က
 *   tab ပွင့်နေမှသာ ရောက်နိုင်သည်)။
 */
public class MyanmarActivity extends AppCompatActivity {

    private static final int GRID_SPAN = 2;

    private RecyclerView grid;
    private ProgressBar progress;
    private LinearLayout emptyBox;
    private TextView emptyText;
    private TextView countText;

    /*
     * ဆက်လက်ကြည့်ရှုရန် section။
     */
    private LinearLayout continueBox;
    private RecyclerView continueList;
    private ContinueAdapter continueAdapter;

    private final List<LocalStore.SamusarProgress>
            continueItems = new ArrayList<>();

    private VideoAdapter adapter;

    private final List<SamusarClient.SamusarVideo> videos =
            new ArrayList<>();

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;

    /*
     * Resolve request တစ်ခု run နေစဉ်
     * ထပ်နှိပ်ခြင်းကို ကာကွယ်ရန်။
     */
    private boolean isResolving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_myanmar);

        grid = findViewById(R.id.myanmarGrid);
        progress = findViewById(R.id.myanmarProgress);
        emptyBox = findViewById(R.id.myanmarEmptyBox);
        emptyText = findViewById(R.id.myanmarEmptyText);
        countText = findViewById(R.id.myanmarCountText);
        continueBox = findViewById(R.id.myanmarContinueBox);
        continueList =
                findViewById(R.id.myanmarContinueList);

        findViewById(R.id.myanmarBackButton)
                .setOnClickListener(
                        view -> finish()
                );

        findViewById(R.id.myanmarRetryButton)
                .setOnClickListener(
                        view -> loadPage(1)
                );

        adapter = new VideoAdapter();

        GridLayoutManager layoutManager =
                new GridLayoutManager(this, GRID_SPAN);

        grid.setLayoutManager(layoutManager);
        grid.setAdapter(adapter);

        grid.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {
                        if (dy <= 0) {
                            return;
                        }

                        int total =
                                layoutManager
                                        .getItemCount();

                        int lastVisible =
                                layoutManager
                                        .findLastVisibleItemPosition();

                        /*
                         * အောက်ဆုံးနား ရောက်လျှင်
                         * နောက် page ကို preload လုပ်မည်။
                         */
                        if (
                                !isLoading &&
                                        hasMore &&
                                        total > 0 &&
                                        lastVisible >=
                                                total - 4
                        ) {
                            loadPage(currentPage + 1);
                        }
                    }
                }
        );

        /*
         * ဆက်လက်ကြည့်ရှုရန် — horizontal list။
         */
        continueAdapter = new ContinueAdapter();

        continueList.setLayoutManager(
                new androidx.recyclerview.widget
                        .LinearLayoutManager(
                                this,
                                androidx.recyclerview.widget
                                        .LinearLayoutManager
                                        .HORIZONTAL,
                                false
                        )
        );

        continueList.setAdapter(continueAdapter);

        loadPage(1);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*
         * PlayerActivity မှ ပြန်လာတိုင်း
         * progress အသစ်များကို ပြန်ဖတ်မည်။
         */
        refreshContinueWatching();
    }

    /*
     * ကြည့်လက်စ samusar video များ —
     * LocalStore သီးသန့် store မှ။
     */
    private void refreshContinueWatching() {
        List<LocalStore.SamusarProgress> items =
                LocalStore
                        .getSamusarContinueWatching();

        continueItems.clear();
        continueItems.addAll(items);

        continueAdapter.notifyDataSetChanged();

        continueBox.setVisibility(
                continueItems.isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );
    }

    // ------------------------------------------------------------------
    // Pagination
    // ------------------------------------------------------------------

    private void loadPage(int page) {
        if (isLoading) {
            return;
        }

        if (!NetworkUtils.isOnline(this)) {
            if (videos.isEmpty()) {
                showEmpty(
                        "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။",
                        true
                );
            } else {
                Toast.makeText(
                        this,
                        "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return;
        }

        isLoading = true;

        if (page == 1 && videos.isEmpty()) {
            progress.setVisibility(View.VISIBLE);
            emptyBox.setVisibility(View.GONE);
        }

        SamusarClient.fetchPage(
                page,
                new SamusarClient.PageCallback() {
                    @Override
                    public void onResult(
                            List<SamusarClient.SamusarVideo>
                                    newVideos,
                            boolean more
                    ) {
                        runOnUiThread(() -> {
                            isLoading = false;

                            progress.setVisibility(
                                    View.GONE
                            );

                            if (page == 1) {
                                videos.clear();
                            }

                            videos.addAll(newVideos);
                            adapter.notifyDataSetChanged();

                            currentPage = page;
                            hasMore = more;

                            updateCount();

                            if (videos.isEmpty()) {
                                showEmpty(
                                        "ဗီဒီယို မရှိပါ။",
                                        true
                                );
                            } else {
                                emptyBox.setVisibility(
                                        View.GONE
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            isLoading = false;

                            progress.setVisibility(
                                    View.GONE
                            );

                            if (videos.isEmpty()) {
                                showEmpty(
                                        "ဗီဒီယိုများ ရယူ၍မရပါ။\nပြန်စမ်းကြည့်ပါ။",
                                        true
                                );
                            } else {
                                Toast.makeText(
                                        MyanmarActivity.this,
                                        "နောက် page ရယူ၍မရပါ။",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
                    }
                }
        );
    }

    private void showEmpty(
            String message,
            boolean showRetry
    ) {
        emptyText.setText(message);
        emptyBox.setVisibility(View.VISIBLE);

        findViewById(R.id.myanmarRetryButton)
                .setVisibility(
                        showRetry
                                ? View.VISIBLE
                                : View.GONE
                );
    }

    private void updateCount() {
        countText.setText(
                videos.isEmpty()
                        ? ""
                        : videos.size() + " ခု"
        );
    }

    // ------------------------------------------------------------------
    // Play — VIP only, fresh resolve
    // ------------------------------------------------------------------

    private void onVideoClick(
            SamusarClient.SamusarVideo video
    ) {
        if (!SessionManager.isVipActive()) {
            PremiumDialog.show(this);
            return;
        }

        if (isResolving) {
            return;
        }

        isResolving = true;

        Toast.makeText(
                this,
                "Video link ထုတ်နေသည်…",
                Toast.LENGTH_SHORT
        ).show();

        SamusarClient.resolveStream(
                video.detailUrl,
                new SamusarClient.StreamCallback() {
                    @Override
                    public void onResult(
                            SamusarClient.SamusarStream
                                    stream
                    ) {
                        runOnUiThread(() -> {
                            isResolving = false;

                            /*
                             * Quality chooser (720p default) —
                             * quality တစ်ခုတည်းရှိလျှင်
                             * dialog မပြဘဲ တန်းဖွင့်မည်။
                             */
                            chooseQuality(
                                    stream,
                                    url ->
                                            openPlayer(
                                                    video,
                                                    stream,
                                                    url
                                            )
                            );
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            isResolving = false;

                            Toast.makeText(
                                    MyanmarActivity.this,
                                    "Video ဖွင့်၍မရပါ။ ပြန်စမ်းပါ။",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    /*
     * Quality chooser dialog — play နှင့် download
     * နှစ်မျိုးလုံး ဒီကနေဖြတ်သည်။
     * Default 720p (ရှိလျှင်)။
     */
    private interface QualityCallback {
        void onQuality(String url);
    }

    private void chooseQuality(
            SamusarClient.SamusarStream stream,
            QualityCallback callback
    ) {
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

        /*
         * Quality တစ်ခုတည်းရှိလျှင် chooser မပြပါ။
         */
        if (urls.size() <= 1) {
            callback.onQuality(stream.bestUrl());
            return;
        }

        /*
         * Default = 720p (ရှိလျှင်)။
         */
        int defaultIndex =
                urls.indexOf(stream.url720);

        if (defaultIndex < 0) {
            defaultIndex = 0;
        }

        final int[] selected = {defaultIndex};

        String[] labelArray =
                labels.toArray(new String[0]);

        /*
         * Default quality ကို label မှာ အမှတ်အသားလုပ်မည်။
         */
        labelArray[defaultIndex] =
                labelArray[defaultIndex] + " (default)";

        new AlertDialog.Builder(this)
                .setTitle("Quality ရွေးပါ")
                .setSingleChoiceItems(
                        labelArray,
                        defaultIndex,
                        (dialog, which) ->
                                selected[0] = which
                )
                .setPositiveButton(
                        "OK",
                        (dialog, which) ->
                                callback.onQuality(
                                        urls.get(
                                                selected[0]
                                        )
                                )
                )
                .setNegativeButton(
                        "မလုပ်တော့ပါ",
                        null
                )
                .show();
    }

    private void openPlayer(
            SamusarClient.SamusarVideo video,
            SamusarClient.SamusarStream stream,
            String url
    ) {
        if (url == null || url.isEmpty()) {
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
         * Resume support — title_id အဖြစ် detail URL
         * ("samusar:" prefix ဖြင့်)။ PlayerActivity မှ
         * LocalStore သီးသန့် store တွင် position
         * သိမ်းမည်။ Backend flow များ မထိပါ။
         */
        intent.putExtra(
                "title_id",
                SamusarClient.videoId(video.detailUrl)
        );
        intent.putExtra("video_title", video.title);
        intent.putExtra("video_thumb", video.thumbUrl);

        /*
         * Samusar tokenized URL များအတွက် လိုအပ်သော
         * request headers — PlayerActivity မှ
         * ExoPlayer data source တွင် ပြန်ထည့်မည်။
         * URL ကို log/cache လုံးဝ မလုပ်ပါ။
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
    // Download — VIP only, fresh resolve, headers via DownloadManager
    // ------------------------------------------------------------------

    private void onDownloadClick(
            SamusarClient.SamusarVideo video
    ) {
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
                    "Download link ထုတ်ရန် အင်တာနက်ချိတ်ဆက်ပါ။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        isResolving = true;

        Toast.makeText(
                this,
                "Download link ထုတ်နေသည်…",
                Toast.LENGTH_SHORT
        ).show();

        SamusarClient.resolveStream(
                video.detailUrl,
                new SamusarClient.StreamCallback() {
                    @Override
                    public void onResult(
                            SamusarClient.SamusarStream
                                    stream
                    ) {
                        runOnUiThread(() -> {
                            isResolving = false;

                            /*
                             * Fresh resolve ပြီးချင်း quality
                             * ရွေးခိုင်းမည် — token expire
                             * မဖြစ်ခင် တန်း enqueue လုပ်ရန်။
                             */
                            chooseQuality(
                                    stream,
                                    url ->
                                            enqueueDownload(
                                                    video,
                                                    stream,
                                                    url
                                            )
                            );
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            isResolving = false;

                            Toast.makeText(
                                    MyanmarActivity.this,
                                    "Download link မရပါ။ ပြန်စမ်းပါ။",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void enqueueDownload(
            SamusarClient.SamusarVideo video,
            SamusarClient.SamusarStream stream,
            String url
    ) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(
                    this,
                    "Download link မရှိပါ။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String fileName =
                buildFileName(video.title);

        try {
            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            /*
             * Samusar session headers —
             * detail page request မှ fresh ရထားသော
             * cookies + referer။
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

            request.setTitle(video.title);
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

        /*
         * Filesystem တွင် အန္တရာယ်ရှိသော
         * character များ ဖယ်မည်။
         */
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

    // ------------------------------------------------------------------
    // Adapter — 16:9 landscape cards
    // ------------------------------------------------------------------

    private final class VideoAdapter
            extends RecyclerView.Adapter<VideoAdapter.Holder> {

        private final int thumbHeightPx;

        VideoAdapter() {
            /*
             * 16:9 thumbnail height ကို screen width
             * အလိုက် ကြိုတွက်မည် (grid padding 8dp×2 +
             * card padding 4dp×2×span)။
             */
            DisplayMetrics metrics =
                    getResources()
                            .getDisplayMetrics();

            float density = metrics.density;

            int horizontalPaddingPx =
                    (int) ((16 + 16) * density);

            int itemWidthPx =
                    (metrics.widthPixels
                            - horizontalPaddingPx)
                            / GRID_SPAN;

            thumbHeightPx = itemWidthPx * 9 / 16;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            View view =
                    LayoutInflater.from(parent.getContext())
                            .inflate(
                                    R.layout
                                            .item_myanmar_video,
                                    parent,
                                    false
                            );

            ImageView thumb =
                    view.findViewById(R.id.cardThumb);

            ViewGroup.LayoutParams params =
                    thumb.getLayoutParams();

            params.height = thumbHeightPx;
            thumb.setLayoutParams(params);

            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull Holder holder,
                int position
        ) {
            SamusarClient.SamusarVideo video =
                    videos.get(position);

            holder.title.setText(video.title);

            Glide.with(holder.itemView)
                    .load(video.thumbUrl)
                    .centerCrop()
                    .into(holder.thumb);

            holder.itemView.setOnClickListener(
                    view -> onVideoClick(video)
            );

            holder.downloadButton.setOnClickListener(
                    view -> onDownloadClick(video)
            );
        }

        @Override
        public int getItemCount() {
            return videos.size();
        }

        final class Holder
                extends RecyclerView.ViewHolder {

            final ImageView thumb;
            final TextView title;
            final ImageView downloadButton;

            Holder(@NonNull View itemView) {
                super(itemView);

                thumb =
                        itemView.findViewById(
                                R.id.cardThumb
                        );
                title =
                        itemView.findViewById(
                                R.id.cardTitle
                        );
                downloadButton =
                        itemView.findViewById(
                                R.id.cardDownloadButton
                        );
            }
        }
    }

    // ------------------------------------------------------------------
    // Continue Watching adapter — ကြည့်လက်စ video များ
    // ------------------------------------------------------------------

    private final class ContinueAdapter
            extends RecyclerView.Adapter<ContinueAdapter
                    .ContinueHolder> {

        @NonNull
        @Override
        public ContinueHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            View view =
                    LayoutInflater.from(parent.getContext())
                            .inflate(
                                    R.layout
                                            .item_myanmar_continue,
                                    parent,
                                    false
                            );

            return new ContinueHolder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull ContinueHolder holder,
                int position
        ) {
            LocalStore.SamusarProgress item =
                    continueItems.get(position);

            holder.title.setText(item.title);

            Glide.with(holder.itemView)
                    .load(item.thumbUrl)
                    .centerCrop()
                    .into(holder.thumb);

            /*
             * Progress bar — ကြည့်ပြီးသား အချိုးအစား။
             */
            if (item.duration > 0L) {
                holder.progressBar.setProgress(
                        (int) Math.min(
                                1000L,
                                item.position * 1000L /
                                        item.duration
                        )
                );
            } else {
                holder.progressBar.setProgress(0);
            }

            holder.timeText.setText(
                    formatRemaining(
                            item.position,
                            item.duration
                    )
            );

            holder.itemView.setOnClickListener(
                    view -> {
                        /*
                         * Continue item မှ SamusarVideo
                         * ပြန်တည်ဆောက်ပြီး ပုံမှန်
                         * play flow အတိုင်း ဖွင့်မည်
                         * (VIP check + fresh resolve +
                         * PlayerActivity resume dialog)။
                         */
                        String detailUrl =
                                SamusarClient
                                        .detailUrlFromId(
                                                item.id
                                        );

                        onVideoClick(
                                new SamusarClient
                                        .SamusarVideo(
                                                item.title,
                                                item.thumbUrl,
                                                detailUrl
                                        )
                        );
                    }
            );
        }

        @Override
        public int getItemCount() {
            return continueItems.size();
        }

        final class ContinueHolder
                extends RecyclerView.ViewHolder {

            final ImageView thumb;
            final TextView title;
            final TextView timeText;
            final ProgressBar progressBar;

            ContinueHolder(@NonNull View itemView) {
                super(itemView);

                thumb =
                        itemView.findViewById(
                                R.id.continueThumb
                        );
                title =
                        itemView.findViewById(
                                R.id.continueTitle
                        );
                timeText =
                        itemView.findViewById(
                                R.id.continueTimeText
                        );
                progressBar =
                        itemView.findViewById(
                                R.id.continueProgressBar
                        );
            }
        }
    }

    private static String formatRemaining(
            long position,
            long duration
    ) {
        if (duration <= 0L || position >= duration) {
            return "";
        }

        long remainingMs = duration - position;

        long totalSeconds = remainingMs / 1000L;

        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        if (minutes >= 60L) {
            long hours = minutes / 60L;
            minutes = minutes % 60L;

            return String.format(
                    java.util.Locale.US,
                    "%d:%02d:%02d ကျန်",
                    hours,
                    minutes,
                    seconds
            );
        }

        return String.format(
                java.util.Locale.US,
                "%d:%02d ကျန်",
                minutes,
                seconds
        );
    }
}