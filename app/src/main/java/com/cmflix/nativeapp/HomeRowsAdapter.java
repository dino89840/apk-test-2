package com.cmflix.nativeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/*
 * Home feed ရဲ့ ထိပ်ပိုင်း header (ConcatAdapter ရဲ့
 * ပထမ adapter)။ Continue Watching / Recently Viewed
 * horizontal rows များနှင့် My Favorites navigation row
 * ကိုပြသည်။
 *
 * Data အားလုံး LocalStore (ဖုန်းတွင်း) ကသာ ရသည် —
 * network request အသစ် လုံးဝမရှိပါ။
 */
public class HomeRowsAdapter
        extends RecyclerView.Adapter<HomeRowsAdapter.Holder> {

    public interface Listener {
        void onItemClick(JSONObject item);

        void onSeeAllContinue();

        void onSeeAllRecent();

        void onOpenFavorites();
    }

    private final Listener listener;

    private List<JSONObject> continueItems =
            Collections.emptyList();
    private List<JSONObject> recentItems =
            Collections.emptyList();
    private boolean showFavorites = false;

    public HomeRowsAdapter(Listener listener) {
        this.listener = listener;
    }

    /*
     * Section တစ်ခုမှ မရှိလျှင် header item ကိုပါ
     * မပြပါ (grid သာပြမည်)။
     */
    @Override
    public int getItemCount() {
        if (
                !continueItems.isEmpty() ||
                        !recentItems.isEmpty() ||
                        showFavorites
        ) {
            return 1;
        }

        return 0;
    }

    @Override
    public int getItemViewType(int position) {
        return 1001;
    }

    public void setData(
            List<JSONObject> newContinueItems,
            List<JSONObject> newRecentItems,
            boolean newShowFavorites
    ) {
        boolean hadItem = getItemCount() > 0;

        continueItems =
                newContinueItems != null
                        ? newContinueItems
                        : Collections.emptyList();
        recentItems =
                newRecentItems != null
                        ? newRecentItems
                        : Collections.emptyList();
        showFavorites = newShowFavorites;

        boolean hasItem = getItemCount() > 0;

        if (hadItem && hasItem) {
            notifyItemChanged(0);
        } else if (hadItem) {
            notifyItemRemoved(0);
        } else if (hasItem) {
            notifyItemInserted(0);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                R.layout.home_rows_header,
                                parent,
                                false
                        );

        return new Holder(view, listener);
    }

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position
    ) {
        holder.bind(
                continueItems,
                recentItems,
                showFavorites
        );
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        private final LinearLayout continueSection;
        private final LinearLayout recentSection;
        private final LinearLayout favoritesNavRow;
        private final RecyclerView continueRow;
        private final RecyclerView recentRow;

        private final TitleAdapter continueAdapter;
        private final TitleAdapter recentAdapter;

        Holder(
                @NonNull View itemView,
                Listener listener
        ) {
            super(itemView);

            continueSection =
                    itemView.findViewById(
                            R.id.continueSection
                    );
            recentSection =
                    itemView.findViewById(
                            R.id.recentSection
                    );
            favoritesNavRow =
                    itemView.findViewById(
                            R.id.favoritesNavRow
                    );
            continueRow =
                    itemView.findViewById(
                            R.id.continueRow
                    );
            recentRow =
                    itemView.findViewById(
                            R.id.recentRow
                    );

            TitleAdapter.Listener openDetail =
                    listener::onItemClick;

            continueAdapter =
                    new TitleAdapter(
                            openDetail,
                            item -> {
                            },
                            R.layout.item_title_row
                    );
            recentAdapter =
                    new TitleAdapter(
                            openDetail,
                            item -> {
                            },
                            R.layout.item_title_row
                    );

            continueRow.setLayoutManager(
                    new LinearLayoutManager(
                            itemView.getContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );
            continueRow.setAdapter(continueAdapter);
            continueRow.setHasFixedSize(true);
            continueRow.setNestedScrollingEnabled(false);

            recentRow.setLayoutManager(
                    new LinearLayoutManager(
                            itemView.getContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );
            recentRow.setAdapter(recentAdapter);
            recentRow.setHasFixedSize(true);
            recentRow.setNestedScrollingEnabled(false);

            itemView.findViewById(R.id.seeAllContinue)
                    .setOnClickListener(
                            view ->
                                    listener.onSeeAllContinue()
                    );
            itemView.findViewById(R.id.seeAllRecent)
                    .setOnClickListener(
                            view ->
                                    listener.onSeeAllRecent()
                    );
            favoritesNavRow.setOnClickListener(
                    view ->
                            listener.onOpenFavorites()
            );
        }

        void bind(
                List<JSONObject> newContinueItems,
                List<JSONObject> newRecentItems,
                boolean newShowFavorites
        ) {
            boolean showContinue =
                    !newContinueItems.isEmpty();
            boolean showRecent =
                    !newRecentItems.isEmpty();

            continueSection.setVisibility(
                    showContinue
                            ? View.VISIBLE
                            : View.GONE
            );
            recentSection.setVisibility(
                    showRecent
                            ? View.VISIBLE
                            : View.GONE
            );
            favoritesNavRow.setVisibility(
                    newShowFavorites
                            ? View.VISIBLE
                            : View.GONE
            );

            if (showContinue) {
                continueAdapter.submitList(
                        newContinueItems
                );
                continueAdapter.refreshProgressSnapshot();
            }

            if (showRecent) {
                recentAdapter.submitList(
                        newRecentItems
                );
                recentAdapter.refreshProgressSnapshot();
            }
        }
    }
}
