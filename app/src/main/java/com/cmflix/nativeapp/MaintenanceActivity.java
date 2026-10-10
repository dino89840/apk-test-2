package com.cmflix.nativeapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/*
 * Maintenance mode full-screen။
 *
 * Backend /app-content ရဲ့ maintenance flag true
 * ဖြစ်နေလျှင် MainActivity က ဒီ screen ကို ဖွင့်မည်။
 *
 * - Message ကို intent extra EXTRA_MESSAGE ကနေ ယူမည်
 *   (MainActivity က AppContentManager
 *   .getMaintenanceMessage() နဲ့ ဖြည့်ပေးသည်)။
 * - "ထွက်မယ်" နှိပ်လျှင် app ကို အပြီးပိတ်မည်
 *   (finishAffinity)။
 */
public class MaintenanceActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE =
            "maintenance_message";

    private TextView messageView;
    private Button exitButton;

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

        exitButton =
                findViewById(
                        R.id.maintenanceExitButton
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

        exitButton.setOnClickListener(
                v -> finishAffinity()
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
