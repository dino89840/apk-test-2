package com.cmflix.nativeapp;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;


import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

public class ProfileActivity
        extends AppCompatActivity {

    private TextView usernameText;
private TextView accountStatusText;
private TextView emailText;
private TextView planText;
private TextView expiryText;
private EditText promoCodeInput;
private Button promoRedeemButton;
private boolean promoLoading = false;



    private View changePasswordButton;
private View adultPinButton;
private TextView adultPinStatus;
private View logoutButton;
private TextView logoutLabel;
private View contactButton;


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

/*
 * Profile screen က user-specific information ဖြစ်သောကြောင့်
 * ဖွင့်တိုင်း server ကနေ လက်ရှိ VIP/password-reset/
 * device-reset state ကိုပြန်စစ်မည်။
 *
 * Cached profile ကို bindCachedProfile() က ချက်ချင်းပြထားပြီး
 * server response ရောက်လျှင် update လုပ်မည်။
 */
loadProfile();

    }

    private void bindViews() {
        usernameText =
                findViewById(R.id.profileUsername);

        accountStatusText =
                findViewById(R.id.profileAccountStatus);

        emailText =
                findViewById(R.id.profileEmail);



        planText =
                findViewById(R.id.profilePlan);

        expiryText =
                findViewById(R.id.profileExpiry);
promoCodeInput =
        findViewById(R.id.promoCodeInput);

promoRedeemButton =
        findViewById(R.id.promoRedeemButton);

        changePasswordButton =
                findViewById(R.id.changePasswordButton);

        adultPinButton =
                findViewById(R.id.adultPinButton);

        adultPinStatus =
                findViewById(R.id.adultPinStatus);

        logoutButton =
        findViewById(R.id.profileLogoutButton);

        logoutLabel =
                findViewById(R.id.profileLogoutLabel);

contactButton =
        findViewById(R.id.profileContactButton);


        progress =
                findViewById(R.id.profileProgress);

        TextView backButton =
                findViewById(R.id.profileBackButton);

        backButton.setOnClickListener(
                view -> finish()
        );
    }

    private void setupClickListeners() {
    promoRedeemButton.setOnClickListener(
            view -> redeemPromoCode()
    );

    changePasswordButton.setOnClickListener(
            view -> showChangePasswordDialog()
    );

    adultPinButton.setOnClickListener(
            view -> showAdultPinDialog()
    );

    updateAdultPinButton();

    logoutButton.setOnClickListener(
            view -> showLogoutDialog()
    );

    contactButton.setOnClickListener(
            view -> openTelegram()
    );
}
private void openTelegram() {
    Intent intent =
            new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                            "https://t.me/iqowoq"
                    )
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

        bindVipState(
        vipUntil,
        SessionManager.getVipPlanType()
);
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

                            int planMonths =
        user.optInt(
                "planMonths",
                0
        );
String planType =
        user.optString(
                "planType",
                vipUntil >
                        System.currentTimeMillis()
                        ? "premium"
                        : "free"
        );

SessionManager.saveAuth(
        user.optString(
                "id",
                SessionManager.getUserId()
        ),
        json.optString(
                "csrf",
                SessionManager.getCsrf()
        ),
        username,
        email,
        vipUntil,
        planMonths,
        planType
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

                            

                            bindVipState(
        vipUntil,
        planType
);
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

    private void bindVipState(
        long vipUntil,
        String planType
) {
    boolean active =
            vipUntil >
                    System.currentTimeMillis();

    boolean isTrial =
            active &&
            "trial".equals(
                    planType
            );

    if (active) {
        if (isTrial) {
            accountStatusText.setText(
        "Trial member"
);


            planText.setText(
                    "Trial"
            );

            accountStatusText.setTextColor(
                    Color.parseColor(
                            "#8FE9FF"
                    )
            );
        } else {
            accountStatusText.setText(
        "Premium member"
);


            planText.setText(
                    SessionManager
                            .getPlanLabel()
            );

            accountStatusText.setTextColor(
                    Color.parseColor(
                            "#FFF2A8"
                    )
            );
        }

        expiryText.setText(
                DateFormat
                        .getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT
                        )
                        .format(
                                new Date(
                                        vipUntil
                                )
                        )
        );
    } else {
        accountStatusText.setText(
        "Premium မရှိသေးပါ"
);


        planText.setText(
                "Free Plan"
        );

        expiryText.setText(
                "Expired"
        );

        accountStatusText.setTextColor(
                Color.parseColor(
                        "#FFD2D2"
                )
        );
    }
}

private void redeemPromoCode() {
    if (promoLoading) {
        return;
    }

    String code =
            promoCodeInput
                    .getText()
                    .toString()
                    .trim()
                    .toUpperCase(
                            java.util.Locale.US
                    );

    if (code.length() < 6) {
        promoCodeInput.setError(
                "Promo Code မှန်မှန်ထည့်ပါ။"
        );

        promoCodeInput.requestFocus();
        return;
    }

    JSONObject body =
            new JSONObject();

    try {
        body.put("code", code);
    } catch (Exception error) {
        Toast.makeText(
                this,
                safeMessage(error),
                Toast.LENGTH_LONG
        ).show();

        return;
    }

    promoLoading = true;

    promoRedeemButton.setEnabled(false);
    promoCodeInput.setEnabled(false);
    promoRedeemButton.setText("WAIT…");
    progress.setVisibility(View.VISIBLE);

    ApiClient.post(
            "account/promo/redeem",
            body,
            new ApiClient.Callback() {
                @Override
                public void onSuccess(
                        JSONObject json
                ) {
                    runOnUiThread(() -> {
                        promoLoading = false;

                        promoRedeemButton.setEnabled(true);
                        promoCodeInput.setEnabled(true);
                        promoRedeemButton.setText("REDEEM");

                        progress.setVisibility(View.GONE);

                        JSONObject user =
                                json.optJSONObject("user");

                        if (user != null) {
                            long vipUntil =
                                    user.optLong(
                                            "vipUntil",
                                            0L
                                    );

                            int planMonths =
                                    user.optInt(
                                            "planMonths",
                                            0
                                    );
                                    String planType =
        user.optString(
                "planType",
                "premium"
        );


                            SessionManager.saveVipState(
        vipUntil,
        planMonths,
        planType
);

bindVipState(
        vipUntil,
        planType
);

                        }

                        promoCodeInput.setText("");

                        Toast.makeText(
                                ProfileActivity.this,
                                json.optString(
                                        "message",
                                        "Promo Code အသုံးပြုပြီးပါပြီ။"
                                ),
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

                @Override
                public void onError(
                        Exception error
                ) {
                    runOnUiThread(() -> {
                        promoLoading = false;

                        promoRedeemButton.setEnabled(true);
                        promoCodeInput.setEnabled(true);
                        promoRedeemButton.setText("REDEEM");

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

    /*
     * 18+ PIN Lock စီမံခန့်ခွဲမှု။
     * PIN မရှိသေးလျှင် အသစ်သတ်မှတ်ခိုင်းပြီး
     * ရှိပြီးလျှင် အရင် verify လုပ်ပြီးမှ
     * ပြောင်း/ဖျက် ခွင့်ပြုမည်။
     */
    private void updateAdultPinButton() {
        if (adultPinStatus == null) {
            return;
        }

        boolean hasPin = SecureCredentialStore.hasAdultPin();
        adultPinStatus.setText(hasPin ? "ON" : "OFF");
        adultPinStatus.setTextColor(
                hasPin ? 0xFFE8B84B : 0xFFA8ADB8
        );
    }

    private EditText createPinInput(String hint) {
        EditText input =
                createPasswordInput(hint);

        input.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType
                                .TYPE_NUMBER_VARIATION_PASSWORD
        );

        input.setFilters(
                new InputFilter[]{
                        new InputFilter.LengthFilter(4)
                }
        );

        input.setGravity(Gravity.CENTER);
        input.setTextSize(20);
        input.setLetterSpacing(0.3f);

        return input;
    }

    private void showAdultPinDialog() {
        if (SecureCredentialStore.hasAdultPin()) {
            showAdultPinVerifyDialog();
        } else {
            showAdultPinCreateDialog();
        }
    }

    private void showAdultPinCreateDialog() {
        boolean isChange =
                SecureCredentialStore.hasAdultPin();

        Dialog dialog = createBaseDialog();
        LinearLayout container =
                createDialogContainer();

        TextView title = createText(
                isChange
                        ? "Change 18+ PIN"
                        : "Set 18+ PIN Lock",
                19,
                Color.WHITE,
                true
        );

        TextView message = createText(
                "Nosub 18+ / Mmsub 18+ tab တွေဖွင့်တိုင်း " +
                        "ဒီ 4-digit PIN တောင်းမယ်။",
                12,
                Color.parseColor("#A8ADB8"),
                false
        );

        /*
         * PIN က device ထဲမှာပဲ သိမ်းထားကြောင်း
         * premium ဆန်ဆန် info note ပြမည်။
         */
        LinearLayout infoRow =
                new LinearLayout(this);

        infoRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView infoIcon = new ImageView(this);

        infoIcon.setImageResource(
                R.drawable.ic_info_pin
        );

        LinearLayout.LayoutParams infoIconParams =
                new LinearLayout.LayoutParams(
                        dp(15),
                        dp(15)
                );

        infoIconParams.setMarginEnd(dp(7));
        infoIcon.setLayoutParams(infoIconParams);

        TextView infoText = createText(
                "PIN ကို ဒီဖုန်းထဲမှာပဲ သိမ်းပါတယ်။ " +
                        "Clear data / reinstall လုပ်ရင် " +
                        "ပျက်သွားပါမယ်။",
                11,
                Color.parseColor("#A8ADB8"),
                false
        );

        infoText.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams
                                .WRAP_CONTENT,
                        1f
                )
        );

        infoRow.addView(infoIcon);
        infoRow.addView(infoText);

        EditText newPin =
                createPinInput("New 4-digit PIN");

        EditText confirmPin =
                createPinInput("Confirm PIN");

        TextView errorText = createText(
                "",
                12,
                Color.parseColor("#FF7A7A"),
                false
        );

        Button saveButton = createDialogButton(
                isChange ? "Change PIN" : "Set PIN",
                Color.parseColor("#079E86")
        );

        Button cancelButton = createDialogButton(
                "Cancel",
                Color.parseColor("#2A2D35")
        );

        container.addView(title);
        addTopMargin(container, message, 6);
        addTopMargin(container, infoRow, 10);
        addTopMargin(container, newPin, 12);
        addTopMargin(container, confirmPin, 9);
        addTopMargin(container, errorText, 8);
        addTopMargin(container, saveButton, 12);
        addTopMargin(container, cancelButton, 8);

        dialog.setContentView(container);

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        saveButton.setOnClickListener(view -> {
            String first =
                    newPin.getText()
                            .toString()
                            .trim();

            String second =
                    confirmPin.getText()
                            .toString()
                            .trim();

            if (!first.matches("\\d{4}")) {
                errorText.setText(
                        "PIN က ဂဏန်း ၄ လုံး ဖြစ်ရမယ်။"
                );
                return;
            }

            if (!first.equals(second)) {
                errorText.setText(
                        "PIN နှစ်ခု တူမနေဘူး။"
                );
                return;
            }

            SecureCredentialStore.setAdultPin(first);
            updateAdultPinButton();
            dialog.dismiss();

            Toast.makeText(
                    this,
                    "18+ PIN သတ်မှတ်ပြီးပါပြီ။",
                    Toast.LENGTH_SHORT
            ).show();
        });

        showSizedDialog(
                dialog,
                0.84f,
                370
        );
    }

    private void showAdultPinVerifyDialog() {
        Dialog dialog = createBaseDialog();
        LinearLayout container =
                createDialogContainer();

        TextView title = createText(
                "18+ PIN Lock",
                19,
                Color.WHITE,
                true
        );

        TextView message = createText(
                "ဆက်လုပ်ဖို့ လက်ရှိ PIN ထည့်ပါ။",
                12,
                Color.parseColor("#A8ADB8"),
                false
        );

        EditText currentPin =
                createPinInput("Current PIN");

        TextView errorText = createText(
                "",
                12,
                Color.parseColor("#FF7A7A"),
                false
        );

        Button continueButton = createDialogButton(
                "Continue",
                Color.parseColor("#079E86")
        );

        Button cancelButton = createDialogButton(
                "Cancel",
                Color.parseColor("#2A2D35")
        );

        container.addView(title);
        addTopMargin(container, message, 6);
        addTopMargin(container, currentPin, 15);
        addTopMargin(container, errorText, 8);
        addTopMargin(container, continueButton, 12);
        addTopMargin(container, cancelButton, 8);

        dialog.setContentView(container);

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        continueButton.setOnClickListener(view -> {
            String pin =
                    currentPin.getText()
                            .toString()
                            .trim();

            if (
                    SecureCredentialStore
                            .verifyAdultPin(pin)
            ) {
                dialog.dismiss();
                showAdultPinManageDialog();
            } else {
                errorText.setText(
                        "PIN မှားနေပါတယ်။"
                );
                currentPin.setText("");
            }
        });

        showSizedDialog(
                dialog,
                0.84f,
                370
        );
    }

    private void showAdultPinManageDialog() {
        Dialog dialog = createBaseDialog();
        LinearLayout container =
                createDialogContainer();

        TextView title = createText(
                "18+ PIN Lock",
                19,
                Color.WHITE,
                true
        );

        TextView message = createText(
                "PIN ကို ပြောင်းမလား၊ ဖျက်မလား ရွေးပါ။",
                12,
                Color.parseColor("#A8ADB8"),
                false
        );

        Button changeButton = createDialogButton(
                "Change PIN",
                Color.parseColor("#079E86")
        );

        Button removeButton = createDialogButton(
                "Remove PIN",
                Color.parseColor("#A63A3A")
        );

        Button cancelButton = createDialogButton(
                "Cancel",
                Color.parseColor("#2A2D35")
        );

        container.addView(title);
        addTopMargin(container, message, 6);
        addTopMargin(container, changeButton, 15);
        addTopMargin(container, removeButton, 8);
        addTopMargin(container, cancelButton, 8);

        dialog.setContentView(container);

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        changeButton.setOnClickListener(view -> {
            dialog.dismiss();
            showAdultPinCreateDialog();
        });

        removeButton.setOnClickListener(view -> {
            SecureCredentialStore.clearAdultPin();
            updateAdultPinButton();
            dialog.dismiss();

            Toast.makeText(
                    this,
                    "18+ PIN ဖျက်ပြီးပါပြီ။",
                    Toast.LENGTH_SHORT
            ).show();
        });

        showSizedDialog(
                dialog,
                0.84f,
                370
        );
    }


    private void showChangePasswordDialog() {
    if (passwordLoading) {
        return;
    }

    Dialog dialog = createBaseDialog();

    LinearLayout container =
            createDialogContainer();

    /*
     * Password dialog ကိုသာ compact ဖြစ်အောင်လုပ်ထားသည်။
     * Logout dialog နှင့် အခြား dialog များကို မထိခိုက်ပါ။
     */
    container.setPadding(
            dp(19),
            dp(19),
            dp(19),
            dp(17)
    );

    TextView title = createText(
            "Change Password",
            19,
            Color.WHITE,
            true
    );

    TextView message = createText(
            "လက်ရှိ Password နဲ့ Password အသစ်ကို ထည့်ပါ။",
            12,
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

    /*
     * ဒီ dialog ထဲက input/button များကိုသာ
     * 54dp မှ 48dp သို့ လျှော့ထားသည်။
     */
    currentPassword.getLayoutParams().height =
            dp(48);

    newPassword.getLayoutParams().height =
            dp(48);

    confirmPassword.getLayoutParams().height =
            dp(48);

    submitButton.getLayoutParams().height =
            dp(48);

    cancelButton.getLayoutParams().height =
            dp(48);

    container.addView(title);
    addTopMargin(container, message, 6);
    addTopMargin(container, currentPassword, 15);
    addTopMargin(container, newPassword, 9);
    addTopMargin(container, confirmPassword, 9);
    addTopMargin(container, submitButton, 15);
    addTopMargin(container, cancelButton, 8);

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

    showSizedDialog(
            dialog,
            0.84f,
            370
    );
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

        /*
         * Remember me ဖွင့်ထားလျှင်
         * password အဟောင်းအစား password အသစ်ကို
         * encrypted storage ထဲပြန်သိမ်းမည်။
         */
        SessionManager
                .updateRememberedPassword(next);

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

    ImageView icon =
            new ImageView(this);

    LinearLayout.LayoutParams iconParams =
            new LinearLayout.LayoutParams(
                    dp(66),
                    dp(66)
            );

    iconParams.gravity = Gravity.CENTER_HORIZONTAL;

    icon.setLayoutParams(iconParams);
    icon.setImageResource(R.drawable.ic_logout);
    icon.setColorFilter(Color.WHITE);

    icon.setPadding(
            dp(17),
            dp(17),
            dp(17),
            dp(17)
    );

    icon.setBackground(
            roundedBackground(
                    "#3B1D23",
                    33,
                    "#E52D38"
            )
    );

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
    addTopMargin(container, title, 12);
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

        logoutLabel.setText("Please wait…");
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
    /*
     * Logout dialog အတွက် မူရင်း size ကို
     * မပြောင်းဘဲ ဆက်သုံးမည်။
     */
    showSizedDialog(
            dialog,
            0.88f,
            400
    );
}

private void showSizedDialog(
        Dialog dialog,
        float widthFraction,
        int maximumWidthDp
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
                    (int) (
                            screenWidth *
                                    widthFraction
                    ),
                    dp(maximumWidthDp)
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
