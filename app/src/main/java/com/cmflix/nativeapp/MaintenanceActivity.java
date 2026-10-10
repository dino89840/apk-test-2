package com.cmflix.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/*
 * Maintenance mode full-screen။
 *
 * Backend /app-content ရဲ့ maintenance flag true
 * ဖြစ်နေလျှင် MainActivity က ဒီ screen ကို ဖွင့်မည်။
 *
 * - Message ကို intent extra EXTRA_MESSAGE ကနေ ယူမည်
 *   (MainActivity က AppContentManager
 *   .getMaintenanceMessage() နဲ့ ဖြည့်ပေးသည်)။
 * - "ပြန်စမ်းမယ်" နှိပ်လျှင် /app-content ကို network
 *   ကနေ ပြန်ခေါ်ပြီး (user-initiated, spam မဖြစ်)
 *   maintenance ပိတ်သွားပြီလား စစ်မည်။
 * - Maintenance ပိတ်သွားလျှင် MainActivity ဖွင့်ပြီး
 *   ဒီ screen ကို ပိတ်မည်။
 * - Network error ဖြစ်လျှင် ဒီ screen မှာပဲ ဆက်နေမည်။
 */
public class MaintenanceActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE =
            "maintenance_message";

    private TextView messageView;
    private Button retryButton;
    private ProgressBar progressView;

    private boolean checking = false;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_maintenance
        );

        messageView =
                findViewById(
                        R.id.maintenanceMessage
                );

        retryButton =
                findViewById(
                        R.id.maintenanceRetryButton
                );

        progressView =
                findViewById(
                        R.id.maintenanceProgress
                );

        String message =
                getIntent()
                        .getStringExtra(EXTRA_MESSAGE);

        if (
                message == null ||
                        message.trim().isEmpty()
        ) {
            message =
                    AppContentManager
                            .getMaintenanceMessage(
                                    null
                            );
        }

        messageView.setText(message);

        retryButton.setOnClickListener(
                v -> checkAgain()
        );
    }

    private void checkAgain() {
        if (checking) {
            return;
        }

        checking = true;

        retryButton.setEnabled(false);

        progressView.setVisibility(
                View.VISIBLE
        );

        AppContentManager.refreshAppContentNow(
                this,
                new AppContentManager.Callback() {
                    @Override
                    public void onContent(
                            JSONObject content
                    ) {
                        runOnUiThread(() -> {
                            checking = false;

                            if (isFinishing()) {
                                return;
                            }

                            if (
                                    !AppContentManager
                                            .isMaintenanceMode(
                                                    content
                                            )
                            ) {
                                Intent intent =
                                        new Intent(
                                                MaintenanceActivity.this,
                                                MainActivity.class
                                        );

                                startActivity(intent);

                                finish();
                            } else {
                                messageView.setText(
                                        AppContentManager
                                                .getMaintenanceMessage(
                                                        content
                                                )
                                );

                                retryButton.setEnabled(
                                        true
                                );

                                progressView.setVisibility(
                                        View.GONE
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        runOnUiThread(() -> {
                            checking = false;

                            if (isFinishing()) {
                                return;
                            }

                            retryButton.setEnabled(
                                    true
                            );

                            progressView.setVisibility(
                                    View.GONE
                            );

                            Toast.makeText(
                                            MaintenanceActivity.this,
                                            "အင်တာနက်ချိတ်ဆက်မှု မရှိပါ။",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show();
                        });
                    }
                }
        );
    }

    @Override
    public void onBackPressed() {
        /*
         * Maintenance ပြနေချိန် back နှိပ်၍
         * app ထဲ ဝင်သွားတာမျိုး မဖြစ်စေရန်။
         */
    }
}