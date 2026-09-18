package com.cmflix.nativeapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends AppCompatActivity {

    private static final long FINISH_DELAY_MS = 2100L;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean mainActivityOpened = false;

    private final Runnable openMainRunnable =
            this::openMainActivity;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        /*
         * Android 12+ system splash အတွက်။
         * Theme ထဲမှာ icon ကို transparent ထားထားမယ်။
         */
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_splash
        );

        View splashRoot =
                findViewById(
                        R.id.splashRoot
                );

        TextView welcomeText =
                findViewById(
                        R.id.splashWelcomeText
                );

        TextView brandText =
                findViewById(
                        R.id.splashBrandText
                );

        View accentLine =
                findViewById(
                        R.id.splashAccentLine
                );

        /*
         * CMFLIX စာကို red gradient ထည့်မယ်။
         */
        brandText.post(() -> {
            float width =
                    brandText.getPaint()
                            .measureText(
                                    brandText
                                            .getText()
                                            .toString()
                            );

            LinearGradient gradient =
                    new LinearGradient(
                            0f,
                            0f,
                            Math.max(width, 1f),
                            0f,
                            new int[]{
                                    Color.parseColor(
                                            "#FF6A72"
                                    ),
                                    Color.parseColor(
                                            "#E50914"
                                    ),
                                    Color.parseColor(
                                            "#9B0008"
                                    )
                            },
                            null,
                            Shader.TileMode.CLAMP
                    );

            brandText
                    .getPaint()
                    .setShader(gradient);

            brandText.invalidate();
        });

        /*
         * Animation မစခင် initial state။
         */
        welcomeText.setAlpha(0f);
        welcomeText.setTranslationY(32f);

        brandText.setAlpha(0f);
        brandText.setScaleX(0.76f);
        brandText.setScaleY(0.76f);
        brandText.setTranslationY(24f);

        accentLine.setAlpha(0f);
        accentLine.setScaleX(0f);

        ObjectAnimator welcomeAlpha =
                ObjectAnimator.ofFloat(
                        welcomeText,
                        View.ALPHA,
                        0f,
                        1f
                );

        ObjectAnimator welcomeMove =
                ObjectAnimator.ofFloat(
                        welcomeText,
                        View.TRANSLATION_Y,
                        32f,
                        0f
                );

        AnimatorSet welcomeAnimation =
                new AnimatorSet();

        welcomeAnimation.playTogether(
                welcomeAlpha,
                welcomeMove
        );

        welcomeAnimation.setDuration(550L);

        ObjectAnimator brandAlpha =
                ObjectAnimator.ofFloat(
                        brandText,
                        View.ALPHA,
                        0f,
                        1f
                );

        ObjectAnimator brandScaleX =
                ObjectAnimator.ofFloat(
                        brandText,
                        View.SCALE_X,
                        0.76f,
                        1.08f,
                        1f
                );

        ObjectAnimator brandScaleY =
                ObjectAnimator.ofFloat(
                        brandText,
                        View.SCALE_Y,
                        0.76f,
                        1.08f,
                        1f
                );

        ObjectAnimator brandMove =
                ObjectAnimator.ofFloat(
                        brandText,
                        View.TRANSLATION_Y,
                        24f,
                        0f
                );

        AnimatorSet brandAnimation =
                new AnimatorSet();

        brandAnimation.playTogether(
                brandAlpha,
                brandScaleX,
                brandScaleY,
                brandMove
        );

        brandAnimation.setDuration(720L);

        ObjectAnimator lineAlpha =
                ObjectAnimator.ofFloat(
                        accentLine,
                        View.ALPHA,
                        0f,
                        1f
                );

        ObjectAnimator lineScale =
                ObjectAnimator.ofFloat(
                        accentLine,
                        View.SCALE_X,
                        0f,
                        1f
                );

        AnimatorSet lineAnimation =
                new AnimatorSet();

        lineAnimation.playTogether(
                lineAlpha,
                lineScale
        );

        lineAnimation.setDuration(450L);

        AnimatorSet introAnimation =
                new AnimatorSet();

        introAnimation.playSequentially(
                welcomeAnimation,
                brandAnimation,
                lineAnimation
        );

        introAnimation.setInterpolator(
                new DecelerateInterpolator(
                        1.7f
                )
        );

        introAnimation.start();

        /*
         * နောက်ဆုံးမှာ splash တစ်ခုလုံး fade + zoom out။
         */
        handler.postDelayed(
                () -> {
                    splashRoot.animate()
                            .alpha(0f)
                            .scaleX(1.04f)
                            .scaleY(1.04f)
                            .setDuration(350L)
                            .setInterpolator(
                                    new DecelerateInterpolator()
                            )
                            .setListener(
                                    new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(
                                                Animator animation
                                        ) {
                                            openMainActivity();
                                        }
                                    }
                            )
                            .start();
                },
                FINISH_DELAY_MS - 350L
        );

        /*
         * Animation callback တစ်ခုခုပျက်သွားသော်လည်း
         * main screen ဆက်ဖွင့်စေရန် fallback။
         */
        handler.postDelayed(
                openMainRunnable,
                FINISH_DELAY_MS + 150L
        );
    }

    private void openMainActivity() {
        if (mainActivityOpened) {
            return;
        }

        mainActivityOpened = true;

        handler.removeCallbacks(
                openMainRunnable
        );

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        startActivity(intent);
        overridePendingTransition(0, 0);

        finish();
    }

    @Override
    public void onBackPressed() {
        /*
         * Splash ပြနေချိန် back နှိပ်၍
         * app flow ပျက်မသွားစေရန်။
         */
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(
                null
        );

        super.onDestroy();
    }
}
