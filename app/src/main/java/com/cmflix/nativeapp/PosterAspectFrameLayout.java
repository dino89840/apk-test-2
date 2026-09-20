package com.cmflix.nativeapp;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*
 * Movie poster များကို standard 2:3 aspect ratio ဖြင့်
 * ဖုန်း/Tablet screen width အလိုက် အလိုအလျောက်တိုင်းပေးမည်။
 *
 * Width 200px ဖြစ်ပါက height 300px ဖြစ်မည်။
 * Width 300px ဖြစ်ပါက height 450px ဖြစ်မည်။
 */
public final class PosterAspectFrameLayout
        extends FrameLayout {

    private static final float
            POSTER_HEIGHT_RATIO = 3f / 2f;

    public PosterAspectFrameLayout(
            @NonNull Context context
    ) {
        super(context);
    }

    public PosterAspectFrameLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
    }

    public PosterAspectFrameLayout(
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
                                POSTER_HEIGHT_RATIO
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
