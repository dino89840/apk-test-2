package com.cmflix.nativeapp;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * In-app APK update checker။
 *
 * /app-content မှ apk.versionCode + apk.url ကို
 * ဖတ်ပြီး BuildConfig.VERSION_CODE နှင့် နှိုင်းယှဉ်သည်။
 *
 * Network request အသစ် လုံးဝ မလို — MainActivity ၏
 * loadBanner callback မှ ရသော cached JSON ကိုသုံးသည်။
 *
 * Update ရှိလျှင် dialog ပြပြီး DownloadManager ဖြင့်
 * download ဆွဲကာ install intent ပို့သည်။
 */
public final class ApkUpdateChecker {

    private static final AtomicBoolean
            CHECKED_THIS_SESSION =
            new AtomicBoolean(false);

    private static final AtomicBoolean
            DISMISSED_THIS_SESSION =
            new AtomicBoolean(false);

    private ApkUpdateChecker() {
    }

    /*
     * MainActivity ၏ app-content callback မှ ခေါ်မည်။
     * Session တစ်ခုမှာ တစ်ခါသာ စစ်မည်။
     */
    public static void checkForUpdate(
            MainActivity activity,
            JSONObject content
    ) {
        if (activity == null || content == null) {
            return;
        }

        if (!CHECKED_THIS_SESSION.compareAndSet(
                false, true)) {
            return;
        }

        if (DISMISSED_THIS_SESSION.get()) {
            return;
        }

        int remoteVersion =
                AppContentManager.getApkVersionCode(
                        content);

        String apkUrl =
                AppContentManager.getApkUrl(content);

        if (remoteVersion <= 0 || apkUrl.isEmpty()) {
            return;
        }

        int installedVersion;
        try {
            installedVersion =
                    activity.getPackageManager()
                            .getPackageInfo(
                                    activity.getPackageName(),
                                    0)
                            .versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }

        if (remoteVersion <= installedVersion) {
            return;
        }

        activity.runOnUiThread(() ->
                showUpdateDialog(
                        activity, apkUrl, remoteVersion));
    }

    private static void showUpdateDialog(
            MainActivity activity,
            String apkUrl,
            int remoteVersion
    ) {
        if (activity.isFinishing() ||
                activity.isDestroyed()) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Update ရရှိနိုင်ပါတယ်")
                .setMessage(
                        "CM FLIX version အသစ် ထွက်ပါပြီ။\n" +
                        "အသစ်တင်ရန် Update ကို နှိပ်ပါ။")
                .setPositiveButton("Update",
                        (dialog, which) -> {
                            dialog.dismiss();
                            downloadAndInstall(
                                    activity, apkUrl);
                        })
                .setNegativeButton("နောက်မှ",
                        (dialog, which) -> {
                            DISMISSED_THIS_SESSION.set(
                                    true);
                            dialog.dismiss();
                        })
                .setCancelable(false)
                .show();
    }

    private static void downloadAndInstall(
            MainActivity activity,
            String apkUrl
    ) {
        // Android 8+ — unknown sources install permission
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {
            if (!activity.getPackageManager()
                    .canRequestPackageInstalls()) {
                Toast.makeText(
                        activity,
                        "Install လုပ်ရန် Settings မှာ " +
                        "Unknown apps ခွင့်ပြုပေးပါ။",
                        Toast.LENGTH_LONG).show();
                try {
                    Intent settingsIntent =
                            new Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse(
                                            "package:" +
                                            activity.getPackageName()));
                    activity.startActivity(
                            settingsIntent);
                } catch (ActivityNotFoundException e) {
                    // settings မပွင့်လျှင် ဆက်လက်မလုပ်ပါ
                }
                return;
            }
        }

        try {
            String fileName =
                    "CMFLIX-update.apk";

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(apkUrl));

            request.setTitle("CM FLIX Update");
            request.setDescription(
                    "Version အသစ် download လုပ်နေပါတယ်…");
            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName);
            request.setMimeType(
                    "application/vnd.android.package-archive");

            DownloadManager dm =
                    (DownloadManager) activity
                            .getSystemService(
                                    Context.DOWNLOAD_SERVICE);

            if (dm == null) {
                return;
            }

            long downloadId = dm.enqueue(request);

            // Download ပြီးကြောင်း သိရန် — poll လုပ်မည့်အစား
            // user ကို notification ကနေ install လုပ်ခိုင်းမည်။
            // ရိုးရှင်းစေရန်: download ပြီးမှ auto-install
            // လုပ်ရန် BroadcastReceiver မထည့်ပါ —
            // notification ကို နှိပ်၍ install လုပ်နိုင်သည်။
            Toast.makeText(
                    activity,
                    "Download စတင်ပါပြီ။ ပြီးရင် " +
                    "notification ကို နှိပ်၍ install လုပ်ပါ။",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(
                    activity,
                    "Download မအောင်မြင်ပါ။ ပြန်စမ်းကြည့်ပါ။",
                    Toast.LENGTH_LONG).show();
        }
    }

    /*
     * Downloaded APK ကို install လုပ်ရန် intent။
     * (လိုအပ်လျှင် အခြား activity မှ ခေါ်နိုင်သည်။)
     */
    public static void installDownloadedApk(
            Context context,
            File apkFile
    ) {
        if (context == null || apkFile == null ||
                !apkFile.exists()) {
            return;
        }

        Uri uri;
        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() +
                            ".fileprovider",
                    apkFile);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(apkFile);
        }

        intent.setDataAndType(
                uri,
                "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    context,
                    "Install မလုပ်နိုင်ပါ။",
                    Toast.LENGTH_LONG).show();
        }
    }
}