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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/*
 * "Free Porn" category — redtube.com direct scrape.
 * (UI label is "Free Porn"; the word "redtube" never
 * appears in user-visible strings.)
 *
 * - 16:9 landscape card grid, infinite-scroll pagination
 * - Search bar (toggle) — /?search={q}&page=N
 * - Card tap → RedtubeDetailActivity
 * - Play + Download: FREE (no VIP), but LOGIN required.
 *   Not logged in → AuthActivity prompt; after login
 *   returns, user must tap Play/Download again manually
 *   (no auto-resume).
 * - PIN မရှိပါ
 * - Stream URL cache မလုပ်ပါ — play/download
 *   မလုပ်မီ fresh resolve အမြဲလုပ်သည်
 */
public class RedtubeActivity extends AppCompatActivity {

    private static final int GRID_SPAN = 2;

    private RecyclerView grid;
    private ProgressBar progress;
    private LinearLayout emptyBox;
    private TextView emptyText;
    private TextView countText;
    private TextView titleText;
    private LinearLayout searchBar;
    private EditText searchInput;

    private View navMyanmarTab;

    private VideoAdapter adapter;

    private final List<RedtubeClient.RedtubeVideo> videos =
            new ArrayList<>();

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;

    /*
     * null = normal listing. non-null = search mode.
     */
    private String searchQuery = null;

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

        titleText.setText("Free Porn");

        findViewById(R.id.javtifulBackButton)
                .setOnClickListener(view -> finish());

        findViewById(R.id.javtifulRetryButton)
                .setOnClickListener(view -> loadPage(1));

        findViewById(R.id.javtifulSearchToggle)
                .setOnClickListener(view -> toggleSearchBar());

        View filterButton =
                findViewById(R.id.javtifulFilterButton);
        if (filterButton != null) {
            filterButton.setVisibility(View.GONE);
        }

        findViewById(R.id.javtifulSearchClear)
                .setOnClickListener(view -> onSearchClear());

        searchInput.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (actionId
                            == EditorInfo.IME_ACTION_SEARCH) {
                        submitSearch();
                        return true;
                    }
                    return false;
                }
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
                                layoutManager.getItemCount();

                        int lastVisible =
                                layoutManager
                                        .findLastVisibleItemPosition();

                        if (!isLoading
                                && hasMore
                                && total > 0
                                && lastVisible >= total - 4) {
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
     * 18+ tab ကို active (gold) ပြမည်။
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
                                        MyanmarHubActivity.class
                                )
                        )
        );

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
    // Search
    // ------------------------------------------------------------------

    private void toggleSearchBar() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
            hideKeyboard();
        } else {
            searchBar.setVisibility(View.VISIBLE);
            searchInput.requestFocus();
            showKeyboard();
        }
    }

    private void submitSearch() {
        String q = searchInput.getText().toString().trim();

        if (q.isEmpty()) {
            Toast.makeText(
                    this,
                    "ရှာဖွေမှုစာသား ရိုက်ထည့်ပါ။",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        searchQuery = q;
        hideKeyboard();
        loadPage(1);
    }

    private void onSearchClear() {
        searchInput.setText("");
        exitSearchMode();
    }

    private void exitSearchMode() {
        searchQuery = null;
        searchBar.setVisibility(View.GONE);
        hideKeyboard();
        loadPage(1);
    }

    private void showKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE
                );

        if (imm != null) {
            imm.showSoftInput(
                    searchInput,
                    InputMethodManager.SHOW_IMPLICIT
            );
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE
                );

        if (imm != null) {
            imm.hideSoftInputFromWindow(
                    searchInput.getWindowToken(), 0
            );
        }
    }

    // ------------------------------------------------------------------
    // Listing
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

        RedtubeClient.PageCallback callback =
                new RedtubeClient.PageCallback() {
                    @Override
                    public void onResult(
                            List<RedtubeClient.RedtubeVideo>
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

                            HashSet<String> seen =
                                    new HashSet<>();

                            for (RedtubeClient.RedtubeVideo v
                                    : videos) {
                                if (v != null
                                        && v.detailUrl != null) {
                                    seen.add(v.detailUrl);
                                }
                            }

                            for (RedtubeClient.RedtubeVideo v
                                    : newVideos) {
                                if (v != null
                                        && v.detailUrl != null
                                        && !seen.contains(
                                                v.detailUrl)) {
                                    videos.add(v);
                                    seen.add(v.detailUrl);
                                }
                            }

                            currentPage = page;
                            hasMore = more;

                            adapter.notifyDataSetChanged();
                            updateCount();

                            if (videos.isEmpty()) {
                                showEmpty(
                                        searchQuery != null
                                                ? "ရှာမတွေ့ပါ။"
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

                            if (videos.isEmpty()) {
                                showEmpty(
                                        "ရယူ၍မရပါ။\nပြန်စမ်းကြည့်ပါ။",
                                        true
                                );
                            } else {
                                Toast.makeText(
                                        RedtubeActivity.this,
                                        "ရယူ၍မရပါ။",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
                    }
                };

        if (searchQuery != null) {
            RedtubeClient.searchVideos(
                    searchQuery, page, callback
            );
        } else {
            RedtubeClient.fetchPage(page, callback);
        }
    }

    private void showEmpty(String message, boolean showRetry) {
        emptyBox.setVisibility(View.VISIBLE);
        emptyText.setText(message);

        View retryButton =
                findViewById(R.id.javtifulRetryButton);

        if (retryButton != null) {
            retryButton.setVisibility(
                    showRetry ? View.VISIBLE : View.GONE
            );
        }
    }

    private void updateCount() {
        if (countText != null) {
            countText.setText(
                    videos.isEmpty()
                            ? ""
                            : videos.size() + " videos"
            );
        }
    }

    private void onVideoClick(
            RedtubeClient.RedtubeVideo video
    ) {
        Intent intent =
                new Intent(this, RedtubeDetailActivity.class);

        intent.putExtra(
                RedtubeDetailActivity.EXTRA_TITLE,
                video.title
        );
        intent.putExtra(
                RedtubeDetailActivity.EXTRA_THUMB,
                video.thumbUrl
        );
        intent.putExtra(
                RedtubeDetailActivity.EXTRA_DETAIL_URL,
                video.detailUrl
        );
        intent.putExtra(
                RedtubeDetailActivity.EXTRA_DURATION,
                video.duration
        );

        startActivity(intent);
    }

    // ------------------------------------------------------------------
    // Adapter
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
            RedtubeClient.RedtubeVideo video =
                    videos.get(position);

            holder.title.setText(video.title);

            if (video.duration != null
                    && !video.duration.isEmpty()) {
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
