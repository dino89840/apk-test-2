package com.cmflix.nativeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Home feed ရဲ့ ထိပ်ပိုင်း header (ConcatAdapter ရဲ့
 * ပထမ adapter)။ Section တစ်ခုချင်းစီ (ဥပမာ Continue
 * Watching, Horror နောက်ဆုံး ၁၀ ကား) ကို title +
 * "More >" + horizontal poster row အဖြစ် ပြသည်။
 *
 * Section များသည် dynamic ဖြစ်သည် — MainActivity က
 * setSections() ဖြင့် ပေးသည်။ Item မရှိသော section
 * များကို Activity ဘက်က filter လုပ်ပြီးသားဖြစ်သည်။
 *
 * Network data လိုသော section များ
 * (Horror / 18+) အတွက် Activity က session cache မှ
 * ပေးသည် — ဒီ adapter က request အသစ်မခေါ်ပါ။
 *
 * Horizontal row များတွင် setHasFixedSize(true)
 * မခေါ်ရ (wrap_content height နှင့် တွဲလျှင်
 * lintVitalRelease က InvalidSetHasFixedSize ဖြင့်
 * build ကျသည်)။
 */
public class HomeRowsAdapter
        extends RecyclerView.Adapter<HomeRowsAdapter.Holder> {

    public static class HomeSection {
        public final String id;
        public final String title;
        public final List<JSONObject> items;

        public HomeSection(
                String id,
                String title,
                List<JSONObject> items
        ) {
            this.id = id;
            this.title = title;
            this.items =
                    items != null
                            ? items
                            : Collections.emptyList();
        }
    }

    public interface Listener {
        void onItemClick(JSONObject item);

        void onSectionMore(HomeSection section);
    }

    private final Listener listener;

    private List<HomeSection> sections =
            Collections.emptyList();

    public HomeRowsAdapter(Listener listener) {
        this.listener = listener;
    }

    /*
     * Section တစ်ခုမှ မရှိလျှင် header item ကိုပါ
     * မပြပါ (grid သာပြမည်)။
     */
    @Override
    public int getItemCount() {
        return sections.isEmpty() ? 0 : 1;
    }

    @Override
    public int getItemViewType(int position) {
        return 1001;
    }

    public void setSections(
            List<HomeSection> newSections
    ) {
        boolean hadItem = getItemCount() > 0;

        sections =
                newSections != null
                        ? new ArrayList<>(newSections)
                        : Collections.emptyList();

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
        LinearLayout container =
                new LinearLayout(parent.getContext());

        container.setLayoutParams(
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        return new Holder(container, listener);
    }

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position
    ) {
        holder.bind(sections);
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        private final LinearLayout container;
        private final Listener listener;

        /*
         * Section id -> ပြန်သုံးနိုင်သော section view။
         * Rebind တွင် adapter အသစ်မဆောက်ဘဲ item list
         * သာအသစ်ပေးသောကြောင့် row တစ်ခုချင်းစီရဲ့
         * scroll position မပျက်ပါ။
         */
        private final Map<String, SectionView> sectionViews =
                new LinkedHashMap<>();

        Holder(
                @NonNull View itemView,
                Listener listener
        ) {
            super(itemView);

            this.container = (LinearLayout) itemView;
            this.listener = listener;
        }

        void bind(List<HomeSection> sections) {
            /*
             * မလိုတော့သော section view များကို
             * ဖြုတ်မည်။
             */
            List<String> wantedIds = new ArrayList<>();

            for (HomeSection section : sections) {
                wantedIds.add(section.id);
            }

            List<String> toRemove = new ArrayList<>();

            for (String id : sectionViews.keySet()) {
                if (!wantedIds.contains(id)) {
                    toRemove.add(id);
                }
            }

            for (String id : toRemove) {
                SectionView old =
                        sectionViews.remove(id);

                if (old != null) {
                    container.removeView(old.root);
                }
            }

            /*
             * Section အစဉ်လိုက် view များ ရှိနေအောင်
             * စီမည် (ရှိပြီးသားကို ပြန်သုံးမည်)။
             */
            for (
                    int index = 0;
                    index < sections.size();
                    index++
            ) {
                HomeSection section =
                        sections.get(index);

                SectionView sectionView =
                        sectionViews.get(section.id);

                if (sectionView == null) {
                    sectionView =
                            new SectionView(
                                    container,
                                    listener
                            );

                    sectionViews.put(
                            section.id,
                            sectionView
                    );
                }

                sectionView.bind(section);

                View root = sectionView.root;

                int currentIndex =
                        container.indexOfChild(root);

                if (currentIndex != index) {
                    container.removeView(root);
                    container.addView(root, index);
                }
            }
        }
    }

    /*
     * Section တစ်ခုစာအတွက် ပြန်သုံးနိုင်သော view set:
     * title + "More >" + horizontal RecyclerView။
     */
    static class SectionView {
        final LinearLayout root;

        private final Listener listener;
        private final TextView titleView;
        private final TextView moreView;
        private final RecyclerView row;
        private final TitleAdapter rowAdapter;

        SectionView(
                ViewGroup parent,
                Listener listener
        ) {
            this.listener = listener;

            root =
                    (LinearLayout)
                            LayoutInflater
                                    .from(parent.getContext())
                                    .inflate(
                                            R.layout.home_section,
                                            parent,
                                            false
                                    );

            titleView =
                    root.findViewById(
                            R.id.sectionTitle
                    );

            moreView =
                    root.findViewById(
                            R.id.sectionMore
                    );

            row = root.findViewById(R.id.sectionRow);

            rowAdapter =
                    new TitleAdapter(
                            listener::onItemClick,
                            item -> {
                            },
                            R.layout.item_title_row
                    );

            row.setLayoutManager(
                    new LinearLayoutManager(
                            parent.getContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                    )
            );

            row.setAdapter(rowAdapter);
            row.setNestedScrollingEnabled(false);

            /*
             * wrap_content height ရှိသော horizontal row
             * တွင် setHasFixedSize(true) မခေါ်ရ —
             * lintVitalRelease ကျမည်။
             */

            parent.addView(root);
        }

        void bind(HomeSection section) {
            titleView.setText(section.title);

            moreView.setOnClickListener(
                    view ->
                            listener.onSectionMore(
                                    section
                            )
            );

            /*
             * Series (Nosub Eng) section က cover ပုံပဲရှိသဖြင့်
             * landscape cover layout သုံးမည်။
             */
            rowAdapter.setLandscapeMode(
                    "series".equals(section.id)
            );

            rowAdapter.submitList(section.items);
            rowAdapter.refreshProgressSnapshot();
        }
    }
}