package com.cmflix.nativeapp;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;

public class CmflixApplication
        extends Application
        implements Application.ActivityLifecycleCallbacks {

    private static final int APP_BACKGROUND =
            Color.parseColor("#090B10");

    @Override
public void onCreate() {
    super.onCreate();

    /*
     * LocalStore က history key ဆောက်ရာမှာ
     * SessionManager.getUserId() ကိုသုံးသောကြောင့်
     * SessionManager ကိုအရင် initialize လုပ်မည်။
     */
    SessionManager.initialize(this);
    LocalStore.initialize(this);

    /*
     * Cached config ကို memory ထဲ load လုပ်ပြီး
     * cache မရှိလျှင် သို့မဟုတ် 24 hours ကျော်လျှင်
     * GitHub config ကို background မှာ refresh လုပ်မည်။
     */
    ConfigManager.initialize(this);

    /*
     * API public cache နဲ့ SessionManager initialization ကို
     * application startup မှာ centralized လုပ်ထားမည်။
     *
     * Activity တွေထဲက existing initialize calls တွေက
     * idempotent ဖြစ်လို့ မဖျက်လည်းရသည်။
     */
    ApiClient.initialize(this);

    registerActivityLifecycleCallbacks(this);
}


    public static void applyTheme(
            Activity activity
    ) {
        if (activity == null) {
            return;
        }

        activity.getWindow()
                .setStatusBarColor(
                        APP_BACKGROUND
                );

        activity.getWindow()
                .setNavigationBarColor(
                        APP_BACKGROUND
                );
    }

    @Override
    public void onActivityCreated(
            Activity activity,
            Bundle state
    ) {
        activity.getWindow()
                .getDecorView()
                .post(() -> applyTheme(activity));
    }

    @Override
    public void onActivityResumed(
            Activity activity
    ) {
        applyTheme(activity);
    }

    @Override
    public void onActivityStarted(
            Activity activity
    ) {
    }

    @Override
    public void onActivityPaused(
            Activity activity
    ) {
    }

    @Override
    public void onActivityStopped(
            Activity activity
    ) {
    }

    @Override
    public void onActivitySaveInstanceState(
            Activity activity,
            Bundle bundle
    ) {
    }

    @Override
    public void onActivityDestroyed(
            Activity activity
    ) {
    }
}
