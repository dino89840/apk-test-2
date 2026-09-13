package com.cmflix.nativeapp;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

public class CmflixApplication
        extends Application
        implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onCreate() {
        super.onCreate();

        LocalStore.initialize(this);
        registerActivityLifecycleCallbacks(this);
    }

    public static void applyTheme(
            Activity activity
    ) {
        boolean amoled =
                LocalStore.isAmoledTheme();

        int background =
                amoled
                        ? Color.BLACK
                        : Color.parseColor("#090B10");

        activity.getWindow()
                .setStatusBarColor(background);

        activity.getWindow()
                .setNavigationBarColor(background);

        View content =
                activity.findViewById(
                        android.R.id.content
                );

        if (content instanceof ViewGroup) {
            ViewGroup group =
                    (ViewGroup) content;

            if (group.getChildCount() > 0) {
                group.getChildAt(0)
                        .setBackgroundColor(background);
            }
        }
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
    public void onActivityResumed(Activity activity) {
        applyTheme(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(
            Activity activity,
            Bundle bundle
    ) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
