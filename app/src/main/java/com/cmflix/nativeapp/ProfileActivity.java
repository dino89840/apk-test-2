package com.cmflix.nativeapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

public class ProfileActivity
        extends AppCompatActivity {

    private TextView usernameText;
    private TextView emailText;
    private TextView vipText;
    private TextView deviceText;
    private Button logoutButton;
private boolean logoutLoading = false;

    private EditText currentPassword;
    private EditText newPassword;
    private EditText confirmPassword;

    private Button changeButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
logoutButton =
        findViewById(
                R.id.profileLogoutButton
        );

logoutButton.setOnClickListener(
        view -> showLogoutDialog()
);

        ApiClient.initialize(this);
        setContentView(
                R.layout.activity_profile
        );

        usernameText =
                findViewById(
                        R.id.profileUsername
                );

        emailText =
                findViewById(
                        R.id.profileEmail
                );

        vipText =
                findViewById(
                        R.id.profileVip
                );

        deviceText =
                findViewById(
                        R.id.profileDevice
                );

        currentPassword =
                findViewById(
                        R.id.currentPassword
                );

        newPassword =
                findViewById(
                        R.id.newPassword
                );

        confirmPassword =
                findViewById(
                        R.id.confirmPassword
                );

        changeButton =
                findViewById(
                        R.id.changePasswordButton
                );

        progress =
                findViewById(
                        R.id.profileProgress
                );

        changeButton.setOnClickListener(
                view -> changePassword()
        );

        loadProfile();
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
                            progress.setVisibility(
                                    View.GONE
                            );

                            JSONObject user =
                                    json.optJSONObject(
                                            "user"
                                    );

                            if (user == null) {
                                SessionManager.clear();
                                finish();
                                return;
                            }
SessionManager.saveAuth(
        json.optString(
                "csrf",
                SessionManager.getCsrf()
        ),
        user.optString(
                "username",
                SessionManager.getUsername()
        ),
        user.optString(
                "email",
                SessionManager.getEmail()
        ),
        user.optLong(
                "vipUntil",
                0L
        )
);

                            usernameText.setText(
                                    "Username: " +
                                            user.optString(
                                                    "username",
                                                    ""
                                            )
                            );

                            emailText.setText(
                                    "Email: " +
                                            user.optString(
                                                    "email",
                                                    ""
                                            )
                            );

                            boolean isVip =
        user.optBoolean(
                "isVip",
                false
        );

long vipUntil =
        user.optLong(
                "vipUntil",
                0L
        );

if (isVip && vipUntil > System.currentTimeMillis()) {
    String expiry =
            DateFormat
                    .getDateTimeInstance()
                    .format(
                            new Date(vipUntil)
                    );

    vipText.setText(
            SessionManager.getPremiumLabel() +
                    "\nVIP သက်တမ်းကုန်မည့်အချိန်: " +
                    expiry
    );
} else {
    vipText.setText("P-0Day\nPremium မရှိသေးပါ");
}


                            deviceText.setText(
                                    user.optBoolean(
                                            "vipDeviceBound",
                                            false
                                    )
                                            ? "VIP Device: ချိတ်ထားသည်"
                                            : "VIP Device: မချိတ်ရသေး"
                            );
                        });
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        runOnUiThread(() -> {
                            progress.setVisibility(
                                    View.GONE
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

    private void changePassword() {
        String current =
                currentPassword
                        .getText()
                        .toString();

        String next =
                newPassword
                        .getText()
                        .toString();

        String confirm =
                confirmPassword
                        .getText()
                        .toString();

        if (next.length() < 8) {
            Toast.makeText(
                    this,
                    "Password အသစ် အနည်းဆုံး 8 လုံးလိုအပ်ပါသည်။",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (!next.equals(confirm)) {
            Toast.makeText(
                    this,
                    "Password အသစ်နှစ်ခု မတူပါ။",
                    Toast.LENGTH_LONG
            ).show();

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
            return;
        }

        changeButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);

        ApiClient.post(
                "account/password",
                body,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(
                            JSONObject json
                    ) {
                        runOnUiThread(() -> {
                            changeButton.setEnabled(
                                    true
                            );

                            progress.setVisibility(
                                    View.GONE
                            );

                            currentPassword.setText("");
                            newPassword.setText("");
                            confirmPassword.setText("");

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
                            changeButton.setEnabled(
                                    true
                            );

                            progress.setVisibility(
                                    View.GONE
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
private void showLogoutDialog() {
    if (logoutLoading) {
        return;
    }

    new AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage(
                    "CMFLIX account မှ ထွက်ရန် " +
                            "သေချာပါသလား?"
            )
            .setNegativeButton(
                    "Cancel",
                    null
            )
            .setPositiveButton(
                    "Logout",
                    (dialog, which) -> logout()
            )
            .show();
}

private void logout() {
    if (logoutLoading) {
        return;
    }

    logoutLoading = true;
    logoutButton.setEnabled(false);
    logoutButton.setText("Please wait…");
    progress.setVisibility(View.VISIBLE);

    ApiClient.post(
            "auth/logout",
            new JSONObject(),
            new ApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject json) {
                    runOnUiThread(() ->
                            completeLocalLogout(
                                    "Logout ပြီးပါပြီ။"
                            )
                    );
                }

                @Override
                public void onError(Exception error) {
                    /*
                     * Server session သက်တမ်းကုန်နေခြင်း သို့မဟုတ်
                     * network error ဖြစ်နေရင်လည်း ဖုန်းထဲက
                     * local session ကိုရှင်းနိုင်ရမည်။
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
