package com.cmflix.nativeapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * မြန်မာ category — source ရွေးချယ်မှု
 * (MyanmarHubActivity မှ ဖွင့်သည်)။
 *
 * - "mmtube"   ("Myanmar + All 1"): mmtube.net တိုက်ရိုက်
 *   (proxy မရှိ; လက်ရှိ မြန်မာမှ VPN မလိုသေး)။
 * - "samusar"  ("Myanmar + All 2"): samusar.com တိုက်ရိုက်
 *   (proxy မရှိ; မြန်မာမှ VPN လိုအပ်နိုင်သည်) —
 *   SamusarClient direct mode။
 * - "mmlovetv" ("Myanmar + All 3"): mmlovetv.com တိုက်ရိုက်
 *   (proxy မရှိ; WordPress cfr2ss MP4 stream)။
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

    public static final String EXTRA_SOURCE =
            "myanmar_source";

    public static final String SOURCE_MMTUBE = "mmtube";
    public static final String SOURCE_SAMUSAR = "samusar";
    public static final String SOURCE_MMLOVETV = "mmlovetv";

    private static final int GRID_SPAN = 2;

    private String sourceMode = SOURCE_MMTUBE;

    /*
     * Source-agnostic list item — client ၃ ခုလုံး
     * ဒီ holder ထဲ map ထည့်သည်။
     */
    private static final class Item {
        final String title;
        final String thumbUrl;
        final String detailUrl;

        Item(String title, String thumbUrl, String detailUrl) {
            this.title = title == null ? "" : title;
            this.thumbUrl = thumbUrl == null ? "" : thumbUrl;
            this.detailUrl =
                    detailUrl == null ? "" : detailUrl;
        }
    }

    private RecyclerView grid;
    private ProgressBar progress;
    private LinearLayout emptyBox;
    private TextView emptyText;
    private TextView countText;

    private VideoAdapter adapter;

    private final List<Item> videos =
            new ArrayList<>();

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;

    /*
     * Cross-page duplicate guard — samusar repeats
     * page 1 for out-of-range page numbers. Track
     * seen detailUrls to stop infinite pagination.
     */
    private final Set<String> seenDetailUrls =
            new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_myanmar);

        grid = findViewById(R.id.myanmarGrid);
        progress = findViewById(R.id.myanmarProgress);
        emptyBox = findViewById(R.id.myanmarEmptyBox);
        emptyText = findViewById(R.id.myanmarEmptyText);
        countText = findViewById(R.id.myanmarCountText);

        /*
         * Source mode — MyanmarHubActivity မှ
         * EXTRA_SOURCE ("mmtube" / "samusar" /
         * "mmlovetv")။ Default: mmtube
         * (Myanmar + All 1)။
         */
        String modeExtra =
                getIntent().getStringExtra(EXTRA_SOURCE);

        if (SOURCE_SAMUSAR.equals(modeExtra)) {
            sourceMode = SOURCE_SAMUSAR;
        } else if (SOURCE_MMLOVETV.equals(modeExtra)) {
            sourceMode = SOURCE_MMLOVETV;
        } else {
            sourceMode = SOURCE_MMTUBE;
        }

        /*
         * Samusar direct mode — proxy/D1 config ကို
         * ကျော်ပြီး samusar.com တိုက်ရိုက်။
         * (mmtube source မှာ မသက်ဆိုင်ပါ။)
         */
        SamusarClient.setDirectMode(
                SOURCE_SAMUSAR.equals(sourceMode)
        );

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

        setupBottomNav();

        loadPage(1);
    }

    /*
     * Bottom nav — Home / Horror / 18+ / Myanmar.
     * Myanmar listing မှာမို့ Myanmar tab ကို active
     * (gold) ပြမည်။
     */
    private void setupBottomNav() {
        View navHome = findViewById(R.id.navHome);
        View navHorror = findViewById(R.id.navHorror);
        View navAdult = findViewById(R.id.navAdult);
        View navMyanmar = findViewById(R.id.navMyanmar);

        ImageView navHomeIcon =
                findViewById(R.id.navHomeIcon);
        TextView navHomeLabel =
                findViewById(R.id.navHomeLabel);
        ImageView navHorrorIcon =
                findViewById(R.id.navHorrorIcon);
        TextView navHorrorLabel =
                findViewById(R.id.navHorrorLabel);
        ImageView navAdultIcon =
                findViewById(R.id.navAdultIcon);
        TextView navAdultLabel =
                findViewById(R.id.navAdultLabel);
        ImageView navMyanmarIcon =
                findViewById(R.id.navMyanmarIcon);
        TextView navMyanmarLabel =
                findViewById(R.id.navMyanmarLabel);

        // Myanmar active (gold)
        setBottomNavItem(navHomeIcon, navHomeLabel, false);
        setBottomNavItem(navHorrorIcon, navHorrorLabel, false);
        setBottomNavItem(navAdultIcon, navAdultLabel, false);
        setBottomNavItem(navMyanmarIcon, navMyanmarLabel, true);

        navHome.setOnClickListener(
                view -> {
                    Intent intent =
                            new Intent(
                                    this,
                                    MainActivity.class
                            );
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    );
                    startActivity(intent);
                    finish();
                }
        );

        navHorror.setOnClickListener(
                view -> {
                    Intent intent =
                            new Intent(
                                    this,
                                    MainActivity.class
                            );
                    intent.putExtra(
                            "open_category", "movies"
                    );
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    );
                    startActivity(intent);
                    finish();
                }
        );

        navAdult.setOnClickListener(
                view -> {
                    Intent intent =
                            new Intent(
                                    this,
                                    AdultHubActivity.class
                            );
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    );
                    startActivity(intent);
                }
        );

        // Myanmar tab — ရောက်ပြီးသားမို့ ဘာမှမလုပ်ပါ
        navMyanmar.setOnClickListener(
                view -> {
                    // already here
                }
        );
    }

    private void setBottomNavItem(
            ImageView icon,
            TextView label,
            boolean active
    ) {
        int color =
                active
                        ? android.graphics.Color.parseColor(
                                "#E8B93E"
                        )
                        : android.graphics.Color.parseColor(
                                "#8A8F9C"
                        );

        icon.setColorFilter(color);
        label.setTextColor(color);

        if (active) {
            icon.setBackgroundResource(
                    R.drawable.drawer_icon_tile
            );
        } else {
            icon.setBackgroundResource(0);
        }
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

            /*
             * VPN hint — loading ကြာနေရင်
             * ပြမည် (spinner အောက်မှာ)။
             */
            View vpnHint =
                    findViewById(R.id.myanmarVpnHint);

            if (vpnHint != null) {
                vpnHint.setVisibility(View.VISIBLE);
            }
        }

        if (SOURCE_SAMUSAR.equals(sourceMode)) {
            SamusarClient.fetchPage(
                    page,
                    new SamusarClient.PageCallback() {
                        @Override
                        public void onResult(
                                List<SamusarClient.SamusarVideo>
                                        newVideos,
                                boolean more
                        ) {
                            List<Item> items =
                                    new ArrayList<>();

                            for (
                                    SamusarClient.SamusarVideo v
                                            : newVideos
                            ) {
                                if (v == null) {
                                    continue;
                                }

                                items.add(
                                        new Item(
                                                v.title,
                                                v.thumbUrl,
                                                v.detailUrl
                                        )
                                );
                            }

                            handlePageResult(
                                    page, items, more
                            );
                        }

                        @Override
                        public void onError(Exception error) {
                            handlePageError(error);
                        }
                    }
            );
        } else if (SOURCE_MMLOVETV.equals(sourceMode)) {
            MmlovetvClient.fetchPage(
                    page,
                    new MmlovetvClient.PageCallback() {
                        @Override
                        public void onResult(
                                List<MmlovetvClient.MmlovetvVideo>
                                        newVideos,
                                boolean more
                        ) {
                            List<Item> items =
                                    new ArrayList<>();

                            for (
                                    MmlovetvClient.MmlovetvVideo v
                                            : newVideos
                            ) {
                                if (v == null) {
                                    continue;
                                }

                                items.add(
                                        new Item(
                                                v.title,
                                                v.thumbUrl,
                                                v.detailUrl
                                        )
                                );
                            }

                            handlePageResult(
                                    page, items, more
                            );
                        }

                        @Override
                        public void onError(Exception error) {
                            handlePageError(error);
                        }
                    }
            );
        } else {
            MmtubeClient.fetchPage(
                    page,
                    new MmtubeClient.PageCallback() {
                        @Override
                        public void onResult(
                                List<MmtubeClient.MmtubeVideo>
                                        newVideos,
                                boolean more
                        ) {
                            List<Item> items =
                                    new ArrayList<>();

                            for (
                                    MmtubeClient.MmtubeVideo v
                                            : newVideos
                            ) {
                                if (v == null) {
                                    continue;
                                }

                                items.add(
                                        new Item(
                                                v.title,
                                                v.thumbUrl,
                                                v.detailUrl
                                        )
                                );
                            }

                            handlePageResult(
                                    page, items, more
                            );
                        }

                        @Override
                        public void onError(Exception error) {
                            handlePageError(error);
                        }
                    }
            );
        }
    }

    private void handlePageResult(
            int page,
            List<Item> newVideos,
            boolean more
    ) {
        runOnUiThread(() -> {
            isLoading = false;

            progress.setVisibility(
                    View.GONE
            );

            View vpnHint =
                    findViewById(
                            R.id.myanmarVpnHint
                    );

            if (vpnHint != null) {
                vpnHint.setVisibility(
                        View.GONE
                );
            }

            if (page == 1) {
                videos.clear();
                seenDetailUrls.clear();
            }

            /*
             * Cross-page duplicate guard —
             * out-of-range pages may repeat page 1.
             * Only append videos not already shown.
             */
            int added = 0;

            for (Item v : newVideos) {
                if (
                        v == null
                                || v.detailUrl == null
                                || seenDetailUrls.contains(
                                        v.detailUrl
                                )
                ) {
                    continue;
                }

                seenDetailUrls.add(v.detailUrl);
                videos.add(v);
                added++;
            }

            adapter.notifyDataSetChanged();

            currentPage = page;

            /*
             * Page added nothing new → stop
             * paginating (prevents infinite
             * repeat loops).
             */
            hasMore =
                    more
                            && !(
                                    page > 1
                                            && added == 0
                            );

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

    private void handlePageError(Exception error) {
        runOnUiThread(() -> {
            isLoading = false;

            progress.setVisibility(
                    View.GONE
            );

            View vpnHint =
                    findViewById(
                            R.id.myanmarVpnHint
                    );

            if (vpnHint != null) {
                vpnHint.setVisibility(
                        View.GONE
                );
            }

            /*
             * Debug: အမှန်တကယ် error ကို
             * ပြသမည် (user က report
             * လုပ်နိုင်ရန်)။
             */
            String detail =
                    error.getClass()
                            .getSimpleName()
                            + ": "
                            + String.valueOf(
                                    error.getMessage()
                            );

            if (videos.isEmpty()) {
                showEmpty(
                        "ဗီဒီယိုများ ရယူ၍မရပါ။\nပြန်စမ်းကြည့်ပါ။\n("
                                + detail + ")",
                        true
                );

                Toast.makeText(
                        MyanmarActivity.this,
                        detail,
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Toast.makeText(
                        MyanmarActivity.this,
                        "နောက် page ရယူ၍မရပါ။\n"
                                + detail,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
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
    // Detail page — card tap opens detail
    // (quality + download buttons are on the
    // detail page per quality row).
    // ------------------------------------------------------------------

    private void onVideoClick(
            Item video
    ) {
        openDetail(video);
    }

    private void openDetail(
            Item video
    ) {
        try {
            if (video == null) {
                return;
            }

            Intent intent =
                    new Intent(
                            this,
                            MyanmarDetailActivity.class
                    );

            intent.putExtra(
                    MyanmarDetailActivity.EXTRA_TITLE,
                    video.title
            );
            intent.putExtra(
                    MyanmarDetailActivity.EXTRA_THUMB,
                    video.thumbUrl
            );
            intent.putExtra(
                    MyanmarDetailActivity.EXTRA_DETAIL_URL,
                    video.detailUrl
            );
            intent.putExtra(
                    MyanmarDetailActivity.EXTRA_SOURCE,
                    sourceMode
            );

            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "ဖွင့်၍မရပါ။ ("
                            + error.getClass()
                                    .getSimpleName()
                            + ": "
                            + error.getMessage()
                            + ")",
                    Toast.LENGTH_LONG
            ).show();
        }
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
            Item video =
                    videos.get(position);

            holder.title.setText(video.title);

            Glide.with(holder.itemView)
                    .load(video.thumbUrl)
                    .centerCrop()
                    .into(holder.thumb);

            holder.itemView.setOnClickListener(
                    view -> onVideoClick(video)
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
            }
        }
    }
}