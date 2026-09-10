package com.cmflix.nativeapp;

import android.content.Intent;
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
    private Button playButton;
    private Button favoriteButton;
    private LinearLayout episodesContainer;

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
        playButton = findViewById(R.id.playButton);
        favoriteButton = findViewById(R.id.favoriteButton);
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

        if (!genres.isEmpty()) {
            if (metaText.length() > 0) {
                metaText.append('\n');
            }

            metaText.append(genres);
        }

        meta.setText(metaText.toString());

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
        favoriteButton.setText("PLEASE WAIT…");

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
                    "♡ LOGIN TO FAVORITE"
            );
            return;
        }

        favoriteButton.setText(
                isFavorite
                        ? "♥ FAVORITED"
                        : "♡ FAVORITE"
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
