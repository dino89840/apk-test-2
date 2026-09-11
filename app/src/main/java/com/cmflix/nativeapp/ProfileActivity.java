package com.cmflix.nativeapp;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

public class ProfileActivity
        extends AppCompatActivity {

    private TextView usernameText;
    private TextView accountStatusText;
    private TextView emailText;
    private TextView pointText;
    private TextView planText;
    private TextView expiryText;

    private Button changePasswordButton;
    private Button logoutButton;

    private ProgressBar progress;

    private boolean logoutLoading = false;
    private boolean passwordLoading = false;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        ApiClient.initialize(this);
        setContentView(R.layout.activity_profile);

        bindViews();
        bindCachedProfile();
        setupClickListeners();
        loadProfile();
    }

    private void bindViews() {
        usernameText =
                findViewById(R.id.profileUsername);

        accountStatusText =
                findViewById(R.id.profileAccountStatus);

        emailText =
                findViewById(R.id.profileEmail);

        pointText =
                findViewById(R.id.profilePoint);

        planText =
                findViewById(R.id.profilePlan);

        expiryText =
                findViewById(R.id.profileExpiry);

        changePasswordButton =
                findViewById(R.id.changePasswordButton);

        logoutButton =
                findViewById(R.id.profileLogoutButton);

        progress =
                findViewById(R.id.profileProgress);

        TextView backButton =
                findViewById(R.id.profileBackButton);

        backButton.setOnClickListener(
                view -> finish()
        );
    }

    private void setupClickListeners() {
        changePasswordButton.setOnClickListener(
                view -> showChangePasswordDialog()
        );

        logoutButton.setOnClickListener(
                view -> showLogoutDialog()
        );
    }

    private void bindCachedProfile() {
        String username =
                SessionManager.getUsername();

        String email =
                SessionManager.getEmail();

        long vipUntil =
                SessionManager.getVipUntil();

        usernameText.setText(
                username == null || username.trim().isEmpty()
                        ? "ACCOUNT"
                        : username
        );

        emailText.setText(
                email == null || email.trim().isEmpty()
                        ? "—"
                        : email
        );

        bindVipState(vipUntil);
    }

    private void loadProfile() {
        progress.setVisibility(View.VISIBLE);

        ApiClient.get(
                "auth/me",
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(
                            JSONObject json
                    ) {
                        runOnUiThread(() -> {
                            progress.setVisibility(View.GONE);

                            JSONObject user =
                                    json.optJSONObject("user");

                            if (user == null) {
                                SessionManager.clear();
                                setResult(RESULT_OK);
                                finish();
                                return;
                            }

                            long vipUntil =
                                    user.optLong(
                                            "vipUntil",
                                            0L
                                    );

                            String username =
                                    user.optString(
                                            "username",
                                            SessionManager
                                                    .getUsername()
                                    );

                            String email =
                                    user.optString(
                                            "email",
                                            SessionManager
                                                    .getEmail()
                                    );

                            SessionManager.saveAuth(
                                    json.optString(
                                            "csrf",
                                            SessionManager
                                                    .getCsrf()
                                    ),
                                    username,
                                    email,
                                    vipUntil
                            );

                            usernameText.setText(
                                    username.trim().isEmpty()
                                            ? "ACCOUNT"
                                            : username
                            );

                            emailText.setText(
                                    email.trim().isEmpty()
                                            ? "—"
                                            : email
                            );

                            /*
                             * Backend မှာ point သို့မဟုတ်
                             * points နှစ်မျိုးထဲက ဘာပေးပေးဖတ်မယ်။
                             */
                            int point =
                                    user.has("points")
                                            ? user.optInt(
                                                    "points",
                                                    0
                                            )
                                            : user.optInt(
                                                    "point",
                                                    0
                                            );

                            pointText.setText(
                                    String.valueOf(
                                            Math.max(0, point)
                                    )
                            );

                            bindVipState(vipUntil);
                        });
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        runOnUiThread(() -> {
                            progress.setVisibility(View.GONE);

                            Toast.makeText(
                                    ProfileActivity.this,
                                    safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void bindVipState(long vipUntil) {
        boolean active =
                vipUntil > System.currentTimeMillis();

        if (active) {
            accountStatusText.setText(
                    "★  Premium member"
            );

            planText.setText(
                    SessionManager.getPremiumLabel()
            );

            expiryText.setText(
                    DateFormat
                            .getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT
                            )
                            .format(new Date(vipUntil))
            );

            accountStatusText.setTextColor(
                    Color.parseColor("#FFF2A8")
            );
        } else {
            accountStatusText.setText(
                    "✕  Premium မရှိသေးပါ"
            );

            planText.setText("Free Plan");
            expiryText.setText("Expired");

            accountStatusText.setTextColor(
                    Color.parseColor("#FFD2D2")
            );
        }
    }

    private void showChangePasswordDialog() {
        if (passwordLoading) {
            return;
        }

        Dialog dialog = createBaseDialog();

        LinearLayout container =
                createDialogContainer();

        TextView title = createText(
                "Change Password",
                21,
                Color.WHITE,
                true
        );

        TextView message = createText(
                "လက်ရှိ Password နဲ့ Password အသစ်ကို ထည့်ပါ။",
                13,
                Color.parseColor("#A8ADB8"),
                false
        );

        EditText currentPassword =
                createPasswordInput(
                        "Current password"
                );

        EditText newPassword =
                createPasswordInput(
                        "New password"
                );

        EditText confirmPassword =
                createPasswordInput(
                        "Confirm new password"
                );

        Button submitButton = createDialogButton(
                "Change Password",
                Color.parseColor("#079E86")
        );

        Button cancelButton = createDialogButton(
                "Cancel",
                Color.parseColor("#2A2D35")
        );

        container.addView(title);
        addTopMargin(container, message, 8);
        addTopMargin(container, currentPassword, 20);
        addTopMargin(container, newPassword, 12);
        addTopMargin(container, confirmPassword, 12);
        addTopMargin(container, submitButton, 20);
        addTopMargin(container, cancelButton, 10);

        dialog.setContentView(container);

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        submitButton.setOnClickListener(view ->
                changePassword(
                        dialog,
                        currentPassword,
                        newPassword,
                        confirmPassword,
                        submitButton,
                        cancelButton
                )
        );

        showSizedDialog(dialog);
    }

    private void changePassword(
            Dialog dialog,
            EditText currentInput,
            EditText newInput,
            EditText confirmInput,
            Button submitButton,
            Button cancelButton
    ) {
        if (passwordLoading) {
            return;
        }

        String current =
                currentInput.getText()
                        .toString()
                        .trim();

        String next =
                newInput.getText()
                        .toString();

        String confirm =
                confirmInput.getText()
                        .toString();

        if (current.isEmpty()) {
            currentInput.setError(
                    "Current password ထည့်ပါ။"
            );
            currentInput.requestFocus();
            return;
        }

        if (next.length() < 8) {
            newInput.setError(
                    "Password အသစ် အနည်းဆုံး 8 လုံးလိုအပ်ပါသည်။"
            );
            newInput.requestFocus();
            return;
        }

        if (!next.equals(confirm)) {
            confirmInput.setError(
                    "Password အသစ်နှစ်ခု မတူပါ။"
            );
            confirmInput.requestFocus();
            return;
        }

        JSONObject body =
                new JSONObject();

        try {
            body.put(
                    "currentPassword",
                    current
            );

            body.put(
                    "newPassword",
                    next
            );
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    safeMessage(error),
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        passwordLoading = true;

        submitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        submitButton.setText("Please wait…");

        ApiClient.post(
                "account/password",
                body,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(
                            JSONObject json
                    ) {
                        runOnUiThread(() -> {
                            passwordLoading = false;
                            dialog.dismiss();

                            Toast.makeText(
                                    ProfileActivity.this,
                                    "Password ပြောင်းပြီးပါပြီ။",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        runOnUiThread(() -> {
                            passwordLoading = false;

                            submitButton.setEnabled(true);
                            cancelButton.setEnabled(true);

                            submitButton.setText(
                                    "Change Password"
                            );

                            Toast.makeText(
                                    ProfileActivity.this,
                                    safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void showLogoutDialog() {
        if (logoutLoading) {
            return;
        }

        Dialog dialog = createBaseDialog();

        LinearLayout container =
                createDialogContainer();

        TextView icon = createText(
                "⏻",
                38,
                Color.parseColor("#FF4A52"),
                true
        );

        icon.setGravity(Gravity.CENTER);

        TextView title = createText(
                "Logout Account",
                21,
                Color.WHITE,
                true
        );

        title.setGravity(Gravity.CENTER);

        TextView message = createText(
                "CMFLIX account မှ ထွက်မှာ သေချာပါသလား?",
                14,
                Color.parseColor("#A8ADB8"),
                false
        );

        message.setGravity(Gravity.CENTER);

        Button confirmButton = createDialogButton(
                "Logout",
                Color.parseColor("#E52D38")
        );

        Button cancelButton = createDialogButton(
                "Cancel",
                Color.parseColor("#2A2D35")
        );

        container.addView(icon);
        addTopMargin(container, title, 8);
        addTopMargin(container, message, 10);
        addTopMargin(container, confirmButton, 24);
        addTopMargin(container, cancelButton, 10);

        dialog.setContentView(container);

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        confirmButton.setOnClickListener(view -> {
            dialog.dismiss();
            logout();
        });

        showSizedDialog(dialog);
    }

    private void logout() {
        if (logoutLoading) {
            return;
        }

        logoutLoading = true;

        logoutButton.setEnabled(false);
        changePasswordButton.setEnabled(false);

        logoutButton.setText("Please wait…");
        progress.setVisibility(View.VISIBLE);

        ApiClient.post(
                "auth/logout",
                new JSONObject(),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(
                            JSONObject json
                    ) {
                        runOnUiThread(() ->
                                completeLocalLogout(
                                        "Logout ပြီးပါပြီ။"
                                )
                        );
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        /*
                         * Network error ဖြစ်သော်လည်း
                         * device ထဲက session ကိုရှင်းမယ်။
                         */
                        runOnUiThread(() ->
                                completeLocalLogout(
                                        "Local logout ပြီးပါပြီ။"
                                )
                        );
                    }
                }
        );
    }

    private void completeLocalLogout(
            String message
    ) {
        logoutLoading = false;

        SessionManager.clear();

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();

        setResult(RESULT_OK);
        finish();
    }

    private Dialog createBaseDialog() {
        Dialog dialog = new Dialog(this);
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

        return dialog;
    }

    private LinearLayout createDialogContainer() {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(22),
                dp(24),
                dp(22),
                dp(22)
        );

        container.setBackground(
                roundedBackground(
                        "#181A21",
                        24,
                        "#363944"
                )
        );

        return container;
    }

    private EditText createPasswordInput(
            String hint
    ) {
        EditText input = new EditText(this);

        input.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams
                                .MATCH_PARENT,
                        dp(54)
                )
        );

        input.setHint(hint);
        input.setSingleLine(true);

        input.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType
                                .TYPE_TEXT_VARIATION_PASSWORD
        );

        input.setTextColor(Color.WHITE);

        input.setHintTextColor(
                Color.parseColor("#818692")
        );

        input.setTextSize(14);

        input.setPadding(
                dp(16),
                0,
                dp(16),
                0
        );

        input.setBackground(
                roundedBackground(
                        "#22252E",
                        15,
                        "#3A3E49"
                )
        );

        return input;
    }

    private Button createDialogButton(
        String text,
        int color
) {
    Button button = new Button(this);

    button.setLayoutParams(
            new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(54)
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
    background.setCornerRadius(dp(16));

    button.setBackground(background);

    return button;
}


    private TextView createText(
            String value,
            int textSize,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(textSize);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(
                    view.getTypeface(),
                    android.graphics.Typeface.BOLD
            );
        }

        return view;
    }

    private void addTopMargin(
            LinearLayout parent,
            View child,
            int marginTop
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

        params.topMargin = dp(marginTop);
        child.setLayoutParams(params);

        parent.addView(child);
    }

    private GradientDrawable roundedBackground(
            String color,
            int radius,
            String strokeColor
    ) {
        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.parseColor(color)
        );

        background.setCornerRadius(
                dp(radius)
        );

        background.setStroke(
                dp(1),
                Color.parseColor(strokeColor)
        );

        return background;
    }

    private void showSizedDialog(
            Dialog dialog
    ) {
        dialog.show();

        Window window =
                dialog.getWindow();

        if (window == null) {
            return;
        }

        int screenWidth =
                getResources()
                        .getDisplayMetrics()
                        .widthPixels;

        int dialogWidth =
                Math.min(
                        (int) (screenWidth * 0.88f),
                        dp(400)
                );

        window.setLayout(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        android.view.WindowManager.LayoutParams attributes =
                window.getAttributes();

        attributes.dimAmount = 0.76f;
        window.setAttributes(attributes);

        window.addFlags(
                android.view.WindowManager.LayoutParams
                        .FLAG_DIM_BEHIND
        );
    }

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    private String safeMessage(
            Exception error
    ) {
        if (
                error == null ||
                error.getMessage() == null ||
                error.getMessage()
                        .trim()
                        .isEmpty()
        ) {
            return "Request မအောင်မြင်ပါ။";
        }

        return error.getMessage();
    }
}
