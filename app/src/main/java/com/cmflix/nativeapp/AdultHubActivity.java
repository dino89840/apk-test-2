package com.cmflix.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/*
 * 18+ hub — bottom nav "18+" tab မှ ဖွင့်သည်။
 *
 * Card ၄ ခု:
 *  1. Nosub  → MainActivity (category "series")
 *  2. Mmsub  → MainActivity (category "lugyi")
 *  3. Jav   → JavtifulActivity (mode "mosaic")
 *  4. Asian → JavtifulActivity (mode "uncensored")
 *     (Jav + Asian kill-switch: javtiful.enabled=false
 *      → နှစ်ခုလုံး ဝှက်မည်)
 *
 * Horror က သီးသန့် tab အတိုင်း (ဒီထဲမပါ)။
 * PIN မရှိပါ။
 */
public class AdultHubActivity extends AppCompatActivity {

    private View javCard;
    private View asianCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_adult_hub);

        findViewById(R.id.adultHubBackButton)
                .setOnClickListener(view -> finish());

        javCard = findViewById(R.id.adultHubJav);
        asianCard = findViewById(R.id.adultHubAsian);

        findViewById(R.id.adultHubNosub)
                .setOnClickListener(
                        view -> openMainCategory("series")
                );

        findViewById(R.id.adultHubMmsub)
                .setOnClickListener(
                        view -> openMainCategory("lugyi")
                );

        javCard.setOnClickListener(
                view -> openJavtiful(
                        JavtifulActivity.MODE_MOSAIC
                )
        );

        asianCard.setOnClickListener(
                view -> openJavtiful(
                        JavtifulActivity.MODE_UNCENSORED
                )
        );

        /*
         * Javtiful kill-switch — /app-content ရဲ့
         * javtiful.enabled flag ကို စစ်သည်။
         * Jav + Asian နှစ်ခုလုံး ထိန်းသည်။
         * Network request အသစ် မရှိပါ။
         */
        updateJavtifulCards(null);

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

                            updateJavtifulCards(content);
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

    private void openJavtiful(String mode) {
        Intent intent =
                new Intent(
                        this, JavtifulActivity.class
                );

        intent.putExtra(
                JavtifulActivity.EXTRA_MODE, mode
        );

        startActivity(intent);
    }

    /*
     * Jav + Asian card visibility — kill-switch။
     * enabled=true (သို့မဟုတ် field မရှိသေးလျှင်
     * default true) → ပြမည်။ Explicitly false →
     * နှစ်ခုလုံး ဝှက်မည်။
     * Nosub/Mmsub က အမြဲပြမည်။
     */
    private void updateJavtifulCards(JSONObject content) {
        if (javCard == null || asianCard == null) {
            return;
        }

        boolean enabled =
                AppContentManager.isJavtifulEnabled(
                        content
                );

        int visibility =
                enabled ? View.VISIBLE : View.GONE;

        javCard.setVisibility(visibility);
        asianCard.setVisibility(visibility);
    }
}