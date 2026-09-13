package com.cmflix.nativeapp;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar playerProgress;
    private TextView resizeButton;

    private final Handler progressHandler =
            new Handler(Looper.getMainLooper());

    private boolean playbackFailed = false;
    private boolean playbackEnded = false;

    private int resizeModeIndex = 0;

    private String titleId = "";
    private long resumePosition = 0L;

    private final Runnable saveProgressTask =
            new Runnable() {
                @Override
                public void run() {
                    saveWatchProgress();

                    progressHandler.postDelayed(
                            this,
                            5000L
                    );
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        setContentView(R.layout.activity_player);

        playerView =
                findViewById(R.id.playerView);

        playerProgress =
                findViewById(R.id.playerProgress);

        resizeButton =
                findViewById(R.id.resizeButton);

        String url =
                getIntent().getStringExtra(
                        "video_url"
                );

        String type =
                getIntent().getStringExtra(
                        "video_type"
                );

        titleId =
                safe(
                        getIntent().getStringExtra(
                                "title_id"
                        )
                );

        resizeModeIndex =
                LocalStore.getResizeMode();

        if (
                resizeModeIndex < 0 ||
                resizeModeIndex > 2
        ) {
            resizeModeIndex = 0;
        }

        resumePosition =
                LocalStore.getResumePosition(
                        titleId
                );

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
                ActivityInfo
                        .SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        );

        setupResizeButton();
        applyResizeMode(false);

        initializePlayer(
                url.trim(),
                type
        );
    }

    private void setupResizeButton() {
        playerView.setControllerVisibilityListener(
                new PlayerView
                        .ControllerVisibilityListener() {
                    @Override
                    public void onVisibilityChanged(
                            int visibility
                    ) {
                        resizeButton.setVisibility(
                                visibility == View.VISIBLE
                                        ? View.VISIBLE
                                        : View.GONE
                        );
                    }
                }
        );

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

            LocalStore.saveResizeMode(
                    resizeModeIndex
            );

            applyResizeMode(true);
            playerView.showController();
            enterImmersive();
        });
    }

    private void applyResizeMode(
            boolean showMessage
    ) {
        int resizeMode;
        String label;
        String message;

        switch (resizeModeIndex) {
            case 1:
                resizeMode =
                        AspectRatioFrameLayout
                                .RESIZE_MODE_ZOOM;

                label = "ZOOM";
                message = "Zoom mode";
                break;

            case 2:
                resizeMode =
                        AspectRatioFrameLayout
                                .RESIZE_MODE_FILL;

                label = "FILL";
                message = "Fill screen mode";
                break;

            default:
                resizeMode =
                        AspectRatioFrameLayout
                                .RESIZE_MODE_FIT;

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
        playerProgress.setVisibility(
                View.VISIBLE
        );

        player =
                new ExoPlayer.Builder(this)
                        .build();

        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setKeepScreenOn(true);

        MediaItem.Builder mediaBuilder =
                new MediaItem.Builder()
                        .setUri(url);

        boolean isHls =
                "m3u8".equalsIgnoreCase(type) ||
                url.toLowerCase()
                        .contains(".m3u8");

        if (isHls) {
            mediaBuilder.setMimeType(
                    MimeTypes.APPLICATION_M3U8
            );
        } else if (
                "mp4".equalsIgnoreCase(type)
        ) {
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
                                playbackState ==
                                        Player.STATE_BUFFERING
                        ) {
                            if (!playbackFailed) {
                                playerProgress.setVisibility(
                                        View.VISIBLE
                                );
                            }

                            return;
                        }

                        if (
                                playbackState ==
                                        Player.STATE_READY
                        ) {
                            playerProgress.setVisibility(
                                    View.GONE
                            );

                            if (resumePosition > 0L) {
                                long duration =
                                        player.getDuration();

                                if (
                                        duration == C.TIME_UNSET ||
                                        resumePosition < duration
                                ) {
                                    player.seekTo(
                                            resumePosition
                                    );
                                }

                                resumePosition = 0L;
                            }
                        }

                        if (
                                playbackState ==
                                        Player.STATE_ENDED
                        ) {
                            playbackEnded = true;
                            playerProgress.setVisibility(
                                    View.GONE
                            );

                            saveWatchProgress();
                        }

                        if (
                                playbackState ==
                                        Player.STATE_IDLE
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

                        playerProgress.setVisibility(
                                View.GONE
                        );

                        playerView.setKeepScreenOn(
                                false
                        );

                        String message =
                                error.getMessage() == null
                                        ? "Video ဖွင့်၍မရပါ။"
                                        : error.getMessage();

                        Toast.makeText(
                                PlayerActivity.this,
                                "Video ဖွင့်၍မရပါ။\n" +
                                        message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        player.setMediaItem(
                mediaBuilder.build()
        );

        player.setPlayWhenReady(true);
        player.prepare();

        progressHandler.postDelayed(
                saveProgressTask,
                5000L
        );
    }

    private void saveWatchProgress() {
        if (
                player == null ||
                titleId.isEmpty() ||
                playbackFailed
        ) {
            return;
        }

        long position =
                Math.max(
                        0L,
                        player.getCurrentPosition()
                );

        long duration =
                player.getDuration();

        if (
                duration == C.TIME_UNSET ||
                duration < 0L
        ) {
            duration = 0L;
        }

        if (playbackEnded && duration > 0L) {
            position = duration;
        }

        if (
                position < 5000L &&
                !playbackEnded
        ) {
            return;
        }

        LocalStore.saveProgress(
                titleId,
                position,
                duration
        );
    }

    private void enterImmersive() {
        Window window = getWindow();

        if (
                android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.R
        ) {
            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {
                controller.hide(
                        WindowInsets.Type.statusBars() |
                        WindowInsets.Type.navigationBars()
                );

                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            window.getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
        }
    }

    private String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override
    protected void onPause() {
        saveWatchProgress();
        super.onPause();
    }

    @Override
    protected void onStop() {
        saveWatchProgress();

        if (player != null) {
            player.pause();
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(
                saveProgressTask
        );

        saveWatchProgress();

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
