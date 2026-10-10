package com.cmflix.nativeapp;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*
 * Landscape cover များကို standard 16:9 aspect ratio ဖြင့်
 * ဖုန်း/Tablet screen width အလိုက် အလိုအလျောက်တိုင်းပေးမည်။
 *
 * Width 160px ဖြစ်ပါက height 90px ဖြစ်မည်။
 * (PosterAspectFrameLayout ၏ 2:3 ဗားရှင်း)
 */
public final class CoverAspectFrameLayout
        extends FrameLayout {

    private static final float
            COVER_HEIGHT_RATIO = 9f / 16f;

    public CoverAspectFrameLayout(
            @NonNull Context context
    ) {
        super(context);
    }

    public CoverAspectFrameLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
    }

    public CoverAspectFrameLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(
                context,
                attrs,
                defStyleAttr
        );
    }

    @Override
    protected void onMeasure(
            int widthMeasureSpec,
            int heightMeasureSpec
    ) {
        int width =
                MeasureSpec.getSize(
                        widthMeasureSpec
                );

        /*
         * RecyclerView/GridLayoutManager က width ကို
         * သတ်မှတ်ပေးထားမည်။ မသတ်မှတ်ရသေးသောအခြေအနေအတွက်
         * ပုံမှန် measure ကို fallback သုံးထားသည်။
         */
        if (
                MeasureSpec.getMode(
                        widthMeasureSpec
                ) == MeasureSpec.UNSPECIFIED
        ) {
            super.onMeasure(
                    widthMeasureSpec,
                    heightMeasureSpec
            );

            width = getMeasuredWidth();
        }

        int height =
                Math.round(
                        width *
                                COVER_HEIGHT_RATIO
                );

        int exactWidthSpec =
                MeasureSpec.makeMeasureSpec(
                        width,
                        MeasureSpec.EXACTLY
                );

        int exactHeightSpec =
                MeasureSpec.makeMeasureSpec(
                        height,
                        MeasureSpec.EXACTLY
                );

        super.onMeasure(
                exactWidthSpec,
                exactHeightSpec
        );
    }
}