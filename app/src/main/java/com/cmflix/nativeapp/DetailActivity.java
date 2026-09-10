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
    private String titleId = "";

    private boolean isFavorite = false;
    private boolean favoriteLoading = false;
    private boolean addFavoriteAfterLogin = false;

    private final ActivityResultLauncher<Intent>
            authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    result -> {
                        updateFavoriteText();

                        if (
                                result.getResultCode()
                                        == RESULT_OK
                        ) {
                            checkFavorite();

                            if (addFavoriteAfterLogin) {
                                addFavoriteAfterLogin = false;
                                setFavorite(true);
                            }
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApiClient.initialize(this);
        setContentView(R.layout.activity_detail);

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

genresContainer = findViewById(
        R.id.genresContainer
);

episodesContainer = findViewById(
        R.id.episodesContainer
);


        String slug =
                getIntent().getStringExtra("slug");

        if (
                slug == null ||
                slug.trim().isEmpty()
        ) {
            finish();
            return;
        }

        playButton.setVisibility(View.GONE);
        updateFavoriteText();

        favoriteButton.setOnClickListener(view -> {
            if (titleId.isEmpty() || favoriteLoading) {
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

            setFavorite(!isFavorite);
        });

        ApiClient.get(
                "titles/" + ApiClient.encode(slug),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            bind(json.optJSONObject("item"));
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            overview.setText(
                                    "Load failed: " +
                                            safeMessage(error)
                            );
                        });
                    }
                }
        );
    }

    private void bind(JSONObject item) {
        if (item == null) {
            overview.setText(
                    "Movie not found."
            );
            return;
        }

        titleId = item.optString("id", "");

        title.setText(
                item.optString("title", "")
        );

        String year =
                item.optString("year", "");

        String rating =
                item.optString("rating", "");

        String genres =
        item.optString("genres", "");

StringBuilder metaText =
        new StringBuilder();

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

shareButton.setEnabled(true);
shareButton.setAlpha(1f);


        overview.setText(
                item.optString(
                        "overview",
                        "ဇာတ်လမ်းအကျဉ်း မရှိသေးပါ။"
                )
        );

        Glide.with(this)
                .load(item.optString("backdrop_url", ""))
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
                "series".equalsIgnoreCase(
                        item.optString("category", "")
                );

        episodesContainer.removeAllViews();

        firstVideoUrl = "";
        firstVideoType = "auto";

        if (isSeries) {
            bindSeries(item);
        } else {
            bindMovie(item);
        }

        playButton.setOnClickListener(
                view -> openPlayer(
                        firstVideoUrl,
                        firstVideoType
                )
        );

        if (SessionManager.isLoggedIn()) {
            checkFavorite();
        } else {
            updateFavoriteText();
        }
    }

    private void bindMovie(JSONObject item) {
        /*
         * Movie ဖြစ်ရင် Episodes label/container ကို
         * လုံးဝဖျောက်မယ်။
         */
        episodesLabel.setVisibility(View.GONE);
        episodesContainer.setVisibility(View.GONE);

        firstVideoUrl =
                item.optString("video_url", "");

        firstVideoType =
                item.optString(
                        "video_type",
                        "auto"
                );

        if (!firstVideoUrl.isEmpty()) {
            playButton.setText("▶ PLAY");
            playButton.setVisibility(View.VISIBLE);
        } else {
            playButton.setVisibility(View.GONE);
        }
    }

    private void bindSeries(JSONObject item) {
        JSONArray episodes =
                item.optJSONArray("episodes");

        if (
                episodes == null ||
                episodes.length() == 0
        ) {
            episodesLabel.setVisibility(View.GONE);
            episodesContainer.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            return;
        }

        episodesLabel.setVisibility(View.VISIBLE);
        episodesContainer.setVisibility(View.VISIBLE);

        for (
                int index = 0;
                index < episodes.length();
                index++
        ) {
            JSONObject episode =
                    episodes.optJSONObject(index);

            if (episode == null) {
                continue;
            }

            String videoUrl =
                    episode.optString(
                            "video_url",
                            ""
                    );

            String videoType =
                    episode.optString(
                            "video_type",
                            "auto"
                    );

            if (
                    firstVideoUrl.isEmpty() &&
                    !videoUrl.isEmpty()
            ) {
                firstVideoUrl = videoUrl;
                firstVideoType = videoType;
            }

            TextView episodeButton =
                    (TextView)
                            LayoutInflater
                                    .from(this)
                                    .inflate(
                                            R.layout.item_episode,
                                            episodesContainer,
                                            false
                                    );

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

            String episodeTitle =
                    episode.optString(
                            "episode_title",
                            ""
                    );

            if (!episodeTitle.isEmpty()) {
                label += "  " + episodeTitle;
            }

            episodeButton.setText(label);

            episodeButton.setOnClickListener(
                    view -> openPlayer(
                            videoUrl,
                            videoType
                    )
            );

            episodesContainer.addView(
                    episodeButton
            );
        }

        if (!firstVideoUrl.isEmpty()) {
            playButton.setText(
                    "▶ PLAY FIRST EPISODE"
            );

            playButton.setVisibility(
                    View.VISIBLE
            );
        } else {
            playButton.setVisibility(
                    View.GONE
            );
        }
    }

    private void checkFavorite() {
        if (
                !SessionManager.isLoggedIn() ||
                titleId.isEmpty()
        ) {
            isFavorite = false;
            updateFavoriteText();
            return;
        }

        favoriteLoading = true;
        favoriteButton.setEnabled(false);
        shareButton.setOnClickListener(
        view -> shareCurrentTitle()
);

telegramButton.setOnClickListener(
        view -> openTelegramContact()
);


        ApiClient.get(
                "favorites",
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            favoriteLoading = false;
                            favoriteButton.setEnabled(true);

                            isFavorite = false;

                            JSONArray items =
                                    json.optJSONArray("items");

                            if (items != null) {
                                for (
                                        int index = 0;
                                        index < items.length();
                                        index++
                                ) {
                                    JSONObject item =
                                            items.optJSONObject(index);

                                    if (
                                            item != null &&
                                            titleId.equals(
                                                    item.optString(
                                                            "id",
                                                            ""
                                                    )
                                            )
                                    ) {
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
        if (
                favoriteLoading ||
                titleId.isEmpty()
        ) {
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

    if (
            genres == null ||
            genres.trim().isEmpty()
    ) {
        genresLabel.setVisibility(View.GONE);
        genresScroll.setVisibility(View.GONE);
        return;
    }

    genresLabel.setVisibility(View.VISIBLE);
    genresScroll.setVisibility(View.VISIBLE);

    String[] genreItems =
            genres.split(",");

    for (String genreValue : genreItems) {
        String genre =
                genreValue.trim();

        if (genre.isEmpty()) {
            continue;
        }

        TextView chip =
                new TextView(this);

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
                        Uri.parse(
                                "https://t.me/iqowoq"
                        )
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

    private void openPlayer(
            String url,
            String type
    ) {
        if (
                url == null ||
                url.trim().isEmpty()
        ) {
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
                type
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
