package com.alouncher.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

    interface Listener {
        void onOpen(AppEntry app);
        void onMenu(View anchor, AppEntry app);
        void onDragStart(View anchor, AppEntry app);
        void onHiddenApps();
    }

    private static final int TYPE_APP = 0;
    private static final int TYPE_HIDDEN = 1;

    private final List<AppEntry> all = new ArrayList<>();   // todas las apps visibles (las ocultas nunca están aquí)
    private final List<AppEntry> items = new ArrayList<>();  // las que se muestran: todas, o las que coinciden con la búsqueda
    private String query = "";                               // búsqueda actual, ya en minúsculas y sin espacios en los extremos
    private boolean showHiddenCell;
    private final Listener listener;
    private LongPressDrag drag;

    AppAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<AppEntry> apps, boolean showHiddenCell) {
        all.clear();
        all.addAll(apps);
        this.showHiddenCell = showHiddenCell;
        applyFilter();            // si hay una búsqueda en curso, se mantiene
        notifyDataSetChanged();
    }

    /** Filtra por texto ("contiene", sin distinguir mayúsculas). Vacío = todas las apps. */
    void setQuery(String q) {
        String n = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        if (n.equals(query)) return;
        query = n;
        applyFilter();
        notifyDataSetChanged();
    }

    boolean isFiltering() {
        return !query.isEmpty();
    }

    /** Cuántas apps se muestran ahora (sin contar la celda "Apps ocultas"). */
    int resultCount() {
        return items.size();
    }

    /** Coincide si lo buscado está en el nombre original O en el nombre personalizado (si lo hay). */
    private void applyFilter() {
        items.clear();
        if (query.isEmpty()) {
            items.addAll(all);
            return;
        }
        for (AppEntry a : all) {
            boolean hit = a.originalLabel.toLowerCase(Locale.getDefault()).contains(query)
                    || (a.customLabel != null && a.customLabel.toLowerCase(Locale.getDefault()).contains(query));
            if (hit) items.add(a);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_APP : TYPE_HIDDEN;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        if (viewType == TYPE_APP) {
            if (drag == null) {
                drag = new LongPressDrag(parent.getContext(), new LongPressDrag.Listener() {
                    @Override public void onLongPress(View view) {
                        Object t = view.getTag();
                        if (t instanceof AppEntry) listener.onMenu(view, (AppEntry) t);
                    }
                    @Override public void onDragStart(View view) {
                        Object t = view.getTag();
                        if (t instanceof AppEntry) listener.onDragStart(view, (AppEntry) t);
                    }
                });
            }
            drag.attach(v); // pulsación larga = menú; seguir y mover = arrastrar
        }
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (position >= items.size()) {
            // Celda especial al final del grid
            h.itemView.setTag(null);
            h.icon.setImageResource(R.drawable.ic_hidden_cell);
            h.label.setText(R.string.apps_ocultas);
            FontManager.applyTo(h.label);
            h.itemView.setOnClickListener(v -> listener.onHiddenApps());
            return;
        }
        AppEntry app = items.get(position);
        h.itemView.setTag(app);
        h.icon.setImageDrawable(app.newIcon(h.itemView.getResources()));
        h.label.setText(app.label());
        FontManager.applyTo(h.label);
        h.itemView.setOnClickListener(v -> listener.onOpen(app));
    }

    @Override
    public int getItemCount() {
        // La celda "Apps ocultas" solo se ve sin búsqueda: no se mezcla con los resultados
        return items.size() + (showHiddenCell && query.isEmpty() ? 1 : 0);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
        }
    }
}
