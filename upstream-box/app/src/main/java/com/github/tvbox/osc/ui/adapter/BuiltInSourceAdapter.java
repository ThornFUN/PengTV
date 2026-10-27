package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.BuiltInSourceRepository;

import java.util.List;

public class BuiltInSourceAdapter extends RecyclerView.Adapter<BuiltInSourceAdapter.ViewHolder> {
    public interface OnSourceClickListener {
        void onClick(BuiltInSourceRepository.Source source);
    }

    private final List<BuiltInSourceRepository.Source> sources;
    private final String selectedUrl;
    private final OnSourceClickListener listener;

    public BuiltInSourceAdapter(List<BuiltInSourceRepository.Source> sources, String selectedUrl, OnSourceClickListener listener) {
        this.sources = sources;
        this.selectedUrl = selectedUrl;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_builtin_source, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BuiltInSourceRepository.Source source = sources.get(position);
        boolean selected = source.url.equals(selectedUrl);
        holder.name.setText((selected ? "√ " : "") + source.name);
        holder.url.setText(source.url);
        holder.description.setText(source.description == null ? "" : source.description);
        holder.itemView.setOnClickListener(v -> listener.onClick(source));
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView url;
        final TextView description;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvName);
            url = itemView.findViewById(R.id.tvUrl);
            description = itemView.findViewById(R.id.tvDescription);
        }
    }
}
