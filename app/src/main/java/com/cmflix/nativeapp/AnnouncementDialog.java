package com.cmflix.nativeapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public final class AnnouncementDialog {

    private AnnouncementDialog() {
    }

    public static void show(
            Activity activity,
            String title,
            String message
    ) {
        if (
                activity == null ||
                activity.isFinishing() ||
                activity.isDestroyed()
        ) {
            return;
        }

        Dialog dialog =
                new Dialog(activity);

        dialog.setContentView(
                R.layout.dialog_app_announcement
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
                    WindowManager.LayoutParams
                            .FLAG_DIM_BEHIND
            );

            WindowManager.LayoutParams attributes =
                    window.getAttributes();

            attributes.width =
                    WindowManager.LayoutParams
                            .MATCH_PARENT;

            attributes.height =
                    WindowManager.LayoutParams
                            .WRAP_CONTENT;

            attributes.dimAmount = 0.78f;

            window.setAttributes(
                    attributes
            );
        }

        TextView titleView =
                dialog.findViewById(
                        R.id.announcementTitle
                );

        TextView messageView =
                dialog.findViewById(
                        R.id.announcementMessage
                );

        ImageView icon =
                dialog.findViewById(
                        R.id.announcementIcon
                );

        Button closeButton =
                dialog.findViewById(
                        R.id.announcementClose
                );

        titleView.setText(
                title == null ||
                        title.trim().isEmpty()
                        ? "အသိပေးချက်"
                        : title.trim()
        );

        messageView.setText(
                message == null
                        ? ""
                        : message.trim()
        );

        closeButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        dialog.setOnShowListener(
                ignored ->
                        startAnimation(icon)
        );

        dialog.show();

        if (window != null) {
            int horizontalMargin =
                    dp(activity, 22);

            WindowManager.LayoutParams attributes =
                    window.getAttributes();

            attributes.width =
                    activity.getResources()
                            .getDisplayMetrics()
                            .widthPixels -
                            horizontalMargin * 2;

            attributes.height =
                    WindowManager.LayoutParams
                            .WRAP_CONTENT;

            window.setAttributes(attributes);
        }
    }

    private static void startAnimation(
            View icon
    ) {
        if (icon == null) {
            return;
        }

        icon.setScaleX(0.65f);
        icon.setScaleY(0.65f);
        icon.setAlpha(0f);
        icon.setTranslationY(28f);
        icon.setRotation(-9f);

        ObjectAnimator scaleX =
                ObjectAnimator.ofFloat(
                        icon,
                        View.SCALE_X,
                        0.65f,
                        1.08f,
                        1f
                );

        ObjectAnimator scaleY =
                ObjectAnimator.ofFloat(
                        icon,
                        View.SCALE_Y,
                        0.65f,
                        1.08f,
                        1f
                );

        ObjectAnimator alpha =
                ObjectAnimator.ofFloat(
                        icon,
                        View.ALPHA,
                        0f,
                        1f
                );

        ObjectAnimator translation =
                ObjectAnimator.ofFloat(
                        icon,
                        View.TRANSLATION_Y,
                        28f,
                        -5f,
                        0f
                );

        ObjectAnimator rotation =
                ObjectAnimator.ofFloat(
                        icon,
                        View.ROTATION,
                        -9f,
                        7f,
                        -4f,
                        0f
                );

        AnimatorSet set =
                new AnimatorSet();

        set.playTogether(
                scaleX,
                scaleY,
                alpha,
                translation,
                rotation
        );

        set.setDuration(720L);

        set.setInterpolator(
                new DecelerateInterpolator()
        );

        set.start();
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
