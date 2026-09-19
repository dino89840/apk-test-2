package com.cmflix.nativeapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class AuthActivity extends AppCompatActivity {

    private static final String TELEGRAM_URL =
            "https://t.me/iqowoq";

    private View authCard;
    private View loginOptionsRow;

    private ImageView authLogo;
    private ImageButton backButton;

    private TextView heading;
    private TextView subtitle;
        private TextView forgotPasswordButton;
    private TextView contactButton;

    private EditText usernameInput;
    private EditText emailInput;
    private EditText identityInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    private CheckBox rememberCheckBox;

    private Button submitButton;
    private Button switchButton;
    private ProgressBar progress;

    private boolean registerMode = false;
    private boolean loading = false;

    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApiClient.initialize(this);
        setContentView(R.layout.activity_auth);

        bindViews();
        setupPasswordToggles();
        setupClickListeners();

        loadRememberedLogin();
        updateMode();
        playEntranceAnimation();
    }

    private void bindViews() {
        authCard = findViewById(R.id.authCard);
        loginOptionsRow =
                findViewById(R.id.loginOptionsRow);

        authLogo = findViewById(R.id.authLogo);
        backButton = findViewById(R.id.authBackButton);

        heading = findViewById(R.id.authHeading);
        subtitle = findViewById(R.id.authSubtitle);
        
        forgotPasswordButton =
                findViewById(R.id.forgotPasswordButton);

        contactButton =
                findViewById(R.id.authContactButton);

        usernameInput =
                findViewById(R.id.usernameInput);

        emailInput =
                findViewById(R.id.emailInput);

        identityInput =
                findViewById(R.id.identityInput);

        passwordInput =
                findViewById(R.id.passwordInput);

        confirmPasswordInput =
                findViewById(R.id.confirmPasswordInput);

        rememberCheckBox =
                findViewById(R.id.rememberCheckBox);

        submitButton =
                findViewById(R.id.authSubmit);

        switchButton =
                findViewById(R.id.authSwitch);

        progress =
                findViewById(R.id.authProgress);
    }

    private void setupClickListeners() {
        submitButton.setOnClickListener(
                view -> submit()
        );

        switchButton.setOnClickListener(view -> {
            if (loading) {
                return;
            }

            registerMode = !registerMode;

            /*
             * Remembered password ကို Register form မှာ
             * မပြမိအောင် register ပြောင်းသောအခါရှင်းမည်။
             */
            if (registerMode) {
                passwordInput.setText("");
                confirmPasswordInput.setText("");
            } else {
                loadRememberedLogin();
            }

            updateMode();
            playModeAnimation();
        });

        backButton.setOnClickListener(view -> {
            if (loading) {
                return;
            }

            if (registerMode) {
                registerMode = false;
                loadRememberedLogin();
                updateMode();
                playModeAnimation();
            } else {
                finish();
            }
        });

        forgotPasswordButton.setOnClickListener(
                view -> openTelegram()
        );

        contactButton.setOnClickListener(
                view -> openTelegram()
        );
    }

    private void setupPasswordToggles() {
        setPasswordDrawable(
                passwordInput,
                false
        );

        setPasswordDrawable(
                confirmPasswordInput,
                false
        );

        passwordInput.setOnTouchListener(
                (view, event) -> {
                    if (
                            event.getAction() ==
                                    MotionEvent.ACTION_UP &&
                            touchedEndDrawable(
                                    passwordInput,
                                    event
                            )
                    ) {
                        passwordVisible =
                                !passwordVisible;

                        updatePasswordVisibility(
                                passwordInput,
                                passwordVisible
                        );

                        return true;
                    }

                    return false;
                }
        );

        confirmPasswordInput.setOnTouchListener(
                (view, event) -> {
                    if (
                            event.getAction() ==
                                    MotionEvent.ACTION_UP &&
                            touchedEndDrawable(
                                    confirmPasswordInput,
                                    event
                            )
                    ) {
                        confirmPasswordVisible =
                                !confirmPasswordVisible;

                        updatePasswordVisibility(
                                confirmPasswordInput,
                                confirmPasswordVisible
                        );

                        return true;
                    }

                    return false;
                }
        );
    }

    private boolean touchedEndDrawable(
            EditText editText,
            MotionEvent event
    ) {
        if (
                editText.getCompoundDrawables()[2] ==
                        null
        ) {
            return false;
        }

        return event.getX() >=
                editText.getWidth() -
                        editText.getTotalPaddingEnd();
    }

    private void updatePasswordVisibility(
            EditText input,
            boolean visible
    ) {
        int selection =
                input.getSelectionStart();

        input.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        (
                                visible
                                        ? InputType
                                        .TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                                        : InputType
                                        .TYPE_TEXT_VARIATION_PASSWORD
                        )
        );

        input.setSelection(
                Math.max(
                        0,
                        Math.min(
                                selection,
                                input.length()
                        )
                )
        );

        setPasswordDrawable(
                input,
                visible
        );
    }

    private void setPasswordDrawable(
            EditText input,
            boolean visible
    ) {
        input.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(
                        this,
                        R.drawable.ic_auth_lock
                ),
                null,
                ContextCompat.getDrawable(
                        this,
                        visible
                                ? R.drawable.ic_auth_visibility
                                : R.drawable.ic_auth_visibility_off
                ),
                null
        );

        input.setCompoundDrawablePadding(dp(12));
    }

    private void loadRememberedLogin() {
        boolean remember =
                SessionManager
                        .isRememberLoginEnabled();

        rememberCheckBox.setChecked(remember);

        if (!remember) {
            return;
        }

        String identity =
                SessionManager
                        .getRememberedIdentity();

        String password =
                SessionManager
                        .getRememberedPassword();

        identityInput.setText(identity);
        passwordInput.setText(password);

        identityInput.setSelection(
                identityInput.length()
        );

        passwordInput.setSelection(
                passwordInput.length()
        );
    }

    private void updateMode() {
        
        usernameInput.setVisibility(
                registerMode
                        ? View.VISIBLE
                        : View.GONE
        );

        emailInput.setVisibility(
                registerMode
                        ? View.VISIBLE
                        : View.GONE
        );

        identityInput.setVisibility(
                registerMode
                        ? View.GONE
                        : View.VISIBLE
        );

        confirmPasswordInput.setVisibility(
                registerMode
                        ? View.VISIBLE
                        : View.GONE
        );

        loginOptionsRow.setVisibility(
                registerMode
                        ? View.GONE
                        : View.VISIBLE
        );

        backButton.setVisibility(
                registerMode
                        ? View.VISIBLE
                        : View.INVISIBLE
        );

        heading.setText(
                registerMode
                        ? "Register"
                        : "Login"
        );

        subtitle.setText("CMFLIX for Mobile");

        submitButton.setText(
                registerMode
                        ? "Register"
                        : "Login"
        );

        switchButton.setText(
                registerMode
                        ? "Login"
                        : "New User? Register"
        );

        if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.O) {

    if (registerMode) {
        usernameInput.setAutofillHints(
                "newUsername"
        );

        emailInput.setAutofillHints(
                View.AUTOFILL_HINT_EMAIL_ADDRESS
        );

        passwordInput.setAutofillHints(
                "newPassword"
        );

        confirmPasswordInput.setAutofillHints(
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

        
        final boolean requestWasRegister =
                registerMode;

        final String password =
                passwordInput
                        .getText()
                        .toString();

        if (password.length() < 8) {
            showError(
                    "Password အနည်းဆုံး 8 လုံးလိုအပ်ပါသည်။"
            );

            passwordInput.requestFocus();
            return;
        }

        final String submittedUsername;
        final String submittedIdentity;

        JSONObject body = new JSONObject();

        try {
            if (requestWasRegister) {
                String username =
                        usernameInput
                                .getText()
                                .toString()
                                .trim();

                String email =
                        emailInput
                                .getText()
                                .toString()
                                .trim();

                String confirmPassword =
                        confirmPasswordInput
                                .getText()
                                .toString();

                if (username.length() < 3) {
                    showError(
                            "Username အနည်းဆုံး 3 လုံးလိုအပ်ပါသည်။"
                    );

                    usernameInput.requestFocus();
                    return;
                }

                if (email.isEmpty()) {
                    showError("Email ထည့်ပါ။");
                    emailInput.requestFocus();
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    showError(
                            "Password နှစ်ခု မတူပါ။"
                    );

                    confirmPasswordInput.requestFocus();
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
                        identityInput
                                .getText()
                                .toString()
                                .trim();

                if (identity.isEmpty()) {
                    showError(
                            "Username သို့မဟုတ် email ထည့်ပါ။"
                    );

                    identityInput.requestFocus();
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

        /*
 * Internet မရှိတာသေချာလျှင် HttpURLConnection ကို
 * request မလုပ်ခင် user-friendly popup ပြမည်။
 */
if (!NetworkUtils.isOnline(this)) {
    showError(
            "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။\n" +
                    "Wi-Fi သို့မဟုတ် Mobile Data ကိုဖွင့်ပြီး " +
                    "ပြန်လည်ကြိုးစားပါ။"
    );

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
                    public void onSuccess(
                            JSONObject json
                    ) {
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
                    public void onError(
                            Exception error
                    ) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showError(
                                    safeMessage(error)
                            );
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
                    submittedIdentity,
                    submittedPassword
            );

            setResult(RESULT_OK);
            finish();
            return;
        }

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

    private void openTelegram() {
        Intent intent =
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(TELEGRAM_URL)
                );

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(
                    this,
                    "Telegram link ကိုဖွင့်နိုင်သော app မရှိပါ။",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void setLoading(boolean value) {
        loading = value;

        progress.setVisibility(
                value
                        ? View.VISIBLE
                        : View.GONE
        );

        submitButton.setEnabled(!value);
        switchButton.setEnabled(!value);
        backButton.setEnabled(!value);

        usernameInput.setEnabled(!value);
        emailInput.setEnabled(!value);
        identityInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        confirmPasswordInput.setEnabled(!value);

        rememberCheckBox.setEnabled(!value);
        forgotPasswordButton.setEnabled(!value);
        contactButton.setEnabled(!value);

        authCard.setAlpha(
                value
                        ? 0.78f
                        : 1f
        );
    }

    private void showError(String message) {
    String safeText =
            message == null ||
                    message.trim().isEmpty()
                    ? "Request မအောင်မြင်ပါ။"
                    : message.trim();

    if (
            isFinishing() ||
            isDestroyed()
    ) {
        return;
    }

    /*
     * AnnouncementDialog က custom modal dialog ဖြစ်တဲ့အတွက်
     * login form UI အတွင်း error box မပြတော့ပါ။
     *
     * Offline error၊ wrong password၊ VIP device reset
     * လိုအပ်သော message အားလုံး ဒီ popup ထဲဝင်မည်။
     */
    AnnouncementDialog.show(
            this,
            "အကြောင်းကြားချက်",
            safeText
    );
}


    private void playEntranceAnimation() {
        authCard.setAlpha(0f);
        authCard.setScaleX(0.96f);
        authCard.setScaleY(0.96f);
        authCard.setTranslationY(38f);

        authCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(450L)
                .setInterpolator(
                        new DecelerateInterpolator(1.7f)
                )
                .start();

        ObjectAnimator logoScaleX =
                ObjectAnimator.ofFloat(
                        authLogo,
                        View.SCALE_X,
                        0.84f,
                        1.05f,
                        1f
                );

        ObjectAnimator logoScaleY =
                ObjectAnimator.ofFloat(
                        authLogo,
                        View.SCALE_Y,
                        0.84f,
                        1.05f,
                        1f
                );

        AnimatorSet set = new AnimatorSet();

        set.playTogether(
                logoScaleX,
                logoScaleY
        );

        set.setDuration(520L);
        set.setInterpolator(
                new DecelerateInterpolator()
        );

        set.start();
    }

    private void playModeAnimation() {
        heading.setAlpha(0f);
        subtitle.setAlpha(0f);

        heading.setTranslationY(10f);
        subtitle.setTranslationY(10f);

        heading.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start();

        subtitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .start();
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
    if (error == null) {
        return "Request မအောင်မြင်ပါ။";
    }

    if (
            error instanceof java.net.UnknownHostException ||
            error instanceof java.net.ConnectException ||
            error instanceof java.net.NoRouteToHostException ||
            error instanceof java.net.SocketTimeoutException
    ) {
        return "အင်တာနက် သို့မဟုတ် server ချိတ်ဆက်မှု မရှိပါ။\n" +
                "Wi-Fi/Mobile Data ဖွင့်ထားခြင်းနှင့် " +
                "server အလုပ်လုပ်နေခြင်းကို စစ်ဆေးပါ။";
    }

    String message =
            error.getMessage();

    if (
            message == null ||
            message.trim().isEmpty()
    ) {
        return "Request မအောင်မြင်ပါ။";
    }

    String normalized =
            message.toLowerCase(
                    java.util.Locale.ROOT
            );

    if (
            normalized.contains(
                    "unable to resolve host"
            ) ||
            normalized.contains(
                    "no address associated with hostname"
            ) ||
            normalized.contains(
                    "failed to connect"
            ) ||
            normalized.contains(
                    "connection refused"
            ) ||
            normalized.contains(
                    "network is unreachable"
            ) ||
            normalized.contains(
                    "no internet connection"
            ) ||
            normalized.contains(
                    "timeout"
            ) ||
            normalized.contains(
                    "timed out"
            )
    ) {
        return "အင်တာနက် သို့မဟုတ် server ချိတ်ဆက်မှု မရှိပါ။\n" +
                "ခဏနောက် ပြန်လည်ကြိုးစားပါ။";
    }

    return message.trim();
}

}
