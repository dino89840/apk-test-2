package com.cmflix.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/*
 * မြန်မာ hub — bottom nav "Myanmar+All" tab မှ
 * ဖွင့်သည် (drawer menu မှလည်း)။
 *
 * Card ၂ ခု:
 *  1. Myanmar 1 → MyanmarActivity (source "mmtube")
 *     mmtube.net တိုက်ရိုက် (လက်ရှိ VPN မလိုသေး)
 *  2. Myanmar 2 → MyanmarActivity (source "samusar")
 *     samusar.com တိုက်ရိုက် (မြန်မာမှ VPN လိုနိုင်)
 *
 * Card အောက်တွင် VPN info box:
 * "ပုံများ Videoများ ကြည့်မရပါက VPN သုံးပါ"
 *
 * PIN မရှိပါ။
 */
public class MyanmarHubActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_myanmar_hub);

        findViewById(R.id.myanmarHubBackButton)
                .setOnClickListener(view -> finish());

        findViewById(R.id.myanmarHubSource1)
                .setOnClickListener(
                        view -> openMyanmar(
                                MyanmarActivity.SOURCE_MMTUBE
                        )
                );

        findViewById(R.id.myanmarHubSource2)
                .setOnClickListener(
                        view -> openMyanmar(
                                MyanmarActivity.SOURCE_SAMUSAR
                        )
                );
    }

    private void openMyanmar(String source) {
        Intent intent =
                new Intent(
                        this,
                        MyanmarActivity.class
                );

        intent.putExtra(
                MyanmarActivity.EXTRA_SOURCE,
                source
        );

        startActivity(intent);
    }
}
