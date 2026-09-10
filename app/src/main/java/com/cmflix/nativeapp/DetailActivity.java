package com.cmflix.nativeapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

public class DetailActivity extends AppCompatActivity {

    private ImageView backdrop;

    private TextView title;
    private TextView meta;
    private TextView overview;
    private TextView episodesLabel;
    private TextView genresLabel;

    private Button playButton;
    private Button favoriteButton;
    private Button shareButton;
    private Button telegramButton;

    private LinearLayout genresContainer;
    private LinearLayout episodesContainer;

    private View genresScroll;

    private String firstVideoUrl = "";
    private String firstVideoType = "auto";
    private String firstEpisodeId = "";

    private String titleId = "";
    private String titleCategory = "";

    private boolean isFavorite = false;
    private boolean favoriteLoading = false;
    private boolean addFavoriteAfterLogin = false;

    private boolean playAfterLogin = false;
    private String pendingEpisodeId = "";

    private final ActivityResultLauncher<Intent> authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        updateFavoriteText();

                        if (result.getResultCode() != RESULT_OK) {
                            addFavoriteAfterLogin = false;
                            playAfterLogin = false;
                            pendingEpisodeId = "";
                            return;
                        }

                        /*
                         * Favorite ထည့်ဖို့ Login ဝင်ထားတာဆိုရင်
                         * checkFavorite() ကို အရင်မခေါ်ပါ။
                         *
                         * checkFavorite() က favoriteLoading = true
                         * လုပ်တာကြောင့် setFavorite() မလုပ်ဖြစ်နိုင်ပါ။
                         */
                        if (addFavoriteAfterLogin) {
                            addFavoriteAfterLogin = false;
                            setFavorite(true);
                        } else {
                            checkFavorite();
                        }

                        if (playAfterLogin) {
                            playAfterLogin = false;

                            String episodeId = pendingEpisodeId;
                            pendingEpisodeId = "";

                            requestProtectedPlayback(episodeId);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApiClient.initialize(this);
        setContentView(R.layout.activity_detail);

        bindViews();
        setupClickListeners();

        String slug = getIntent().getStringExtra("slug");

        if (slug == null || slug.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Movie slug မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        playButton.setVisibility(View.GONE);

        shareButton.setEnabled(false);
        shareButton.setAlpha(0.5f);

        updateFavoriteText();

        loadTitle(slug);
    }

    private void bindViews() {
        backdrop = findViewById(R.id.backdrop);

        title = findViewById(R.id.detailTitle);
        meta = findViewById(R.id.detailMeta);
        overview = findViewById(R.id.detailOverview);

        episodesLabel = findViewById(R.id.episodesLabel);
        genresLabel = findViewById(R.id.genresLabel);

        playButton = findViewById(R.id.playButton);
        favoriteButton = findViewById(R.id.favoriteButton);
        shareButton = findViewById(R.id.shareButton);
        telegramButton = findViewById(R.id.telegramButton);

        genresScroll = findViewById(R.id.genresScroll);

        genresContainer = findViewById(R.id.genresContainer);
        episodesContainer = findViewById(R.id.episodesContainer);
    }

    private void setupClickListeners() {
        favoriteButton.setOnClickListener(view -> {
            if (titleId.isEmpty() || favoriteLoading) {
                return;
            }

            if (!SessionManager.isLoggedIn()) {
                addFavoriteAfterLogin = true;

                authLauncher.launch(
                        new Intent(
                                DetailActivity.this,
                                AuthActivity.class
                        )
                );

                return;
            }

            setFavorite(!isFavorite);
        });

        shareButton.setOnClickListener(
                view -> shareCurrentTitle()
        );

        telegramButton.setOnClickListener(
                view -> openTelegramContact()
        );

        playButton.setOnClickListener(view ->
                playVideo(
                        firstVideoUrl,
                        firstVideoType,
                        firstEpisodeId
                )
        );
    }

    private void loadTitle(String slug) {
        ApiClient.get(
                "titles/" + ApiClient.encode(slug),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() ->
                                bind(json.optJSONObject("item"))
                        );
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            overview.setText(
                                    "Load failed: " + safeMessage(error)
                            );

                            playButton.setVisibility(View.GONE);
                            shareButton.setEnabled(false);
                            shareButton.setAlpha(0.5f);
                        });
                    }
                }
        );
    }

    private void bind(JSONObject item) {
        if (item == null) {
            overview.setText("Movie not found.");
            playButton.setVisibility(View.GONE);
            return;
        }

        titleId = item.optString("id", "");

        titleCategory = item.optString(
                "category",
                ""
        );

        String currentTitle = item.optString(
                "title",
                ""
        );

        title.setText(currentTitle);

        String year = item.optString(
                "year",
                ""
        );

        String rating = item.optString(
                "rating",
                ""
        );

        String genres = item.optString(
                "genres",
                ""
        );

        StringBuilder metaText = new StringBuilder();

        if (!year.isEmpty()) {
            metaText.append(year);
        }

        if (!rating.isEmpty()) {
            if (metaText.length() > 0) {
                metaText.append("  •  ");
            }

            metaText
                    .append("★ ")
                    .append(rating);
        }

        meta.setText(metaText.toString());

        bindGenres(genres);

        shareButton.setEnabled(!currentTitle.trim().isEmpty());
        shareButton.setAlpha(
                currentTitle.trim().isEmpty()
                        ? 0.5f
                        : 1f
        );

        overview.setText(
                item.optString(
                        "overview",
                        "ဇာတ်လမ်းအကျဉ်း မရှိသေးပါ။"
                )
        );

        Glide.with(this)
                .load(
                        item.optString(
                                "backdrop_url",
                                ""
                        )
                )
                .centerCrop()
                .thumbnail(0.25f)
                .dontAnimate()
                .placeholder(
                        android.R.drawable.ic_menu_report_image
                )
                .error(
                        android.R.drawable.ic_menu_report_image
                )
                .into(backdrop);

        boolean isSeries =
                "series".equalsIgnoreCase(titleCategory);

        episodesContainer.removeAllViews();

        firstVideoUrl = "";
        firstVideoType = "auto";
        firstEpisodeId = "";

        if (isSeries) {
            bindSeries(item);
        } else {
            bindMovie(item);
        }

        if (SessionManager.isLoggedIn()) {
            checkFavorite();
        } else {
            isFavorite = false;
            updateFavoriteText();
        }
    }

    private void bindMovie(JSONObject item) {
        episodesLabel.setVisibility(View.GONE);
        episodesContainer.setVisibility(View.GONE);

        firstEpisodeId = "";

        firstVideoUrl = item.optString(
                "video_url",
                ""
        );

        firstVideoType = item.optString(
                "video_type",
                "auto"
        );

        boolean hasVideo = item.optBoolean(
                "has_video",
                !firstVideoUrl.isEmpty()
        );

        if (hasVideo) {
            if ("lugyi".equalsIgnoreCase(titleCategory)) {
                playButton.setText("VIP PLAY");
            } else {
                playButton.setText("PLAY");
            }

            playButton.setEnabled(true);
            playButton.setVisibility(View.VISIBLE);
        } else {
            playButton.setVisibility(View.GONE);
        }
    }

    private void bindSeries(JSONObject item) {
        /*
         * မူရင်းကုဒ်ရဲ့ compile error ဖြစ်နေတဲ့နေရာကို
         * ဒီလို ခွဲရေးရပါမယ်။
         */
        firstEpisodeId = "";
        firstVideoUrl = "";
        firstVideoType = "auto";

        JSONArray episodes = item.optJSONArray("episodes");

        if (episodes == null || episodes.length() == 0) {
            episodesLabel.setVisibility(View.GONE);
            episodesContainer.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            return;
        }

        episodesLabel.setVisibility(View.VISIBLE);
        episodesContainer.setVisibility(View.VISIBLE);

        for (int index = 0; index < episodes.length(); index++) {
            JSONObject episode = episodes.optJSONObject(index);

            if (episode == null) {
                continue;
            }

            final String videoUrl = episode.optString(
                    "video_url",
                    ""
            );

            final String episodeId = episode.optString(
                    "id",
                    ""
            );

            final String videoType = episode.optString(
                    "video_type",
                    "auto"
            );

            boolean hasVideo = episode.optBoolean(
                    "has_video",
                    !videoUrl.isEmpty()
            );

            if (firstEpisodeId.isEmpty() && hasVideo) {
                firstEpisodeId = episodeId;
                firstVideoUrl = videoUrl;
                firstVideoType = videoType;
            }

            View episodeView = LayoutInflater
                    .from(this)
                    .inflate(
                            R.layout.item_episode,
                            episodesContainer,
                            false
                    );

            if (!(episodeView instanceof TextView)) {
                /*
                 * item_episode.xml ရဲ့ root view က TextView
                 * မဟုတ်ရင် ClassCastException မဖြစ်အောင်ပါ။
                 */
                continue;
            }

            TextView episodeButton = (TextView) episodeView;

            String label =
                    "S" +
                            episode.optInt(
                                    "season_number",
                                    1
                            ) +
                            " • E" +
                            episode.optInt(
                                    "episode_number",
                                    1
                            );

            String episodeTitle = episode.optString(
                    "episode_title",
                    ""
            );

            if (!episodeTitle.isEmpty()) {
                label += "  " + episodeTitle;
            }

            episodeButton.setText(label);
            episodeButton.setEnabled(hasVideo);
            episodeButton.setAlpha(hasVideo ? 1f : 0.5f);

            if (hasVideo) {
                episodeButton.setOnClickListener(view ->
                        playVideo(
                                videoUrl,
                                videoType,
                                episodeId
                        )
                );
            } else {
                episodeButton.setOnClickListener(null);
            }

            episodesContainer.addView(episodeButton);
        }

        if (!firstVideoUrl.isEmpty()) {
            if ("lugyi".equalsIgnoreCase(titleCategory)) {
                playButton.setText("VIP PLAY FIRST EPISODE");
            } else {
                playButton.setText("PLAY FIRST EPISODE");
            }

            playButton.setEnabled(true);
            playButton.setVisibility(View.VISIBLE);
        } else {
            playButton.setVisibility(View.GONE);
        }
    }

    private void checkFavorite() {
        if (!SessionManager.isLoggedIn() || titleId.isEmpty()) {
            isFavorite = false;
            updateFavoriteText();
            return;
        }

        favoriteLoading = true;
        favoriteButton.setEnabled(false);
        favoriteButton.setText("Please wait…");

        ApiClient.get(
                "favorites",
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            favoriteLoading = false;
                            favoriteButton.setEnabled(true);

                            isFavorite = false;

                            JSONArray items = json.optJSONArray("items");

                            if (items != null) {
                                for (
                                        int index = 0;
                                        index < items.length();
                                        index++
                                ) {
                                    JSONObject favoriteItem =
                                            items.optJSONObject(index);

                                    if (favoriteItem == null) {
                                        continue;
                                    }

                                    String favoriteTitleId =
                                            favoriteItem.optString(
                                                    "id",
                                                    ""
                                            );

                                    if (titleId.equals(favoriteTitleId)) {
                                        isFavorite = true;
                                        break;
                                    }
                                }
                            }

                            updateFavoriteText();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            favoriteLoading = false;
                            favoriteButton.setEnabled(true);
                            updateFavoriteText();
                        });
                    }
                }
        );
    }

    private void setFavorite(boolean shouldFavorite) {
        if (favoriteLoading || titleId.isEmpty()) {
            return;
        }

        if (!SessionManager.isLoggedIn()) {
            addFavoriteAfterLogin = true;

            authLauncher.launch(
                    new Intent(
                            this,
                            AuthActivity.class
                    )
            );

            return;
        }

        favoriteLoading = true;
        favoriteButton.setEnabled(false);
        favoriteButton.setText("Please wait…");

        ApiClient.Callback callback =
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            favoriteLoading = false;
                            favoriteButton.setEnabled(true);

                            isFavorite = shouldFavorite;
                            updateFavoriteText();

                            Toast.makeText(
                                    DetailActivity.this,
                                    shouldFavorite
                                            ? "Favorite မှတ်ပြီးပါပြီ။"
                                            : "Favorite မှ ဖယ်ရှားပြီးပါပြီ။",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            favoriteLoading = false;
                            favoriteButton.setEnabled(true);
                            updateFavoriteText();

                            Toast.makeText(
                                    DetailActivity.this,
                                    safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                };

        String path =
                "favorites/" +
                        ApiClient.encode(titleId);

        if (shouldFavorite) {
            ApiClient.post(
                    path,
                    new JSONObject(),
                    callback
            );
        } else {
            ApiClient.delete(
                    path,
                    callback
            );
        }
    }

    private void updateFavoriteText() {
        if (!SessionManager.isLoggedIn()) {
            favoriteButton.setText(
                    "♡  Login to add Favorite"
            );
            return;
        }

        favoriteButton.setText(
                isFavorite
                        ? "♥  Added to Favorites"
                        : "♡  Add to Favorites"
        );
    }

    private void bindGenres(String genres) {
        genresContainer.removeAllViews();

        if (genres == null || genres.trim().isEmpty()) {
            genresLabel.setVisibility(View.GONE);
            genresScroll.setVisibility(View.GONE);
            return;
        }

        genresLabel.setVisibility(View.VISIBLE);
        genresScroll.setVisibility(View.VISIBLE);

        String[] genreItems = genres.split(",");

        for (String genreValue : genreItems) {
            String genre = genreValue.trim();

            if (genre.isEmpty()) {
                continue;
            }

            TextView chip = new TextView(this);

            chip.setText(genre);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(13);
            chip.setSingleLine(true);

            chip.setPadding(
                    dp(14),
                    dp(8),
                    dp(14),
                    dp(8)
            );

            GradientDrawable background =
                    new GradientDrawable();

            background.setShape(
                    GradientDrawable.RECTANGLE
            );

            background.setColor(
                    Color.parseColor("#1A1D24")
            );

            background.setCornerRadius(
                    dp(50)
            );

            background.setStroke(
                    dp(1),
                    Color.parseColor("#3A404C")
            );

            chip.setBackground(background);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            params.setMarginEnd(dp(8));

            genresContainer.addView(
                    chip,
                    params
            );
        }

        if (genresContainer.getChildCount() == 0) {
            genresLabel.setVisibility(View.GONE);
            genresScroll.setVisibility(View.GONE);
        }
    }

    private void shareCurrentTitle() {
        String currentTitle =
                title.getText()
                        .toString()
                        .trim();

        if (currentTitle.isEmpty()) {
            return;
        }

        String shareText =
                "CMFLIX မှာ \"" +
                        currentTitle +
                        "\" ကို ကြည့်ရှုပါ။";

        Intent shareIntent =
                new Intent(Intent.ACTION_SEND);

        shareIntent.setType("text/plain");

        shareIntent.putExtra(
                Intent.EXTRA_SUBJECT,
                currentTitle
        );

        shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                shareText
        );

        startActivity(
                Intent.createChooser(
                        shareIntent,
                        "Share movie"
                )
        );
    }

    private void openTelegramContact() {
        try {
            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/iqowoq")
                    );

            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "Telegram contact ကို ဖွင့်၍မရပါ။",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    private void playVideo(
            String url,
            String type,
            String episodeId
    ) {
        if ("lugyi".equalsIgnoreCase(titleCategory)) {
            if (!SessionManager.isLoggedIn()) {
                playAfterLogin = true;

                pendingEpisodeId =
                        episodeId == null
                                ? ""
                                : episodeId;

                authLauncher.launch(
                        new Intent(
                                this,
                                AuthActivity.class
                        )
                );

                return;
            }

            requestProtectedPlayback(episodeId);
            return;
        }

        openPlayer(url, type);
    }

    private void requestProtectedPlayback(String episodeId) {
        if (titleId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Title ID မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        playButton.setEnabled(false);
        playButton.setText("Checking VIP...");

        JSONObject body = new JSONObject();

        try {
            body.put(
                    "titleId",
                    titleId
            );

            if (episodeId != null && !episodeId.isEmpty()) {
                body.put(
                        "episodeId",
                        episodeId
                );
            }
        } catch (Exception error) {
            restorePlayButtonText();

            Toast.makeText(
                    this,
                    safeMessage(error),
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        ApiClient.post(
                "play",
                body,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            restorePlayButtonText();

                            String videoUrl =
                                    json.optString(
                                            "videoUrl",
                                            ""
                                    );

                            String videoType =
                                    json.optString(
                                            "videoType",
                                            "auto"
                                    );

                            if (videoUrl.isEmpty()) {
                                Toast.makeText(
                                        DetailActivity.this,
                                        "Video link မရပါ။",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            openPlayer(
                                    videoUrl,
                                    videoType
                            );
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            restorePlayButtonText();

                            Toast.makeText(
                                    DetailActivity.this,
                                    safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void restorePlayButtonText() {
        playButton.setEnabled(true);

        boolean isSeries =
                episodesContainer.getVisibility() == View.VISIBLE;

        if ("lugyi".equalsIgnoreCase(titleCategory)) {
            playButton.setText(
                    isSeries
                            ? "VIP PLAY FIRST EPISODE"
                            : "VIP PLAY"
            );
        } else {
            playButton.setText(
                    isSeries
                            ? "PLAY FIRST EPISODE"
                            : "PLAY"
            );
        }
    }

    private void openPlayer(
            String url,
            String type
    ) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Video link မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent =
                new Intent(
                        this,
                        PlayerActivity.class
                );

        intent.putExtra(
                "video_url",
                url
        );

        intent.putExtra(
                "video_type",
                type == null || type.trim().isEmpty()
                        ? "auto"
                        : type
        );

        intent.putExtra(
                "title",
                title.getText().toString()
        );

        startActivity(intent);
    }

    private String safeMessage(Exception error) {
        if (
                error == null ||
                error.getMessage() == null ||
                error.getMessage().trim().isEmpty()
        ) {
            return "Request မအောင်မြင်ပါ။";
        }

        return error.getMessage();
    }
}
