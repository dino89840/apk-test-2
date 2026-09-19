package com.cmflix.nativeapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.PersistableBundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

public final class NewAccountDialog {

    private NewAccountDialog() {
    }

    public static void show(
            Activity activity,
            String username,
            String password,
            Runnable onFinished
    ) {
        if (
                activity == null ||
                activity.isFinishing() ||
                activity.isDestroyed()
        ) {
            if (onFinished != null) {
                onFinished.run();
            }

            return;
        }

        Dialog dialog = new Dialog(activity);

        dialog.setContentView(
                R.layout.dialog_new_account
        );

        /*
         * User က credential ကိုမှတ်သားပြီး
         * confirmation button နှိပ်မှပိတ်မယ်။
         */
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        Window window = dialog.getWindow();

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

            attributes.dimAmount = 0.86f;
            window.setAttributes(attributes);
        }

        View card =
                dialog.findViewById(
                        R.id.newAccountCard
                );

        View iconGlow =
                dialog.findViewById(
                        R.id.newAccountIconGlow
                );

        TextView usernameText =
                dialog.findViewById(
                        R.id.newAccountUsername
                );

        TextView passwordText =
                dialog.findViewById(
                        R.id.newAccountPassword
                );

        TextView copyUsername =
                dialog.findViewById(
                        R.id.copyUsernameButton
                );

        TextView copyPassword =
                dialog.findViewById(
                        R.id.copyPasswordButton
                );

        TextView doneButton =
                dialog.findViewById(
                        R.id.newAccountDoneButton
                );

        usernameText.setText(username);
        passwordText.setText(password);

        copyUsername.setOnClickListener(view ->
                copyToClipboard(
                        activity,
                        "CMFLIX username",
                        username,
                        false
                )
        );

        copyPassword.setOnClickListener(view ->
                copyToClipboard(
                        activity,
                        "CMFLIX password",
                        password,
                        true
                )
        );

        doneButton.setOnClickListener(view -> {
            dialog.dismiss();

            if (onFinished != null) {
                onFinished.run();
            }
        });

        dialog.setOnDismissListener(ignored -> {
            card.animate().cancel();
            iconGlow.animate().cancel();
        });

        dialog.show();

        if (window != null) {
            int screenWidth =
                    activity.getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int width =
        Math.min(
                (int) (screenWidth * 0.86f),
                dp(activity, 390)
        );


            window.setLayout(
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        card.setAlpha(0f);
        card.setScaleX(0.82f);
        card.setScaleY(0.82f);
        card.setTranslationY(
                dp(activity, 32)
        );

        card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(420L)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .start();

        ObjectAnimator glowX =
                ObjectAnimator.ofFloat(
                        iconGlow,
                        View.SCALE_X,
                        0.92f,
                        1.10f,
                        0.92f
                );

        ObjectAnimator glowY =
                ObjectAnimator.ofFloat(
                        iconGlow,
                        View.SCALE_Y,
                        0.92f,
                        1.10f,
                        0.92f
                );

        ObjectAnimator glowAlpha =
                ObjectAnimator.ofFloat(
                        iconGlow,
                        View.ALPHA,
                        0.5f,
                        1f,
                        0.5f
                );

        glowX.setDuration(1500L);
        glowY.setDuration(1500L);
        glowAlpha.setDuration(1500L);

        glowX.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        glowY.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        glowAlpha.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        AnimatorSet glowSet =
                new AnimatorSet();

        glowSet.playTogether(
                glowX,
                glowY,
                glowAlpha
        );

        glowSet.setInterpolator(
                new AccelerateDecelerateInterpolator()
        );

        glowSet.start();

        dialog.setOnDismissListener(ignored -> {
            glowSet.cancel();
            card.animate().cancel();
            iconGlow.animate().cancel();
        });
    }

    private static void copyToClipboard(
            Activity activity,
            String label,
            String value,
            boolean sensitive
    ) {
        ClipboardManager manager =
                (ClipboardManager)
                        activity.getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );

        if (manager == null) {
            Toast.makeText(
                    activity,
                    "Clipboard အသုံးပြု၍မရပါ။",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        ClipData clip =
                ClipData.newPlainText(
                        label,
                        value == null ? "" : value
                );

        /*
         * Android 13+ clipboard preview မှာ
         * password ကို plain text မပြစေရန်။
         */
        if (
                sensitive &&
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU
        ) {
            PersistableBundle extras =
                    new PersistableBundle();

            extras.putBoolean(
                    ClipDescription.EXTRA_IS_SENSITIVE,
                    true
            );

            clip.getDescription()
                    .setExtras(extras);
        }

        manager.setPrimaryClip(clip);

        Toast.makeText(
                activity,
                sensitive
                        ? "Password copy လုပ်ပြီးပါပြီ။"
                        : "Username copy လုပ်ပြီးပါပြီ။",
                Toast.LENGTH_SHORT
        ).show();
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
