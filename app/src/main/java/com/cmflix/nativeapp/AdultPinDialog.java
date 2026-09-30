package com.cmflix.nativeapp;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/*
 * 18+ tab များဖွင့်ရာတွင် PIN တောင်းသော dialog။
 * App theme (dark) နှင့်လိုက်ဖက်အောင်
 * programmatically တည်ဆောက်ထားသည်။
 */
public final class AdultPinDialog {

    public interface Callback {
        void onUnlocked();
    }

    private AdultPinDialog() {
    }

    public static void show(
            Context context,
            Callback callback
    ) {
        Dialog dialog = new Dialog(context);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(
                            Color.TRANSPARENT
                    )
            );
        }

        LinearLayout container =
                new LinearLayout(context);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(context, 22),
                dp(context, 24),
                dp(context, 22),
                dp(context, 22)
        );

        container.setBackground(
                roundedBackground(
                        context,
                        "#181A21",
                        24,
                        "#363944"
                )
        );

        TextView title = new TextView(context);
        title.setText("🔒 18+ PIN Lock");
        title.setTextSize(19);
        title.setTextColor(Color.WHITE);
        title.setTypeface(
                title.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        title.setGravity(Gravity.CENTER);

        TextView message = new TextView(context);
        message.setText(
                "ဒီအပိုင်းကြည့်ဖို့ 4-digit PIN ထည့်ပါ။"
        );
        message.setTextSize(13);
        message.setTextColor(
                Color.parseColor("#A8ADB8")
        );
        message.setGravity(Gravity.CENTER);

        TextView errorText = new TextView(context);
        errorText.setText("");
        errorText.setTextSize(12);
        errorText.setTextColor(
                Color.parseColor("#FF7A7A")
        );
        errorText.setGravity(Gravity.CENTER);

        EditText pinInput = new EditText(context);

        pinInput.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams
                                .MATCH_PARENT,
                        dp(context, 54)
                )
        );

        pinInput.setHint("PIN");
        pinInput.setSingleLine(true);
        pinInput.setGravity(Gravity.CENTER);

        pinInput.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType
                                .TYPE_NUMBER_VARIATION_PASSWORD
        );

        pinInput.setFilters(
                new InputFilter[]{
                        new InputFilter.LengthFilter(4)
                }
        );

        pinInput.setTextColor(Color.WHITE);
        pinInput.setHintTextColor(
                Color.parseColor("#818692")
        );
        pinInput.setTextSize(22);
        pinInput.setLetterSpacing(0.35f);

        pinInput.setBackground(
                roundedBackground(
                        context,
                        "#22252E",
                        15,
                        "#3A3E49"
                )
        );

        Button unlockButton =
                createButton(
                        context,
                        "Unlock",
                        Color.parseColor("#079E86")
                );

        Button cancelButton =
                createButton(
                        context,
                        "Cancel",
                        Color.parseColor("#2A2D35")
                );

        container.addView(title);
        addTopMargin(
                context,
                container,
                message,
                6
        );
        addTopMargin(
                context,
                container,
                pinInput,
                15
        );
        addTopMargin(
                context,
                container,
                errorText,
                8
        );
        addTopMargin(
                context,
                container,
                unlockButton,
                12
        );
        addTopMargin(
                context,
                container,
                cancelButton,
                8
        );

        dialog.setContentView(container);

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        unlockButton.setOnClickListener(view -> {
            String pin =
                    pinInput.getText()
                            .toString()
                            .trim();

            if (
                    SecureCredentialStore
                            .verifyAdultPin(pin)
            ) {
                dialog.dismiss();
                callback.onUnlocked();
            } else {
                errorText.setText(
                        "PIN မှားနေပါတယ်။ ထပ်စမ်းကြည့်ပါ။"
                );
                pinInput.setText("");
                pinInput.requestFocus();
            }
        });

        dialog.show();

        Window shownWindow = dialog.getWindow();

        if (shownWindow != null) {
            int screenWidth =
                    context.getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int dialogWidth =
                    Math.min(
                            (int) (screenWidth * 0.84f),
                            dp(context, 370)
                    );

            shownWindow.setLayout(
                    dialogWidth,
                    ViewGroup.LayoutParams
                            .WRAP_CONTENT
            );

            android.view.WindowManager.LayoutParams
                    attributes =
                    shownWindow.getAttributes();

            attributes.dimAmount = 0.76f;
            shownWindow.setAttributes(attributes);

            shownWindow.addFlags(
                    android.view.WindowManager.LayoutParams
                            .FLAG_DIM_BEHIND
            );
        }

        pinInput.requestFocus();
    }

    private static Button createButton(
            Context context,
            String text,
            int color
    ) {
        Button button = new Button(context);

        button.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams
                                .MATCH_PARENT,
                        dp(context, 54)
                )
        );

        button.setMinHeight(0);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(color);
        background.setCornerRadius(dp(context, 16));

        button.setBackground(background);

        return button;
    }

    private static void addTopMargin(
            Context context,
            LinearLayout parent,
            android.view.View child,
            int marginTopDp
    ) {
        LinearLayout.LayoutParams params =
                child.getLayoutParams()
                        instanceof LinearLayout.LayoutParams
                        ? (LinearLayout.LayoutParams)
                        child.getLayoutParams()
                        : new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams
                                .MATCH_PARENT,
                        ViewGroup.LayoutParams
                                .WRAP_CONTENT
                );

        params.topMargin = dp(context, marginTopDp);
        child.setLayoutParams(params);
        parent.addView(child);
    }

    private static GradientDrawable roundedBackground(
            Context context,
            String fillColor,
            int cornerRadiusDp,
            String strokeColor
    ) {
        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.parseColor(fillColor)
        );

        background.setCornerRadius(
                dp(context, cornerRadiusDp)
        );

        background.setStroke(
                dp(context, 1),
                Color.parseColor(strokeColor)
        );

        return background;
    }

    private static int dp(
            Context context,
            int value
    ) {
        return Math.round(
                value *
                        context.getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
