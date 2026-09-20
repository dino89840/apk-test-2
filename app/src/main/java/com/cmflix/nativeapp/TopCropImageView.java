package com.cmflix.nativeapp;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Poster ပုံကို view အပြည့်ဖြည့်ပြီး crop လုပ်ပေးသော ImageView ဖြစ်သည်။
 *
 * ပုံ၏ width ကို အလယ်တည့်တည့်ထားမည်။
 * ပုံ၏အပေါ်ပိုင်းကို view အပေါ်ဘက်တွင်ကပ်ထားမည်။
 *
 * Source ပုံက poster frame ထက် အမြင့်ပိုရှည်ပါက
 * အပေါ်ပိုင်းကိုမဖြတ်ဘဲ အောက်ပိုင်းကိုသာ crop လုပ်မည်။
 */
public final class TopCropImageView
        extends AppCompatImageView {

    private final Matrix topCropMatrix =
            new Matrix();

    public TopCropImageView(
            @NonNull Context context
    ) {
        super(context);
        initialize();
    }

    public TopCropImageView(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        initialize();
    }

    public TopCropImageView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(
                context,
                attrs,
                defStyleAttr
        );

        initialize();
    }

    private void initialize() {
        /*
         * ImageView ရဲ့ built-in centerCrop ကို မသုံးဘဲ
         * ကိုယ်ပိုင် Matrix ဖြင့် top-aligned crop
         * ပြုလုပ်မည်။
         */
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    public void setImageDrawable(
            @Nullable Drawable drawable
    ) {
        super.setImageDrawable(drawable);
        updateTopCropMatrix();
    }

    @Override
    protected void onSizeChanged(
            int width,
            int height,
            int oldWidth,
            int oldHeight
    ) {
        super.onSizeChanged(
                width,
                height,
                oldWidth,
                oldHeight
        );

        updateTopCropMatrix();
    }

    private void updateTopCropMatrix() {
        Drawable drawable = getDrawable();

        int viewWidth =
                getWidth() -
                        getPaddingLeft() -
                        getPaddingRight();

        int viewHeight =
                getHeight() -
                        getPaddingTop() -
                        getPaddingBottom();

        if (
                drawable == null ||
                viewWidth <= 0 ||
                viewHeight <= 0
        ) {
            return;
        }

        int drawableWidth =
                drawable.getIntrinsicWidth();

        int drawableHeight =
                drawable.getIntrinsicHeight();

        /*
         * ColorDrawable placeholder လို intrinsic size
         * မရှိသော drawable ဖြစ်ပါက matrix calculation
         * မလုပ်ပါ။
         */
        if (
                drawableWidth <= 0 ||
                drawableHeight <= 0
        ) {
            topCropMatrix.reset();
            setImageMatrix(topCropMatrix);
            return;
        }

        /*
         * View ကိုအပြည့်ဖုံးနိုင်သော scale ကိုရွေးမည်။
         * ဒါကြောင့် ပုံရဲ့ aspect ratio ကိုမပျက်စေဘဲ
         * poster frame အပြည့်ပြနိုင်မည်။
         */
        float scale =
                Math.max(
                        (float) viewWidth /
                                (float) drawableWidth,
                        (float) viewHeight /
                                (float) drawableHeight
                );

        float scaledWidth =
                drawableWidth * scale;

        /*
         * Width က ပိုကျယ်နေလျှင် ဘယ်/ညာကို
         * ပမာဏတူညီစွာ crop လုပ်မည်။
         */
        float translateX =
                (viewWidth - scaledWidth) / 2f;

        /*
         * Y position ကို zero ထားသောကြောင့်
         * ပုံ၏အပေါ်ပိုင်းကို အမြဲထိန်းထားပြီး
         * ပိုနေသောအောက်ပိုင်းကိုသာ crop လုပ်မည်။
         */
        float translateY = 0f;

        topCropMatrix.reset();

        topCropMatrix.setScale(
                scale,
                scale
        );

        topCropMatrix.postTranslate(
                getPaddingLeft() + translateX,
                getPaddingTop() + translateY
        );

        setImageMatrix(topCropMatrix);
    }
}
