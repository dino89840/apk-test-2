package com.cmflix.nativeapp;

import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TitleAdapter
        extends ListAdapter<JSONObject, TitleAdapter.Holder> {

    public interface Listener {
        void onClick(JSONObject item);
    }

    public interface RemoveFavoriteListener {
        void onRemove(JSONObject item);
    }

    private final Listener listener;
    private final RemoveFavoriteListener
            removeFavoriteListener;

    /*
     * Home horizontal rows အတွက် fixed-width layout
     * (item_title_row) ကိုလည်း ဒီ adapter တစ်ခုတည်းနဲ့
     * ပြန်သုံးနိုင်ရန်။
     */
    private final int layoutRes;

    /*
     * Series (Nosub Eng) category အတွက် landscape
     * cover layout သုံးရန်။ true ဖြစ်လျှင်
     * item_title_landscape / item_title_row_landscape
     * ကို သုံးမည်။
     */
    private boolean landscapeMode = false;

    private boolean favoriteMode = false;
private static final Object
        PAYLOAD_PROGRESS =
        new Object();

private Map<String, long[]>
        progressSnapshot =
        Collections.emptyMap();

    private static final DiffUtil.ItemCallback<JSONObject>
            DIFF_CALLBACK =
            new DiffUtil.ItemCallback<JSONObject>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull JSONObject oldItem,
                        @NonNull JSONObject newItem
                ) {
                    String oldId =
                            oldItem.optString(
                                    "id",
                                    oldItem.optString(
                                            "slug",
                                            ""
                                    )
                            );

                    String newId =
                            newItem.optString(
                                    "id",
                                    newItem.optString(
                                            "slug",
                                            ""
                                    )
                            );

                    return oldId.equals(newId);
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull JSONObject oldItem,
                        @NonNull JSONObject newItem
                ) {
                    return oldItem.toString()
                            .equals(newItem.toString());
                }
            };

    public TitleAdapter(
        Listener listener,
        RemoveFavoriteListener removeFavoriteListener
) {
    this(
            listener,
            removeFavoriteListener,
            R.layout.item_title
    );
}

public TitleAdapter(
        Listener listener,
        RemoveFavoriteListener removeFavoriteListener,
        int layoutRes
) {
    super(DIFF_CALLBACK);

    this.listener = listener;
    this.removeFavoriteListener =
            removeFavoriteListener;
    this.layoutRes = layoutRes;

    progressSnapshot =
            LocalStore.getProgressSnapshot();

    setStateRestorationPolicy(
            StateRestorationPolicy
                    .PREVENT_WHEN_EMPTY
    );
}
public void refreshProgressSnapshot() {
    progressSnapshot =
            LocalStore.getProgressSnapshot();

    int count = getItemCount();

    if (count > 0) {
        notifyItemRangeChanged(
                0,
                count,
                PAYLOAD_PROGRESS
        );
    }
}


    public void setFavoriteMode(boolean value) {
        if (favoriteMode == value) {
            return;
        }

        favoriteMode = value;
        notifyDataSetChanged();
    }

    /*
     * Landscape cover mode (series category) အဖွင့်/အပိတ်။
     * Mode ပြောင်းလျှင် list ကို ပြန်ဆွဲမည်။
     */
    public void setLandscapeMode(boolean value) {
        if (landscapeMode == value) {
            return;
        }

        landscapeMode = value;
        notifyDataSetChanged();
    }

    public boolean isLandscapeMode() {
        return landscapeMode;
    }

    /*
     * Landscape mode ဖွင့်ထားလျှင် landscape layout
     * ကို ပြန်ပေးမည်။
     */
    private int getEffectiveLayoutRes() {
        if (!landscapeMode) {
            return layoutRes;
        }

        if (layoutRes == R.layout.item_title_row) {
            return R.layout.item_title_row_landscape;
        }

        return R.layout.item_title_landscape;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        int effectiveRes = getEffectiveLayoutRes();

        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                effectiveRes,
                                parent,
                                false
                        );

        if (effectiveRes == R.layout.item_title_row
                || effectiveRes
                        == R.layout.item_title_row_landscape) {
            applyResponsiveRowWidth(parent, view);
        }

        return new Holder(view);
    }

    /*
     * Home horizontal row card များ ဖုန်းအရွယ်အစား
     * မရွေး တစ်တန်းမှာ ၃ ကား အတိအကျ အပြည့်
     * မြင်ရအောင် item width ကို row ရဲ့
     * အကျယ်အတိုင်း ချိန်ညှိသည် (fixed 124dp အစား)။
     * Grid layout (item_title) ကို မထိပါ။
     */
    private void applyResponsiveRowWidth(
            ViewGroup parent,
            View view
    ) {
        DisplayMetrics dm =
                parent.getResources()
                        .getDisplayMetrics();

        float density = dm.density;

        int rowContentPx =
                parent.getMeasuredWidth() -
                        parent.getPaddingStart() -
                        parent.getPaddingEnd();

        if (rowContentPx <= 0) {
            /*
             * Row မတိုင်းတာရသေးလျှင် screen width မှ
             * သိထားသော padding များ
             * (main list 7dp*2 + row 8dp*2) နှုတ်မည်။
             */
            rowContentPx =
                    dm.widthPixels -
                            (int) (30 * density);
        }

        /*
         * Landscape cover များ 16:9 မို့ တစ်တန်းမှာ
         * ၂ ခု ပြမည်။ Portrait poster များ ၃ ခု။
         */
        float perRow = landscapeMode ? 2.0f : 3.0f;

        int itemTotalPx =
                (int) (rowContentPx / perRow);

        /*
         * Card ရဲ့ start/end margin 4dp+4dp နှုတ်မည်။
         */
        int itemWidthPx =
                itemTotalPx -
                        (int) (8 * density);

        if (
                itemWidthPx > 0 &&
                        view.getLayoutParams()
                                instanceof
                                RecyclerView.LayoutParams
        ) {
            RecyclerView.LayoutParams lp =
                    (RecyclerView.LayoutParams)
                            view.getLayoutParams();

            lp.width = itemWidthPx;
            view.setLayoutParams(lp);
        }
    }
@Override
public void onBindViewHolder(
        @NonNull Holder holder,
        int position,
        @NonNull List<Object> payloads
) {
    if (
            !payloads.isEmpty() &&
            payloads.contains(
                    PAYLOAD_PROGRESS
            )
    ) {
        bindProgress(
                holder,
                getItem(position)
        );

        return;
    }

    super.onBindViewHolder(
            holder,
            position,
            payloads
    );
}

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position
    ) {
        JSONObject item = getItem(position);

        holder.title.setText(
                item.optString("title", "")
        );

        String localKind =
                item.optString(
                        "_local_kind",
                        ""
                );

        holder.meta.setText(
                buildMetadata(item, localKind)
        );

        String badgeLabel =
        getCategoryBadgeLabel(
                item.optString(
                        "category",
                        ""
                )
        );

holder.vipRibbon.setText(badgeLabel);

holder.vipRibbon.setVisibility(
        badgeLabel.isEmpty()
                ? View.GONE
                : View.VISIBLE
);


        String posterUrl =
                item.optString(
                        "poster_url",
                        ""
                );

        RequestBuilder<?> posterRequest =
        Glide.with(holder.poster)
                .load(posterUrl)

                /*
                 * Glide က ImageView scaleType ကိုကြည့်ပြီး
                 * fitCenter/centerCrop transformation
                 * အလိုအလျောက်ထည့်ခြင်းကိုပိတ်ထားသည်။
                 *
                 * Poster crop alignment ကို
                 * TopCropImageView က တာဝန်ယူမည်။
                 */
                .dontTransform()

                /*
                 * Original poster resolution ကြီးလွန်းလျှင်
                 * grid အတွက်လုံလောက်သော resolution ဖြင့်
                 * decode လုပ်ပြီး memory အသုံးပြုမှုလျှော့မည်။
                 *
                 * Poster frame သည် 2:3 ratio ဖြစ်သည်။
                 */
                .override(480, 720)

                /*
                 * Poster ပုံများအတွက် RGB_565 သုံးခြင်းဖြင့်
                 * bitmap memory အသုံးပြုမှုကိုလျှော့မည်။
                 */
                .format(
                        DecodeFormat.PREFER_RGB_565
                )

                /*
                 * Network poster များကို memory/disk cache မှ
                 * ပြန်လည်အသုံးပြုနိုင်စေရန်။
                 */
                .diskCacheStrategy(
                        DiskCacheStrategy.AUTOMATIC
                )

                .placeholder(R.color.card_bg)
                .error(R.color.card_bg)

                /*
                 * Poster load ပြီးချိန်တွင် နူးညံ့စွာ
                 * ပေါ်လာစေရန် cross-fade သုံးမည်။
                 */
                .transition(
                        DrawableTransitionOptions
                                .withCrossFade(180)
                );


        /*
         * Local list ကိုဖွင့်ရုံနဲ့ poster network request
         * အသစ်မတိုးစေရန် disk/memory cache ထဲမှာရှိမှသာ load။
         */
        if (!localKind.isEmpty()) {
            posterRequest =
                    posterRequest
                            .onlyRetrieveFromCache(true);
        }

        posterRequest.into(holder.poster);

        bindProgress(holder, item);

        holder.favoriteRemove.setVisibility(
                favoriteMode
                        ? View.VISIBLE
                        : View.GONE
        );

        holder.favoriteRemove.setEnabled(true);
        holder.favoriteRemove.setAlpha(1f);

        holder.favoriteRemove.setOnClickListener(
                view -> {
                    view.setEnabled(false);
                    view.setAlpha(0.55f);

                    if (
                            removeFavoriteListener !=
                                    null
                    ) {
                        removeFavoriteListener
                                .onRemove(item);
                    }
                }
        );

        holder.itemView.setOnClickListener(
                view -> listener.onClick(item)
        );
    }

    private void bindProgress(
            Holder holder,
            JSONObject item
    ) {
        long position =
                item.optLong(
                        "_position",
                        -1L
                );

        long duration =
                item.optLong(
                        "_duration",
                        -1L
                );

        if (position < 0L || duration < 0L) {
    String id =
            item.optString(
                    "id",
                    item.optString(
                            "slug",
                            ""
                    )
            ).trim();

    long[] stored =
            progressSnapshot.get(id);

    if (stored != null) {
        position = stored[0];
        duration = stored[1];
    } else {
        position = 0L;
        duration = 0L;
    }
}


        boolean show =
                position >= 10_000L &&
                duration > 0L &&
                position < duration * 0.95d;

        if (!show) {
            holder.watchProgress.setVisibility(
                    View.GONE
            );

            holder.watchProgress.setProgress(0);
            return;
        }

        int progress =
                (int) Math.min(
                        1000L,
                        Math.max(
                                0L,
                                position * 1000L /
                                        duration
                        )
                );

        holder.watchProgress.setProgress(progress);
        holder.watchProgress.setVisibility(
                View.VISIBLE
        );
    }
private String getCategoryBadgeLabel(
        String category
) {
    if (
            "movies".equalsIgnoreCase(
                    category
            ) ||
            "movie".equalsIgnoreCase(
                    category
            )
    ) {
        return "Horror";
    }

    if (
            "series".equalsIgnoreCase(
                    category
            )
    ) {
        return "Nosub Eng";
    }

    if (
            "lugyi".equalsIgnoreCase(
                    category
            )
    ) {
        return "Mmsub";
    }

    return "";
}

    private String buildMetadata(
            JSONObject item,
            String localKind
    ) {
        if ("continue".equals(localKind)) {
            long position =
                    item.optLong(
                            "_position",
                            0L
                    );

            return "RESUME • " +
                    formatTime(position);
        }

        if ("download".equals(localKind)) {
            long timestamp =
                    item.optLong(
                            "_downloaded_at",
                            0L
                    );

            if (timestamp > 0L) {
                return "Downloaded • " +
                        DateFormat
                                .getDateInstance(
                                        DateFormat.SHORT
                                )
                                .format(
                                        new Date(timestamp)
                                );
            }

            return "Downloaded";
        }

        String year =
                item.optString("year", "");

        String rating =
                item.optString("rating", "");

        StringBuilder result =
                new StringBuilder();

        if (!year.isEmpty()) {
            result.append(year);
        }

        if (!rating.isEmpty()) {
            if (result.length() > 0) {
                result.append("  •  ");
            }

            result.append("★ ")
                    .append(rating);
        }

        return result.toString();
    }

    private String formatTime(long value) {
        long seconds =
                Math.max(0L, value / 1000L);

        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remaining = seconds % 60L;

        if (hours > 0L) {
            return String.format(
                    java.util.Locale.US,
                    "%d:%02d:%02d",
                    hours,
                    minutes,
                    remaining
            );
        }

        return String.format(
                java.util.Locale.US,
                "%02d:%02d",
                minutes,
                remaining
        );
    }

    @Override
    public void onViewRecycled(
            @NonNull Holder holder
    ) {
        Glide.with(holder.poster)
                .clear(holder.poster);

        holder.favoriteRemove
                .setOnClickListener(null);

        holder.itemView
                .setOnClickListener(null);

        holder.watchProgress.setProgress(0);
        holder.watchProgress.setVisibility(
                View.GONE
        );

        super.onViewRecycled(holder);
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        final ImageView poster;
        final ImageButton favoriteRemove;
        final TextView title;
        final TextView meta;
        final TextView vipRibbon;
        final ProgressBar watchProgress;

        Holder(@NonNull View itemView) {
            super(itemView);

            poster =
                    itemView.findViewById(
                            R.id.poster
                    );

            favoriteRemove =
                    itemView.findViewById(
                            R.id.favoriteRemove
                    );

            vipRibbon =
                    itemView.findViewById(
                            R.id.vipRibbon
                    );

            title =
                    itemView.findViewById(
                            R.id.movieTitle
                    );

            meta =
                    itemView.findViewById(
                            R.id.meta
                    );

            watchProgress =
                    itemView.findViewById(
                            R.id.watchProgress
                    );
        }
    }
}