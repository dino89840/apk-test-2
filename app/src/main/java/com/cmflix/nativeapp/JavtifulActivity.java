package com.cmflix.nativeapp;

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
import java.util.List;

/*
 * Javtiful (reducing mosaic) category — direct
 * javtiful.com scrape (NO proxy for now, user
 * will test first).
 *
 * - 16:9 landscape card grid, pagination
 * - Card tap → JavtifulDetailActivity
 * - Play + Download နှစ်မျိုးလုံး VIP only
 * - PIN မရှိပါ
 * - Stream URL cache မလုပ်ပါ — play/download
 *   မလုပ်မီ fresh resolve အမြဲလုပ်သည်
 * - Continue Watching မပါ (v1 simple)
 */
public class JavtifulActivity extends AppCompatActivity {

    private static final int GRID_SPAN = 2;

    private RecyclerView grid;
    private ProgressBar progress;
    private LinearLayout emptyBox;
    private TextView emptyText;
    private TextView countText;

    private VideoAdapter adapter;

    private final List<JavtifulClient.JavtifulVideo> videos =
            new ArrayList<>();

    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_javtiful);

        grid = findViewById(R.id.javtifulGrid);
        progress = findViewById(R.id.javtifulProgress);
        emptyBox = findViewById(R.id.javtifulEmptyBox);
        emptyText = findViewById(R.id.javtifulEmptyText);
        countText = findViewById(R.id.javtifulCountText);

        findViewById(R.id.javtifulBackButton)
                .setOnClickListener(view -> finish());

        findViewById(R.id.javtifulRetryButton)
                .setOnClickListener(view -> loadPage(1));

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

        loadPage(1);
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

        JavtifulClient.fetchPage(
                page,
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
                }
        );
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