package com.cmflix.nativeapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

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
class VipBannerAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<VipBannerAdapter.BannerViewHolder>() {

    fun interface Listener {
        /*
         * Banner ImageView bind လုပ်တိုင်း ခေါ်သည် —
         * image (local/remote) နှင့် click listener
         * ထားရန်။
         */
        fun onBindVipBanner(bannerView: ImageView)
    }

    private var isVisible = false

    /*
     * Banner ပြသင့်/မသင့် ပြောင်းလျှင် ခေါ်သည်။
     * Insert/remove notification ဖြင့် feed က
     * သူ့ဟာသူ ရွေ့သွားမည်။
     */
    fun setVisible(visible: Boolean) {
        if (this.isVisible == visible) {
            return
        }

        this.isVisible = visible

        if (visible) {
            notifyItemInserted(0)
        } else {
            notifyItemRemoved(0)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BannerViewHolder {
        val bannerView = LayoutInflater
            .from(parent.context)
            .inflate(
                R.layout.item_vip_banner,
                parent,
                false
            ) as ImageView

        return BannerViewHolder(bannerView)
    }

    override fun onBindViewHolder(
        holder: BannerViewHolder,
        position: Int
    ) {
        listener.onBindVipBanner(
            holder.bannerView
        )
    }

    override fun getItemCount(): Int {
        return if (isVisible) 1 else 0
    }

    class BannerViewHolder(
        val bannerView: ImageView
    ) : RecyclerView.ViewHolder(bannerView)
}
