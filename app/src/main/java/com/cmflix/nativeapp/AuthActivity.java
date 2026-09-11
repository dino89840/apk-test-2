package com.cmflix.nativeapp;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class AuthActivity extends AppCompatActivity {

    private TextView heading;
    private TextView errorText;
    private EditText usernameInput;
    private EditText emailInput;
    private EditText identityInput;
    private EditText passwordInput;
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

        heading = findViewById(R.id.authHeading);
        errorText = findViewById(R.id.authError);
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        identityInput = findViewById(R.id.identityInput);
        passwordInput = findViewById(R.id.passwordInput);
        submitButton = findViewById(R.id.authSubmit);
        switchButton = findViewById(R.id.authSwitch);
        progress = findViewById(R.id.authProgress);

        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        submitButton.setOnClickListener(
                view -> submit()
        );

        switchButton.setOnClickListener(view -> {
            if (!loading) {
                registerMode = !registerMode;
                updateMode();
            }
        });

        updateMode();
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

        heading.setText(
                registerMode
                        ? "Account အသစ်ဖွင့်မည်"
                        : "Login"
        );

        submitButton.setText(
                registerMode
                        ? "REGISTER"
                        : "LOGIN"
        );

        switchButton.setText(
                registerMode
                        ? "Account ရှိပြီးသားလား? Login ဝင်မည်"
                        : "Account မရှိသေးဘူးလား? Register လုပ်မည်"
        );
    }

    private void submit() {
        if (loading) {
            return;
        }

        String password =
                passwordInput.getText()
                        .toString();

        if (password.length() < 8) {
            showError(
                    "Password အနည်းဆုံး 8 လုံးလိုအပ်ပါသည်။"
            );
            return;
        }

        JSONObject body = new JSONObject();

        try {
            if (registerMode) {
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

                body.put("identity", identity);
                body.put("password", password);
                body.put("turnstileToken", "");
            }
        } catch (Exception error) {
            showError(error.getMessage());
            return;
        }
        try {
            body.put(
                    "deviceId",
                    SessionManager.getDeviceId()
            );
        } catch (Exception error) {
            showError(error.getMessage());
            return;
        }

        setLoading(true);

        ApiClient.post(
                registerMode
                        ? "auth/register"
                        : "auth/login",
                body,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        runOnUiThread(() -> {
                            setLoading(false);

                            JSONObject user =
                                    json.optJSONObject("user");

                            if (user == null) {
                                showError(
                                        "User information မရပါ။"
                                );
                                return;
                            }

                            SessionManager.saveAuth(
        json.optString("csrf", ""),
        user.optString("username", ""),
        user.optString("email", ""),
        user.optLong("vipUntil", 0L)
);


                            Toast.makeText(
                                    AuthActivity.this,
                                    "Login အောင်မြင်ပါသည်။",
                                    Toast.LENGTH_SHORT
                            ).show();

                            setResult(RESULT_OK);
                            finish();
                        });
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

    private void setLoading(boolean value) {
        loading = value;

        progress.setVisibility(
                value ? View.VISIBLE : View.GONE
        );

        submitButton.setEnabled(!value);
        switchButton.setEnabled(!value);
    }

    private void showError(String message) {
        errorText.setText(
                message == null || message.isEmpty()
                        ? "Unknown error"
                        : message
        );

        errorText.setVisibility(View.VISIBLE);
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
