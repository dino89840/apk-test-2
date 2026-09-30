package com.cmflix.nativeapp;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/*
 * VIP price banner ကို home feed ရဲ့ ပထမဆုံး item
 * အဖြစ် ပြသည်။
 *
 * အရင်က fixed header ထဲမှာထားသဖြင့် scroll ဆွဲလည်း
 * အပေါ်မှာ ကပ်နေခဲ့သည်။ ယခု feed ထဲထည့်လိုက်သဖြင့်
 * scroll နဲ့အတူ အပေါ်ကို ပါသွားပြီး ပျောက်မည် —
 * အောက်က Mmsub 18+ ကြည့်နေချိန် banner မရှိတော့ပါ။
 * အပေါ်ပြန်ဆွဲလျှင် banner ပြန်ပေါ်မည်။
 *
 * Full-grid view တွင် item count ကို 0 ထားသဖြင့်
 * banner မပါဝင်ပါ။
 */
public class VipBannerAdapter
        extends RecyclerView.Adapter<
                VipBannerAdapter.BannerViewHolder> {

    public interface Listener {
        /*
         * Banner ImageView bind လုပ်တိုင်း ခေါ်သည် —
         * image (local/remote) နှင့် click listener
         * ထားရန်။
         */
        void onBindVipBanner(
                ImageView bannerView
        );
    }

    private final Listener listener;

    private boolean visible = false;

    public VipBannerAdapter(
            Listener listener
    ) {
        this.listener = listener;
    }

    /*
     * Banner ပြသင့်/မသင့် ပြောင်းလျှင် ခေါ်သည်။
     * Insert/remove notification ဖြင့် feed က
     * သူ့ဟာသူ ရွေ့သွားမည်။
     */
    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }

        this.visible = visible;

        if (visible) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ImageView bannerView =
                (ImageView) LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                R.layout.item_vip_banner,
                                parent,
                                false
                        );

        return new BannerViewHolder(bannerView);
    }

    @Override
    public void onBindViewHolder(
            @NonNull BannerViewHolder holder,
            int position
    ) {
        listener.onBindVipBanner(
                holder.bannerView
        );
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    static class BannerViewHolder
            extends RecyclerView.ViewHolder {

        final ImageView bannerView;

        BannerViewHolder(
                @NonNull ImageView bannerView
        ) {
            super(bannerView);
            this.bannerView = bannerView;
        }
    }
}
