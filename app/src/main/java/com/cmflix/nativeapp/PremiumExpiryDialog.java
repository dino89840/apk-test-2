package com.cmflix.nativeapp;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public final class PremiumExpiryDialog {

    private static final long ONE_DAY_MS =
            24L * 60L * 60L * 1000L;

    private static final String TELEGRAM_URL =
            "https://t.me/iqowoq";

    /*
     * App process တစ်ကြိမ်အတွင်း dialog ထပ်ခါထပ်ခါ
     * မပေါ်စေရန် memory ထဲမှာပဲမှတ်ထားမယ်။
     *
     * App ကိုအပြည့်ပိတ်ပြီး ပြန်ဖွင့်သည့်အခါ
     * process အသစ်ဖြစ်သောကြောင့် ပြန်ပြပါမယ်။
     */
    private static boolean shownThisAppSession =
            false;

    private PremiumExpiryDialog() {
    }

    public static void showIfNeeded(
            Activity activity
    ) {
        if (
                shownThisAppSession ||
                activity == null ||
                activity.isFinishing() ||
                activity.isDestroyed() ||
                !SessionManager.isLoggedIn()
        ) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long vipUntil =
                SessionManager.getVipUntil();

        long remaining =
                vipUntil - now;

        /*
         * Premium အသက်ဝင်နေပြီး 24 နာရီ သို့မဟုတ်
         * ဒီထက်နည်းမှသာ warning ပြပါမယ်။
         */
        if (
                remaining <= 0L ||
                remaining > ONE_DAY_MS
        ) {
            return;
        }

        shownThisAppSession = true;

        show(
                activity,
                vipUntil
        );
    }

    private static void show(
            Activity activity,
            long vipUntil
    ) {
        Dialog dialog =
                new Dialog(activity);

        dialog.setContentView(
                R.layout.dialog_premium_expiry_warning
        );

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Window window =
                dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(
                            Color.TRANSPARENT
                    )
            );

            window.addFlags(
                    android.view.WindowManager
                            .LayoutParams
                            .FLAG_DIM_BEHIND
            );

            android.view.WindowManager.LayoutParams
                    attributes =
                    window.getAttributes();

            attributes.dimAmount = 0.84f;
            window.setAttributes(attributes);
        }

        View card =
                dialog.findViewById(
                        R.id.expiryWarningCard
                );

        View crown =
                dialog.findViewById(
                        R.id.expiryCrownIcon
                );

        View glow =
                dialog.findViewById(
                        R.id.expiryIconGlow
                );

        View sparkleLeft =
                dialog.findViewById(
                        R.id.expirySparkleLeft
                );

        View sparkleRight =
                dialog.findViewById(
                        R.id.expirySparkleRight
                );

        TextView expiryDateText =
                dialog.findViewById(
                        R.id.expiryDateText
                );

        TextView renewButton =
                dialog.findViewById(
                        R.id.expiryRenewButton
                );

        TextView laterButton =
                dialog.findViewById(
                        R.id.expiryLaterButton
                );

        String expiryDate =
                DateFormat
                        .getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT
                        )
                        .format(
                                new Date(vipUntil)
                        );

        expiryDateText.setText(
                "ကုန်ဆုံးမည့်အချိန်  •  " +
                        expiryDate
        );

        laterButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        renewButton.setOnClickListener(view -> {
            try {
                Intent intent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                        TELEGRAM_URL
                                )
                        );

                activity.startActivity(intent);
            } catch (Exception error) {
                Toast.makeText(
                        activity,
                        "Telegram ကို ဖွင့်၍မရပါ။",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        /*
         * Crown ကို ဖြည်းဖြည်းလှုပ်/ခုန်နေစေမယ်။
         */
        ObjectAnimator crownScaleX =
                ObjectAnimator.ofFloat(
                        crown,
                        View.SCALE_X,
                        0.90f,
                        1.12f,
                        0.90f
                );

        ObjectAnimator crownScaleY =
                ObjectAnimator.ofFloat(
                        crown,
                        View.SCALE_Y,
                        0.90f,
                        1.12f,
                        0.90f
                );

        ObjectAnimator crownRotation =
                ObjectAnimator.ofFloat(
                        crown,
                        View.ROTATION,
                        -7f,
                        7f,
                        -7f
                );

        crownScaleX.setDuration(1400L);
        crownScaleY.setDuration(1400L);
        crownRotation.setDuration(1800L);

        crownScaleX.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        crownScaleY.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        crownRotation.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        /*
         * Icon နောက်ခံ glow ကို pulse လုပ်မယ်။
         */
        ObjectAnimator glowScaleX =
                ObjectAnimator.ofFloat(
                        glow,
                        View.SCALE_X,
                        0.92f,
                        1.08f,
                        0.92f
                );

        ObjectAnimator glowScaleY =
                ObjectAnimator.ofFloat(
                        glow,
                        View.SCALE_Y,
                        0.92f,
                        1.08f,
                        0.92f
                );

        ObjectAnimator glowAlpha =
                ObjectAnimator.ofFloat(
                        glow,
                        View.ALPHA,
                        0.55f,
                        1f,
                        0.55f
                );

        glowScaleX.setDuration(1700L);
        glowScaleY.setDuration(1700L);
        glowAlpha.setDuration(1700L);

        glowScaleX.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        glowScaleY.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        glowAlpha.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        /*
         * Sparkle နှစ်ခုကို တစ်လှည့်စီမှိတ်တုတ်လုပ်မယ်။
         */
        ObjectAnimator leftSparkle =
                ObjectAnimator.ofFloat(
                        sparkleLeft,
                        View.ALPHA,
                        0.2f,
                        1f,
                        0.2f
                );

        ObjectAnimator rightSparkle =
                ObjectAnimator.ofFloat(
                        sparkleRight,
                        View.ALPHA,
                        1f,
                        0.2f,
                        1f
                );

        leftSparkle.setDuration(1100L);
        rightSparkle.setDuration(1100L);

        leftSparkle.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        rightSparkle.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        AnimatorSet animationSet =
                new AnimatorSet();

        animationSet.setInterpolator(
                new AccelerateDecelerateInterpolator()
        );

        animationSet.playTogether(
                crownScaleX,
                crownScaleY,
                crownRotation,
                glowScaleX,
                glowScaleY,
                glowAlpha,
                leftSparkle,
                rightSparkle
        );

        dialog.setOnDismissListener(
                ignored -> {
                    animationSet.cancel();

                    card.animate().cancel();
                    crown.animate().cancel();
                    glow.animate().cancel();
                    sparkleLeft.animate().cancel();
                    sparkleRight.animate().cancel();
                }
        );

        dialog.show();

        if (window != null) {
            int screenWidth =
                    activity.getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int dialogWidth =
        Math.min(
                (int) (
                        screenWidth * 0.85f
                ),
                dp(
                        activity,
                        380
                )
        );


            window.setLayout(
                    dialogWidth,
                    ViewGroup.LayoutParams
                            .WRAP_CONTENT
            );
        }

        /*
         * Dialog စပေါ်ချိန် entrance animation။
         */
        card.setAlpha(0f);
        card.setScaleX(0.86f);
        card.setScaleY(0.86f);
        card.setTranslationY(
                dp(activity, 24)
        );

        card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(360L)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .start();

        animationSet.start();
    }

    private static int dp(
            Activity activity,
            int value
    ) {
        return Math.round(
                value *
                        activity.getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
