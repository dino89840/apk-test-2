package com.cmflix.nativeapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class AuthActivity extends AppCompatActivity {

    private View authCard;
    private ImageView authLogo;

    private TextView heading;
    private TextView subtitle;
    private TextView errorText;

    private EditText usernameInput;
    private EditText emailInput;
    private EditText identityInput;
    private EditText passwordInput;

    private CheckBox rememberCheckBox;

    private Button submitButton;
    private Button switchButton;
    private ProgressBar progress;

    private boolean registerMode = false;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApiClient.initialize(this);
        setContentView(R.layout.activity_auth);

        authCard = findViewById(R.id.authCard);
        authLogo = findViewById(R.id.authLogo);

        heading = findViewById(R.id.authHeading);
        subtitle = findViewById(R.id.authSubtitle);
        errorText = findViewById(R.id.authError);

        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        identityInput = findViewById(R.id.identityInput);
        passwordInput = findViewById(R.id.passwordInput);

        rememberCheckBox =
                findViewById(R.id.rememberCheckBox);

        submitButton = findViewById(R.id.authSubmit);
        switchButton = findViewById(R.id.authSwitch);
        progress = findViewById(R.id.authProgress);

        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        loadRememberedLogin();

        submitButton.setOnClickListener(
                view -> submit()
        );

        switchButton.setOnClickListener(view -> {
            if (loading) {
                return;
            }

            registerMode = !registerMode;
            updateMode();
            playModeAnimation();
        });

        updateMode();
        playEntranceAnimation();
    }

    private void loadRememberedLogin() {
        boolean remember =
                SessionManager.isRememberLoginEnabled();

        rememberCheckBox.setChecked(remember);

        if (remember) {
            identityInput.setText(
                    SessionManager.getRememberedIdentity()
            );

            identityInput.setSelection(
                    identityInput.getText().length()
            );
        }
    }

    private void updateMode() {
        errorText.setVisibility(View.GONE);

        usernameInput.setVisibility(
                registerMode ? View.VISIBLE : View.GONE
        );

        emailInput.setVisibility(
                registerMode ? View.VISIBLE : View.GONE
        );

        identityInput.setVisibility(
                registerMode ? View.GONE : View.VISIBLE
        );

        rememberCheckBox.setVisibility(
                registerMode ? View.GONE : View.VISIBLE
        );

        heading.setText(
                registerMode
                        ? "Create Account"
                        : "Welcome Back"
        );

        subtitle.setText(
                registerMode
                        ? "CMFLIX မှာ account အသစ်ဖွင့်ပြီး စတင်ကြည့်ရှုလိုက်ပါ"
                        : "လူကြီးမင်း၏ account ဖြင့် ပြန်လည်ဝင်ရောက်ပါ"
        );

        submitButton.setText(
                registerMode
                        ? "ACCOUNT ဖွင့်မည်"
                        : "LOGIN ဝင်မည်"
        );

        switchButton.setText(
                registerMode
                        ? "Account ရှိပြီးသားလား?  Login ဝင်မည်"
                        : "Account မရှိသေးဘူးလား?  Register လုပ်မည်"
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (registerMode) {
                usernameInput.setAutofillHints(
                        "newUsername"
                );

                passwordInput.setAutofillHints(
                        "newPassword"
                );
            } else {
                identityInput.setAutofillHints(
                        View.AUTOFILL_HINT_USERNAME,
                        View.AUTOFILL_HINT_EMAIL_ADDRESS
                );

                passwordInput.setAutofillHints(
                        View.AUTOFILL_HINT_PASSWORD
                );
            }
        }
    }

    private void submit() {
        if (loading) {
            return;
        }

        errorText.setVisibility(View.GONE);

        final boolean requestWasRegister =
                registerMode;

        final String password =
                passwordInput.getText()
                        .toString();

        if (password.length() < 8) {
            showError(
                    "Password အနည်းဆုံး 8 လုံးလိုအပ်ပါသည်။"
            );
            return;
        }

        final String submittedUsername;
        final String submittedIdentity;

        JSONObject body = new JSONObject();

        try {
            if (requestWasRegister) {
                String username =
                        usernameInput.getText()
                                .toString()
                                .trim();

                String email =
                        emailInput.getText()
                                .toString()
                                .trim();

                if (username.length() < 3) {
                    showError(
                            "Username အနည်းဆုံး 3 လုံးလိုအပ်ပါသည်။"
                    );
                    return;
                }

                if (email.isEmpty()) {
                    showError("Email ထည့်ပါ။");
                    return;
                }

                submittedUsername = username;
                submittedIdentity = "";

                body.put("username", username);
                body.put("email", email);
                body.put("password", password);
                body.put("turnstileToken", "");
            } else {
                String identity =
                        identityInput.getText()
                                .toString()
                                .trim();

                if (identity.isEmpty()) {
                    showError(
                            "Username သို့မဟုတ် email ထည့်ပါ။"
                    );
                    return;
                }

                submittedUsername = "";
                submittedIdentity = identity;

                body.put("identity", identity);
                body.put("password", password);
                body.put("turnstileToken", "");
            }

            body.put(
                    "deviceId",
                    SessionManager.getDeviceId()
            );
        } catch (Exception error) {
            showError(safeMessage(error));
            return;
        }

        setLoading(true);

        ApiClient.post(
                requestWasRegister
                        ? "auth/register"
                        : "auth/login",
                body,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() ->
                                handleAuthSuccess(
                                        json,
                                        requestWasRegister,
                                        submittedUsername,
                                        submittedIdentity,
                                        password
                                )
                        );
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showError(safeMessage(error));
                        });
                    }
                }
        );
    }

    private void handleAuthSuccess(
            JSONObject json,
            boolean requestWasRegister,
            String submittedUsername,
            String submittedIdentity,
            String submittedPassword
    ) {
        setLoading(false);

        JSONObject user =
                json.optJSONObject("user");

        if (user == null) {
            showError(
                    "User information မရပါ။"
            );
            return;
        }

        long vipUntil =
                user.optLong(
                        "vipUntil",
                        0L
                );

        String planType =
                user.optString(
                        "planType",
                        vipUntil >
                                System.currentTimeMillis()
                                ? "premium"
                                : "free"
                );

        String responseUsername =
                user.optString(
                        "username",
                        submittedUsername
                ).trim();

        SessionManager.saveAuth(
                json.optString("csrf", ""),
                responseUsername,
                user.optString("email", ""),
                vipUntil,
                user.optInt("planMonths", 0),
                planType
        );

        if (!requestWasRegister) {
            SessionManager.saveRememberedLogin(
                    rememberCheckBox.isChecked(),
                    submittedIdentity
            );

            setResult(RESULT_OK);
            finish();
            return;
        }

        /*
         * Password ကို SharedPreferences ထဲမသိမ်းပါ။
         * Register response အောင်မြင်သည့်အချိန်မှာ
         * dialog ကို တစ်ကြိမ်သာပြပါမယ်။
         */
        NewAccountDialog.show(
                this,
                responseUsername.isEmpty()
                        ? submittedUsername
                        : responseUsername,
                submittedPassword,
                () -> {
                    setResult(RESULT_OK);
                    finish();
                }
        );
    }

    private void setLoading(boolean value) {
        loading = value;

        progress.setVisibility(
                value ? View.VISIBLE : View.GONE
        );

        submitButton.setEnabled(!value);
        switchButton.setEnabled(!value);

        usernameInput.setEnabled(!value);
        emailInput.setEnabled(!value);
        identityInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        rememberCheckBox.setEnabled(!value);

        authCard.setAlpha(
                value ? 0.78f : 1f
        );
    }

    private void showError(String message) {
        errorText.setText(
                message == null ||
                        message.trim().isEmpty()
                        ? "Request မအောင်မြင်ပါ။"
                        : message
        );

        errorText.setVisibility(View.VISIBLE);

        errorText.setAlpha(0f);
        errorText.setTranslationY(-12f);

        errorText.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start();
    }

    private void playEntranceAnimation() {
        authCard.setAlpha(0f);
        authCard.setScaleX(0.94f);
        authCard.setScaleY(0.94f);
        authCard.setTranslationY(48f);

        authCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(480L)
                .setInterpolator(
                        new DecelerateInterpolator(1.7f)
                )
                .start();

        ObjectAnimator logoScaleX =
                ObjectAnimator.ofFloat(
                        authLogo,
                        View.SCALE_X,
                        0.82f,
                        1.08f,
                        1f
                );

        ObjectAnimator logoScaleY =
                ObjectAnimator.ofFloat(
                        authLogo,
                        View.SCALE_Y,
                        0.82f,
                        1.08f,
                        1f
                );

        ObjectAnimator logoRotation =
                ObjectAnimator.ofFloat(
                        authLogo,
                        View.ROTATION,
                        -7f,
                        4f,
                        0f
                );

        AnimatorSet set = new AnimatorSet();

        set.playTogether(
                logoScaleX,
                logoScaleY,
                logoRotation
        );

        set.setDuration(600L);
        set.setInterpolator(
                new DecelerateInterpolator()
        );
        set.start();
    }

    private void playModeAnimation() {
        heading.setAlpha(0f);
        subtitle.setAlpha(0f);

        heading.setTranslationY(12f);
        subtitle.setTranslationY(12f);

        heading.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start();

        subtitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280L)
                .start();
    }

    private String safeMessage(Exception error) {
        if (
                error == null ||
                error.getMessage() == null ||
                error.getMessage().trim().isEmpty()
        ) {
            return "Request မအောင်မြင်ပါ။";
        }

        return error.getMessage();
    }
}
