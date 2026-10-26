package com.cosmicchimps.meetingsummary.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmicchimps.meetingsummary.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Adapter genérico de lista con título, subtítulo y click. */
public class SimpleAdapter<T> extends RecyclerView.Adapter<SimpleAdapter.VH> {

    public interface OnClick<T> { void click(T item); }

    private final List<T> items = new ArrayList<>();
    private final Function<T, String> titleOf;
    private final Function<T, String> subtitleOf;
    private final OnClick<T> onClick;

    public SimpleAdapter(Function<T, String> titleOf, Function<T, String> subtitleOf, OnClick<T> onClick) {
        this.titleOf = titleOf;
        this.subtitleOf = subtitleOf;
        this.onClick = onClick;
    }

    public void setItems(List<T> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Títulos actualmente mostrados (útil para validar duplicados). */
    public List<String> titles() {
        List<String> out = new ArrayList<>(items.size());
        for (T it : items) out.add(titleOf.apply(it));
        return out;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_simple, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        T item = items.get(pos);
        h.title.setText(titleOf.apply(item));
        String sub = subtitleOf != null ? subtitleOf.apply(item) : null;
        h.subtitle.setText(sub != null ? sub : "");
        h.subtitle.setVisibility(sub != null && !sub.isEmpty() ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> onClick.click(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
        }
    }
}
