package com.cmflix.nativeapp;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar playerProgress;

    private boolean playbackFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        playerProgress = findViewById(R.id.playerProgress);

        String url =
                getIntent().getStringExtra("video_url");

        String type =
                getIntent().getStringExtra("video_type");

        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Video link မရှိပါ။",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        enterImmersive();

        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        );

        initializePlayer(url.trim(), type);
    }

    private void initializePlayer(
            String url,
            String type
    ) {
        playerProgress.setVisibility(View.VISIBLE);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        /*
         * PlayerView ရဲ့ default controller ကိုသုံးမယ်။
         * User screen ကိုထိရင် controls ပြပေးမယ်။
         */
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setKeepScreenOn(true);

        MediaItem.Builder mediaBuilder =
                new MediaItem.Builder()
                        .setUri(url);

        boolean isHls =
                "m3u8".equalsIgnoreCase(type)
                        || url.toLowerCase().contains(".m3u8");

        if (isHls) {
            mediaBuilder.setMimeType(
                    MimeTypes.APPLICATION_M3U8
            );
        } else if ("mp4".equalsIgnoreCase(type)) {
            mediaBuilder.setMimeType(
                    MimeTypes.VIDEO_MP4
            );
        }

        player.addListener(
                new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(
                            int playbackState
                    ) {
                        if (
                                playbackState
                                        == Player.STATE_BUFFERING
                        ) {
                            if (!playbackFailed) {
                                playerProgress.setVisibility(
                                        View.VISIBLE
                                );
                            }

                            return;
                        }

                        if (
                                playbackState
                                        == Player.STATE_READY
                                        || playbackState
                                        == Player.STATE_ENDED
                                        || playbackState
                                        == Player.STATE_IDLE
                        ) {
                            playerProgress.setVisibility(
                                    View.GONE
                            );
                        }
                    }

                    @Override
                    public void onIsPlayingChanged(
                            boolean isPlaying
                    ) {
                        if (isPlaying) {
                            playerProgress.setVisibility(
                                    View.GONE
                            );
                        }
                    }

                    @Override
                    public void onPlayerError(
                            PlaybackException error
                    ) {
                        playbackFailed = true;

                        playerProgress.setVisibility(View.GONE);
                        playerView.setKeepScreenOn(false);

                        String message =
                                error.getMessage() == null
                                        ? "Video ဖွင့်၍မရပါ။"
                                        : error.getMessage();

                        Toast.makeText(
                                PlayerActivity.this,
                                "Video ဖွင့်၍မရပါ။\n" + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        player.setMediaItem(mediaBuilder.build());
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private void enterImmersive() {
        Window window = getWindow();

        if (
                android.os.Build.VERSION.SDK_INT
                        >= android.os.Build.VERSION_CODES.R
        ) {
            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {
                controller.hide(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );

                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (playerView != null) {
            playerView.setPlayer(null);
        }

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}
