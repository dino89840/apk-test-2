package com.cmflix.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
 * Javtiful category — direct javtiful.com scrape
 * (NO proxy for now, user will test first).
 *
 * Two modes (EXTRA_MODE):
 * - "mosaic" (default) → "Jav" (reducing mosaic)
 * - "uncensored" → "Asian" (uncensored)
 * - "chinese" → "Chinese AV" (chinese-av)
 *
 * - 16:9 landscape card grid, pagination
 * - Search bar (JAV code / actress name,
 *   mode-appropriate badge filter)
 * - Actress filmography mode (EXTRA_ACTRESS_URL)
 *   — combined mosaic+uncensored
 * - Card tap → JavtifulDetailActivity
 * - Play + Download နှစ်မျိုးလုံး VIP only
 * - PIN မရှိပါ
 * - Stream URL cache မလုပ်ပါ — play/download
 *   မလုပ်မီ fresh resolve အမြဲလုပ်သည်
 * - Continue Watching မပါ (v1 simple)
 */
public class JavtifulActivity extends AppCompatActivity {

    private static final int GRID_SPAN = 2;

    public static final String EXTRA_ACTRESS_URL =
            "actress_url";
    public static final String EXTRA_ACTRESS_NAME =
            "actress_name";

    /*
     * Listing mode: "mosaic" (default), "uncensored"
     * or "chinese".
     * - mosaic → "Jav" (reducing mosaic listing)
     * - uncensored → "Asian" (uncensored listing)
     * - chinese → "Chinese AV" (chinese-av listing)
     */
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_MOSAIC = "mosaic";
    public static final String MODE_UNCENSORED = "uncensored";
    public static final String MODE_CHINESE = "chinese";

    private RecyclerView grid;
    private ProgressBar progress;
    private LinearLayout emptyBox;
    private TextView emptyText;
    private TextView countText;
    private TextView titleText;
    private LinearLayout searchBar;
    private EditText searchInput;

    /*
     * Bottom nav — Home / Horror / 18+ / Myanmar
     * (listing pages only, detail pages excluded).
     */
    private View navMyanmarTab;

    private VideoAdapter adapter;

    private final List<JavtifulClient.JavtifulVideo> videos =
            new ArrayList<>();

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;

    /*
     * null = normal reducing-mosaic listing.
     * non-null = search mode (mosaic-only results).
     */
    private String searchQuery = null;

    /*
     * Actress filmography mode — null = off.
     * (actress page URL, mosaic+uncensored combined filter).
     */
    private String actressUrl = null;
    private String actressName = null;

    /*
     * Listing mode — MODE_MOSAIC (default),
     * MODE_UNCENSORED or MODE_CHINESE. Controls
     * listing URL, search filter, and title
     * ("Jav" / "Asian" / "Chinese AV").
     */
    private String mode = MODE_MOSAIC;

    /*
     * Sort/filter — "" = Latest (default, no param).
     * Verified live on javtiful.com:
     * added_today / added_week / added_month /
     * popular_today / popular_week / popular_month /
     * most_liked / most_viewed.
     * Applies to listing only (not search/actress).
     */
    private String sortParam = "";

    private static final String[] SORT_LABELS = {
            "Latest",
            "Added Today",
            "Added This Week",
            "Added This Month",
            "Popular Today",
            "Popular This Week",
            "Popular This Month",
            "Most Liked",
            "Most Viewed"
    };

    private static final String[] SORT_VALUES = {
            "",
            "added_today",
            "added_week",
            "added_month",
            "popular_today",
            "popular_week",
            "popular_month",
            "most_liked",
            "most_viewed"
    };

    private boolean isUncensoredMode() {
        return MODE_UNCENSORED.equals(mode);
    }

    private boolean isChineseMode() {
        return MODE_CHINESE.equals(mode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_javtiful);

        grid = findViewById(R.id.javtifulGrid);
        progress = findViewById(R.id.javtifulProgress);
        emptyBox = findViewById(R.id.javtifulEmptyBox);
        emptyText = findViewById(R.id.javtifulEmptyText);
        countText = findViewById(R.id.javtifulCountText);
        titleText = findViewById(R.id.javtifulTitleText);
        searchBar = findViewById(R.id.javtifulSearchBar);
        searchInput = findViewById(R.id.javtifulSearchInput);

        findViewById(R.id.javtifulBackButton)
                .setOnClickListener(view -> finish());

        findViewById(R.id.javtifulRetryButton)
                .setOnClickListener(view -> loadPage(1));

        findViewById(R.id.javtifulSearchToggle)
                .setOnClickListener(view -> toggleSearchBar());

        findViewById(R.id.javtifulFilterButton)
                .setOnClickListener(view -> showSortDialog());

        findViewById(R.id.javtifulSearchClear)
                .setOnClickListener(view -> onSearchClear());

        searchInput.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (
                            actionId ==
                                    EditorInfo.IME_ACTION_SEARCH
                    ) {
                        submitSearch();
                        return true;
                    }

                    return false;
                }
        );

        Intent launchIntent = getIntent();

        if (launchIntent != null) {
            String modeExtra =
                    launchIntent.getStringExtra(
                            EXTRA_MODE
                    );

            if (
                    MODE_UNCENSORED.equals(modeExtra)
            ) {
                mode = MODE_UNCENSORED;
            } else if (
                    MODE_CHINESE.equals(modeExtra)
            ) {
                mode = MODE_CHINESE;
            }

            String url =
                    launchIntent.getStringExtra(
                            EXTRA_ACTRESS_URL
                    );

            if (url != null && !url.trim().isEmpty()) {
                actressUrl = url.trim();
                actressName =
                        launchIntent.getStringExtra(
                                EXTRA_ACTRESS_NAME
                        );

                if (
                        actressName == null ||
                                actressName.trim().isEmpty()
                ) {
                    actressName = "Actress";
                }

                updateTitle();
            }

            updateTitle();
        }

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
                                layoutManager.getItemCount();

                        int lastVisible =
                                layoutManager
                                        .findLastVisibleItemPosition();

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
     * Jav/Asian listing မှာမို့ 18+ tab ကို active
     * (gold) ပြမည်။ Myanmar tab က kill-switch
     * (AppContentManager.isMyanmarEnabled) မှ
     * visibility ကို ထိန်းသည်။
     */
    private void setupBottomNav() {
        View navHome = findViewById(R.id.navHome);
        View navHorror = findViewById(R.id.navHorror);
        View navAdult = findViewById(R.id.navAdult);
        navMyanmarTab = findViewById(R.id.navMyanmar);

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

        // 18+ active (gold) — Jav/Asian သည် 18+ hub အောက်မှာမို့
        setBottomNavItem(navHomeIcon, navHomeLabel, false);
        setBottomNavItem(navHorrorIcon, navHorrorLabel, false);
        setBottomNavItem(navAdultIcon, navAdultLabel, true);
        setBottomNavItem(navMyanmarIcon, navMyanmarLabel, false);

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

        navMyanmarTab.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        MyanmarActivity.class
                                )
                        )
        );

        // Kill-switch — ပိတ်ထားလျှင် Myanmar tab ဖျောက်မည်
        updateMyanmarTabVisibility(null);

        AppContentManager.loadBanner(
                this,
                new AppContentManager.Callback() {
                    @Override
                    public void onContent(
                            org.json.JSONObject content
                    ) {
                        runOnUiThread(() ->
                                updateMyanmarTabVisibility(
                                        content
                                )
                        );
                    }

                    @Override
                    public void onError(Exception error) {
                        // default (visible) အတိုင်း ထားမည်
                    }
                }
        );
    }

    private void updateMyanmarTabVisibility(
            org.json.JSONObject content
    ) {
        if (navMyanmarTab == null) {
            return;
        }

        boolean enabled =
                AppContentManager.isMyanmarEnabled(
                        content
                );

        navMyanmarTab.setVisibility(
                enabled ? View.VISIBLE : View.GONE
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
    // Search (JAV code / actress name, mosaic-only)
    // ------------------------------------------------------------------

    private boolean isSearchMode() {
        return searchQuery != null;
    }

    private boolean isActressMode() {
        return !isSearchMode()
                && actressUrl != null
                && !actressUrl.isEmpty();
    }

    private void toggleSearchBar() {
        if (
                searchBar.getVisibility() == View.VISIBLE
        ) {
            searchBar.setVisibility(View.GONE);
            hideKeyboard();

            if (isSearchMode()) {
                exitSearchMode();
            }
        } else {
            searchBar.setVisibility(View.VISIBLE);
            searchInput.requestFocus();
            showKeyboard();
        }
    }

    private void submitSearch() {
        String q =
                searchInput.getText() == null
                        ? ""
                        : searchInput.getText()
                                .toString().trim();

        if (q.isEmpty()) {
            Toast.makeText(
                    this,
                    "ရှာဖွေမှုစာသား ရိုက်ထည့်ပါ။",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        hideKeyboard();

        searchQuery = q;

        // search သည် actress mode မှ ထွက်သည်
        actressUrl = null;
        actressName = null;

        updateTitle();
        loadPage(1);
    }

    private void onSearchClear() {
        String current =
                searchInput.getText() == null
                        ? ""
                        : searchInput.getText()
                                .toString();

        if (!current.isEmpty()) {
            searchInput.setText("");
        }

        if (isSearchMode()) {
            exitSearchMode();
        }

        /*
         * ✕ သည် "ပိတ်" ခလုတ် — စာသားရှင်းပြီး
         * search mode မဟုတ်ရင် search bar ကို
         * ဖျောက်မည် (user expectation)။
         */
        searchBar.setVisibility(View.GONE);
        hideKeyboard();
    }

    private void exitSearchMode() {
        searchQuery = null;
        searchInput.setText("");
        updateTitle();
        loadPage(1);
    }

    private void updateTitle() {
        if (isSearchMode()) {
            titleText.setText("🔍 " + searchQuery);
        } else if (isActressMode()) {
            titleText.setText(actressName);
        } else if (isUncensoredMode()) {
            titleText.setText("Uncensored");
        } else if (isChineseMode()) {
            titleText.setText("Chinese AV");
        } else {
            titleText.setText("Reduce Mosaic");
        }
    }

    /*
     * Sort/filter dialog — listing mode only.
     * Search/actress mode မှ ထွက်ပြီး listing
     * မှာ sort အသုံးပြုမည်။
     */
    private void showSortDialog() {
        if (isSearchMode() || isActressMode()) {
            exitToListing();
        }

        int checked = 0;

        for (int i = 0; i < SORT_VALUES.length; i++) {
            if (SORT_VALUES[i].equals(sortParam)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this, R.style.CmflixSortDialog)
                .setTitle("Sort by")
                .setSingleChoiceItems(
                        new android.widget.ArrayAdapter<>(
                                this,
                                R.layout.item_sort_option,
                                SORT_LABELS
                        ),
                        checked,
                        (dialog, which) -> {
                            sortParam = SORT_VALUES[which];
                            dialog.dismiss();
                            updateFilterButtonState();
                            currentPage = 0;
                            videos.clear();
                            adapter.notifyDataSetChanged();
                            loadPage(1);
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    /*
     * Search/actress mode မှ listing သို့ ပြန်ထွက်သည်
     * (sort dialog မပြမီ)။
     */
    private void exitToListing() {
        searchQuery = null;
        searchInput.setText("");
        searchBar.setVisibility(View.GONE);
        actressUrl = null;
        actressName = null;
        hideKeyboard();
        updateTitle();
    }

    /*
     * Filter button state — active sort (Latest မဟုတ်)
     * ဆိုရင် gold tint ပြမည်။
     */
    private void updateFilterButtonState() {
        ImageView filterBtn =
                findViewById(R.id.javtifulFilterButton);

        if (filterBtn == null) {
            return;
        }

        if (sortParam == null || sortParam.isEmpty()) {
            filterBtn.clearColorFilter();
        } else {
            filterBtn.setColorFilter(
                    0xFFE8B93E,
                    android.graphics.PorterDuff.Mode.SRC_IN
            );
        }
    }

    private void showKeyboard() {
        try {
            InputMethodManager imm =
                    (InputMethodManager)
                            getSystemService(
                                    INPUT_METHOD_SERVICE
                            );

            if (imm != null) {
                imm.showSoftInput(
                        searchInput,
                        InputMethodManager.SHOW_IMPLICIT
                );
            }
        } catch (Exception ignored) {
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm =
                    (InputMethodManager)
                            getSystemService(
                                    INPUT_METHOD_SERVICE
                            );

            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(
                        getCurrentFocus()
                                .getWindowToken(),
                        0
                );
            }
        } catch (Exception ignored) {
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
                    findViewById(R.id.javtifulVpnHint);

            if (vpnHint != null) {
                vpnHint.setVisibility(View.VISIBLE);
            }
        }

        JavtifulClient.PageCallback callback =
                new JavtifulClient.PageCallback() {
                    @Override
                    public void onResult(
                            List<JavtifulClient.JavtifulVideo>
                                    newVideos,
                            boolean more
                    ) {
                        runOnUiThread(() -> {
                            isLoading = false;
                            progress.setVisibility(
                                    View.GONE
                            );

                            View vpnHint =
                                    findViewById(
                                            R.id.javtifulVpnHint
                                    );

                            if (vpnHint != null) {
                                vpnHint.setVisibility(
                                        View.GONE
                                );
                            }

                            if (page == 1) {
                                videos.clear();
                            }

                            /*
                             * Duplicate guard — server may
                             * repeat a page's content (e.g.
                             * actress pages repeat the last
                             * page beyond the end). Only
                             * append videos not already shown.
                             */
                            java.util.HashSet<String> seen =
                                    new java.util.HashSet<>();

                            for (
                                    JavtifulClient.JavtifulVideo v
                                            : videos
                            ) {
                                if (
                                        v != null
                                                && v.detailUrl
                                                        != null
                                ) {
                                    seen.add(v.detailUrl);
                                }
                            }

                            int added = 0;

                            for (
                                    JavtifulClient.JavtifulVideo v
                                            : newVideos
                            ) {
                                if (
                                        v == null
                                                || v.detailUrl
                                                        == null
                                                || seen.contains(
                                                        v.detailUrl
                                                )
                                ) {
                                    continue;
                                }

                                seen.add(v.detailUrl);
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
                                                            && added
                                                                    == 0
                                            );

                            updateCount();

                            if (videos.isEmpty()) {
                                showEmpty(
                                        isSearchMode()
                                                ? "ရှာမတွေ့ပါ။\n(\"" + searchQuery + "\")"
                                                : "ဗီဒီယို မရှိပါ။",
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

                            View vpnHint =
                                    findViewById(
                                            R.id.javtifulVpnHint
                                    );

                            if (vpnHint != null) {
                                vpnHint.setVisibility(
                                        View.GONE
                                );
                            }

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
                            } else {
                                Toast.makeText(
                                        JavtifulActivity.this,
                                        "နောက် page ရယူ၍မရပါ။\n"
                                                + detail,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }
                };

        if (isSearchMode()) {
            if (isChineseMode()) {
                JavtifulClient.searchChinese(
                        searchQuery,
                        page,
                        callback
                );
            } else if (isUncensoredMode()) {
                JavtifulClient.searchUncensored(
                        searchQuery,
                        page,
                        callback
                );
            } else {
                JavtifulClient.searchVideos(
                        searchQuery,
                        page,
                        callback
                );
            }
        } else if (isActressMode()) {
            JavtifulClient.getActressVideos(
                    actressUrl,
                    page,
                    callback
            );
        } else if (isChineseMode()) {
            JavtifulClient.fetchChinesePage(
                    page, sortParam, callback
            );
        } else if (isUncensoredMode()) {
            JavtifulClient.fetchUncensoredPage(
                    page, sortParam, callback
            );
        } else {
            JavtifulClient.fetchPage(
                    page, sortParam, callback
            );
        }
    }

    private void showEmpty(
            String message,
            boolean showRetry
    ) {
        emptyText.setText(message);
        emptyBox.setVisibility(View.VISIBLE);

        findViewById(R.id.javtifulRetryButton)
                .setVisibility(
                        showRetry ? View.VISIBLE : View.GONE
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
    // Detail
    // ------------------------------------------------------------------

    private void onVideoClick(
            JavtifulClient.JavtifulVideo video
    ) {
        if (video == null) {
            return;
        }

        try {
            Intent intent =
                    new Intent(
                            this,
                            JavtifulDetailActivity.class
                    );

            intent.putExtra(
                    JavtifulDetailActivity.EXTRA_TITLE,
                    video.title
            );
            intent.putExtra(
                    JavtifulDetailActivity.EXTRA_THUMB,
                    video.thumbUrl
            );
            intent.putExtra(
                    JavtifulDetailActivity.EXTRA_DETAIL_URL,
                    video.detailUrl
            );
            intent.putExtra(
                    JavtifulDetailActivity.EXTRA_DURATION,
                    video.duration
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
    // Adapter — 16:9 landscape cards
    // ------------------------------------------------------------------

    private final class VideoAdapter
            extends RecyclerView.Adapter<VideoAdapter.Holder> {

        private final int thumbHeightPx;

        VideoAdapter() {
            DisplayMetrics metrics =
                    getResources().getDisplayMetrics();

            float density = metrics.density;

            int horizontalPaddingPx =
                    (int) ((16 + 16) * density);

            int itemWidthPx =
                    (metrics.widthPixels - horizontalPaddingPx)
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
                                    R.layout.item_javtiful_video,
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
            JavtifulClient.JavtifulVideo video =
                    videos.get(position);

            holder.title.setText(video.title);

            if (
                    video.duration != null &&
                            !video.duration.isEmpty()
            ) {
                holder.duration.setText(video.duration);
                holder.duration.setVisibility(View.VISIBLE);
            } else {
                holder.duration.setVisibility(View.GONE);
            }

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
            final TextView duration;

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
                duration =
                        itemView.findViewById(
                                R.id.cardDuration
                        );
            }
        }
    }
}