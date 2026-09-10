package com.cmflix.nativeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TitleAdapter extends RecyclerView.Adapter<TitleAdapter.Holder> {
    public interface Listener {
        void onClick(JSONObject item);
    }

    private final List<JSONObject> items = new ArrayList<>();
    private final Listener listener;

    public TitleAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<JSONObject> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_title, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        JSONObject item = items.get(position);

        holder.title.setText(item.optString("title", ""));
        String year = item.optString("year", "");
        String rating = item.optString("rating", "");
        holder.meta.setText(
                (year.isEmpty() ? "" : year) +
                (year.isEmpty() || rating.isEmpty() ? "" : "  •  ") +
                (rating.isEmpty() ? "" : "★ " + rating)
        );

        Glide.with(holder.poster.getContext())
                .load(item.optString("poster_url", ""))
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.poster);

        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title;
        TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.poster);
            title = itemView.findViewById(R.id.movieTitle);
            meta = itemView.findViewById(R.id.meta);
        }
    }
}
