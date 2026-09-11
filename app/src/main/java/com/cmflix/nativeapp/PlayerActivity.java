package com.cmflix.nativeapp;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends AppCompatActivity {

    private static final String STATE_RESIZE_MODE =
            "player_resize_mode";

    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar playerProgress;
    private TextView resizeButton;

    private boolean playbackFailed = false;

    /*
     * 0 = FIT
     * 1 = ZOOM
     * 2 = FILL
     */
    private int resizeModeIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        playerProgress = findViewById(R.id.playerProgress);
        resizeButton = findViewById(R.id.resizeButton);

        if (savedInstanceState != null) {
            resizeModeIndex = savedInstanceState.getInt(
                    STATE_RESIZE_MODE,
                    0
            );
        }

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

        setupResizeButton();
        applyResizeMode(false);
        initializePlayer(url.trim(), type);
    }

    private void setupResizeButton() {
    /*
     * PlayerView ရဲ့ မူရင်း playback controls
     * ပေါ်/ပျောက် အခြေအနေနဲ့ resize button ကို
     * အတူတူ ပေါ်/ပျောက်စေမယ်။
     */
    playerView.setControllerVisibilityListener(visibility -> {
        resizeButton.setVisibility(
                visibility == View.VISIBLE
                        ? View.VISIBLE
                        : View.GONE
        );
    });

    /*
     * Listener တပ်လိုက်ချိန်မှာ controller ရဲ့
     * လက်ရှိအခြေအနေကို resize button မှာ ချက်ချင်းသက်ရောက်စေမယ်။
     */
    resizeButton.setVisibility(
            playerView.isControllerFullyVisible()
                    ? View.VISIBLE
                    : View.GONE
    );

    resizeButton.setOnClickListener(view -> {
        resizeModeIndex++;

        if (resizeModeIndex > 2) {
            resizeModeIndex = 0;
        }

        applyResizeMode(true);

        /*
         * Resize button နှိပ်ထားချိန်မှာ controller timeout ကို
         * ပြန်စပေးမယ်။ သတ်မှတ်ထားတဲ့အချိန်ပြည့်ရင်
         * controller နဲ့ resize button နှစ်ခုလုံး ပျောက်မယ်။
         */
        playerView.showController();

        enterImmersive();
    });
}


    private void applyResizeMode(boolean showMessage) {
        String label;
        String message;
        int resizeMode;

        switch (resizeModeIndex) {
            case 1:
                resizeMode =
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM;

                label = "ZOOM";
                message = "Zoom mode";
                break;

            case 2:
                resizeMode =
                        AspectRatioFrameLayout.RESIZE_MODE_FILL;

                label = "FILL";
                message = "Fill screen mode";
                break;

            case 0:
            default:
                resizeMode =
                        AspectRatioFrameLayout.RESIZE_MODE_FIT;

                label = "FIT";
                message = "Fit screen mode";
                break;
        }

        playerView.setResizeMode(resizeMode);
        resizeButton.setText(label);

        if (showMessage) {
            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void initializePlayer(
            String url,
            String type
    ) {
        playerProgress.setVisibility(View.VISIBLE);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

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
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        outState.putInt(
                STATE_RESIZE_MODE,
                resizeModeIndex
        );

        super.onSaveInstanceState(outState);
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
