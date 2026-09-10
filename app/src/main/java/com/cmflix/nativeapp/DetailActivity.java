package com.cmflix.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

public class DetailActivity extends AppCompatActivity {

    private ImageView backdrop;
    private TextView title;
    private TextView meta;
    private TextView overview;
    private Button playButton;
    private LinearLayout episodesContainer;

    private String firstVideoUrl = "";
    private String firstVideoType = "auto";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        backdrop = findViewById(R.id.backdrop);
        title = findViewById(R.id.detailTitle);
        meta = findViewById(R.id.detailMeta);
        overview = findViewById(R.id.detailOverview);
        playButton = findViewById(R.id.playButton);
        episodesContainer = findViewById(R.id.episodesContainer);

        String slug = getIntent().getStringExtra("slug");

        if (slug == null || slug.trim().isEmpty()) {
            finish();
            return;
        }

        playButton.setVisibility(View.GONE);

        ApiClient.get(
                "titles/" + ApiClient.encode(slug),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> bind(json.optJSONObject("item")));
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() ->
                                overview.setText(
                                        "Load failed: " + error.getMessage()
                                )
                        );
                    }
                }
        );
    }

    private void bind(JSONObject item) {
        if (item == null) {
            overview.setText("Movie not found.");
            return;
        }

        title.setText(item.optString("title", ""));

        String year = item.optString("year", "");
        String rating = item.optString("rating", "");
        String genres = item.optString("genres", "");

        StringBuilder metaText = new StringBuilder();

        if (!year.isEmpty()) {
            metaText.append(year);
        }

        if (!rating.isEmpty()) {
            if (metaText.length() > 0) {
                metaText.append("  •  ");
            }
            metaText.append("★ ").append(rating);
        }

        if (!genres.isEmpty()) {
            if (metaText.length() > 0) {
                metaText.append('\n');
            }
            metaText.append(genres);
        }

        meta.setText(metaText.toString());
        overview.setText(item.optString("overview", "No description."));

        Glide.with(this)
                .load(item.optString("backdrop_url", ""))
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(android.R.drawable.ic_menu_report_image)
                .into(backdrop);

        episodesContainer.removeAllViews();

        firstVideoUrl = "";
        firstVideoType = "auto";

        JSONArray episodes = item.optJSONArray("episodes");

        if (episodes != null && episodes.length() > 0) {
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject episode = episodes.optJSONObject(i);

                if (episode == null) {
                    continue;
                }

                String videoUrl = episode.optString("video_url", "");
                String videoType = episode.optString("video_type", "auto");

                if (firstVideoUrl.isEmpty() && !videoUrl.isEmpty()) {
                    firstVideoUrl = videoUrl;
                    firstVideoType = videoType;
                }

                TextView episodeButton =
                        (TextView) LayoutInflater.from(this).inflate(
                                R.layout.item_episode,
                                episodesContainer,
                                false
                        );

                String label =
                        "S" + episode.optInt("season_number", 1)
                                + " • E"
                                + episode.optInt("episode_number", 1);

                String episodeTitle =
                        episode.optString("episode_title", "");

                if (!episodeTitle.isEmpty()) {
                    label += "  " + episodeTitle;
                }

                episodeButton.setText(label);

                episodeButton.setOnClickListener(
                        view -> openPlayer(videoUrl, videoType)
                );

                episodesContainer.addView(episodeButton);
            }

            if (!firstVideoUrl.isEmpty()) {
                playButton.setText("PLAY FIRST EPISODE");
                playButton.setVisibility(View.VISIBLE);
            } else {
                playButton.setVisibility(View.GONE);
            }
        } else {
            firstVideoUrl = item.optString("video_url", "");
            firstVideoType = item.optString("video_type", "auto");

            if (!firstVideoUrl.isEmpty()) {
                playButton.setText("PLAY");
                playButton.setVisibility(View.VISIBLE);
            } else {
                playButton.setVisibility(View.GONE);
            }
        }

        playButton.setOnClickListener(
                view -> openPlayer(firstVideoUrl, firstVideoType)
        );
    }

    private void openPlayer(String url, String type) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("video_url", url);
        intent.putExtra("video_type", type);
        intent.putExtra("title", title.getText().toString());

        startActivity(intent);
    }
}
