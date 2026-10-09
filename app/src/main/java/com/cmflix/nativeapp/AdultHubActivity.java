package com.cmflix.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/*
 * 18+ hub — bottom nav "18+" tab မှ ဖွင့်သည်။
 *
 * Card ၃ ခု:
 *  1. Nosub  → MainActivity (category "series")
 *  2. Mmsub  → MainActivity (category "lugyi")
 *  3. Javtiful → JavtifulActivity
 *     (kill-switch: javtiful.enabled=false → ဝှက်မည်)
 *
 * Horror က သီးသန့် tab အတိုင်း (ဒီထဲမပါ)။
 * PIN မရှိပါ။
 */
public class AdultHubActivity extends AppCompatActivity {

    private View javtifulCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_adult_hub);

        findViewById(R.id.adultHubBackButton)
                .setOnClickListener(view -> finish());

        javtifulCard = findViewById(R.id.adultHubJavtiful);

        findViewById(R.id.adultHubNosub)
                .setOnClickListener(
                        view -> openMainCategory("series")
                );

        findViewById(R.id.adultHubMmsub)
                .setOnClickListener(
                        view -> openMainCategory("lugyi")
                );

        javtifulCard.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        JavtifulActivity.class
                                )
                        )
        );

        /*
         * Javtiful kill-switch — /app-content ရဲ့
         * javtiful.enabled flag ကို စစ်သည်။
         * Network request အသစ် မရှိပါ။
         */
        updateJavtifulCard(null);

        AppContentManager.loadBanner(
                this,
                new AppContentManager.Callback() {
                    @Override
                    public void onContent(
                            JSONObject content
                    ) {
                        runOnUiThread(() -> {
                            if (
                                    isFinishing() ||
                                            isDestroyed()
                            ) {
                                return;
                            }

                            updateJavtifulCard(content);
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        // default (visible) အတိုင်း ထားမည်
                    }
                }
        );
    }

    private void openMainCategory(String category) {
        Intent intent =
                new Intent(this, MainActivity.class);

        intent.putExtra("open_category", category);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }

    /*
     * Javtiful card visibility — kill-switch။
     * enabled=true (သို့မဟုတ် field မရှိသေးလျှင်
     * default true) → ပြမည်။ Explicitly false → ဝှက်မည်။
     * Nosub/Mmsub က အမြဲပြမည်။
     */
    private void updateJavtifulCard(JSONObject content) {
        if (javtifulCard == null) {
            return;
        }

        boolean enabled =
                AppContentManager.isJavtifulEnabled(
                        content
                );

        javtifulCard.setVisibility(
                enabled ? View.VISIBLE : View.GONE
        );
    }
}