package com.cmflix.nativeapp;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.Map;

/*
 * Javtiful video title — on-device English → Burmese
 * translation via ML Kit.
 *
 * - Server request လုံးဝ မရှိ (on-device, free)
 * - Model ပထမအကြိမ် download လုပ်ရမည်
 *   (နောက်ပိုင်း offline ရ)
 * - တူညီတဲ့ title တွေ memory ထဲ cache ထား
 *   သည် (ပြန်မပြန်စေရန်)
 * - UI ကို block မလုပ်ပါ — callback async
 * - Model download / translate fail ရင်
 *   onError → caller က silently hide လုပ်မည်
 */
public class JavtifulTranslator {

    public interface Callback {
        void onTranslated(String burmese);
        void onError(Exception error);
    }

    private static volatile JavtifulTranslator instance;

    private final Translator translator;

    /*
     * title (trimmed) → burmese. Synchronized access.
     */
    private final Map<String, String> cache =
            new HashMap<>();

    private JavtifulTranslator() {
        TranslatorOptions options =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(
                                TranslateLanguage.ENGLISH
                        )
                        .setTargetLanguage(
                                TranslateLanguage.BURMESE
                        )
                        .build();

        translator =
                Translation.getClient(options);
    }

    public static JavtifulTranslator get() {
        if (instance == null) {
            synchronized (JavtifulTranslator.class) {
                if (instance == null) {
                    instance =
                            new JavtifulTranslator();
                }
            }
        }

        return instance;
    }

    /*
     * English title → Burmese. Callback သည် main
     * thread ပေါ်မှာ ခေါ်သည် (ML Kit default).
     */
    public void translateToMyanmar(
            String english,
            Callback callback
    ) {
        if (callback == null) {
            return;
        }

        String text =
                english == null ? "" : english.trim();

        if (text.isEmpty()) {
            callback.onError(
                    new IllegalArgumentException(
                            "Title မရှိပါ။"
                    )
            );
            return;
        }

        synchronized (cache) {
            String hit = cache.get(text);

            if (hit != null) {
                callback.onTranslated(hit);
                return;
            }
        }

        /*
         * Model မရှိသေးရင် download လုပ်မည် —
         * WiFi condition မထည့်ပါ (mobile data နဲ့လည်း
         * ရစေရန်; ~30MB ခန့် တစ်ကြိမ်သာ).
         */
        translator
                .downloadModelIfNeeded(
                        new DownloadConditions.Builder()
                                .build()
                )
                .addOnSuccessListener(
                        unused ->
                                doTranslate(
                                        text, callback
                                )
                )
                .addOnFailureListener(callback::onError);
    }

    private void doTranslate(
            String text,
            Callback callback
    ) {
        translator
                .translate(text)
                .addOnSuccessListener(
                        burmese -> {
                            String out =
                                    burmese == null
                                            ? ""
                                            : burmese
                                                    .trim();

                            if (out.isEmpty()) {
                                callback.onError(
                                        new IllegalStateException(
                                                "ဘာသာပြန်ချက်"
                                                        + " မရပါ။"
                                        )
                                );
                                return;
                            }

                            synchronized (cache) {
                                cache.put(text, out);
                            }

                            callback.onTranslated(out);
                        }
                )
                .addOnFailureListener(callback::onError);
    }

    /*
     * App ပိတ်ခါနီး (optional) — ပုံမှန်အားဖြင့်
     * ခေါ်စရာ မလိုပါ (singleton က app
     * lifecycle အတိုင်း နေမည်).
     */
    public void close() {
        try {
            translator.close();
        } catch (Exception ignored) {
        }
    }
}