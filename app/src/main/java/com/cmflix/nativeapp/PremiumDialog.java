package com.cmflix.nativeapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public final class PremiumDialog {

    private static final String TELEGRAM_USERNAME = "iqowoq";
    private static final String TELEGRAM_URL =
            "https://t.me/" + TELEGRAM_USERNAME;

    private PremiumDialog() {
    }

    public static void show(Activity activity) {
        if (
                activity == null ||
                activity.isFinishing() ||
                activity.isDestroyed()
        ) {
            return;
        }

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_premium_required);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );

            window.addFlags(
                    android.view.WindowManager.LayoutParams
                            .FLAG_DIM_BEHIND
            );

            android.view.WindowManager.LayoutParams attributes =
                    window.getAttributes();

            attributes.dimAmount = 0.78f;
            window.setAttributes(attributes);
        }

        TextView telegramButton =
                dialog.findViewById(
                        R.id.premiumTelegramButton
                );

        TextView closeButton =
                dialog.findViewById(
                        R.id.premiumCloseButton
                );

        telegramButton.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(TELEGRAM_URL)
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

        closeButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        dialog.show();

        if (window != null) {
            int screenWidth =
                    activity.getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int dialogWidth =
                    Math.min(
                            (int) (screenWidth * 0.88f),
                            dp(activity, 390)
                    );

            window.setLayout(
                    dialogWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
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
