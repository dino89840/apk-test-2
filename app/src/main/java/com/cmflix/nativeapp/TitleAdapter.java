package com.cmflix.nativeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

public class TitleAdapter
        extends ListAdapter<JSONObject, TitleAdapter.Holder> {

    public interface Listener {
        void onClick(JSONObject item);
    }

    private final Listener listener;

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
                                    oldItem.optString("slug", "")
                            );

                    String newId =
                            newItem.optString(
                                    "id",
                                    newItem.optString("slug", "")
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

    public TitleAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;

        setStateRestorationPolicy(
                StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );
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
                                R.layout.item_title,
                                parent,
                                false
                        );

        return new Holder(view);
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

        String year =
                item.optString("year", "");

        String rating =
                item.optString("rating", "");

        StringBuilder metadata =
                new StringBuilder();

        if (!year.isEmpty()) {
            metadata.append(year);
        }

        if (!rating.isEmpty()) {
            if (metadata.length() > 0) {
                metadata.append("  •  ");
            }

            metadata.append("★ ").append(rating);
        }

        holder.meta.setText(metadata.toString());

        Glide.with(holder.poster)
                .load(item.optString("poster_url", ""))
                .centerCrop()
                .thumbnail(0.25f)
                .dontAnimate()
                .placeholder(
                        android.R.drawable.ic_menu_report_image
                )
                .error(
                        android.R.drawable.ic_menu_report_image
                )
                .into(holder.poster);

        holder.itemView.setOnClickListener(
                view -> listener.onClick(item)
        );
    }

    @Override
    public void onViewRecycled(
            @NonNull Holder holder
    ) {
        Glide.with(holder.poster)
                .clear(holder.poster);

        super.onViewRecycled(holder);
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        final ImageView poster;
        final TextView title;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);

            poster = itemView.findViewById(
                    R.id.poster
            );

            title = itemView.findViewById(
                    R.id.movieTitle
            );

            meta = itemView.findViewById(
                    R.id.meta
            );
        }
    }
}
