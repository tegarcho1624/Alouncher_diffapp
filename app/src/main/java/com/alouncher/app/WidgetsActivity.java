package com.alouncher.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fase 6A: panel de Widgets (solo informativo; colocar en la Home llega en la 6B).
 *  - Izquierda (~30 %): buscador y lista de apps que tienen widgets, con su cantidad.
 *  - Derecha (~70 %): vista previa del widget elegido (carrusel si la app tiene varios), nombre y tamaño en celdas.
 * Los datos salen de AppWidgetManager.getInstalledProviders(); no hay AppWidgetHost ni binding.
 */
public class WidgetsActivity extends Activity {

    /** Resultado para la Home: ComponentName.flattenToString() del widget elegido (mantener presionado). */
    static final String EXTRA_PROVIDER = "provider";
    private static final String STATE_PKG = "selectedPkg";
    private static final String STATE_QUERY = "query";

    /** Un widget (provider) con su tamaño ya convertido a celdas. */
    private static final class Item {
        final AppWidgetProviderInfo info;
        final String label;
        final int cellsW;
        final int cellsH;

        Item(AppWidgetProviderInfo info, String label, int cellsW, int cellsH) {
            this.info = info;
            this.label = label;
            this.cellsW = cellsW;
            this.cellsH = cellsH;
        }
    }

    /** Una app con sus widgets. */
    private static final class Group {
        final String pkg;
        final String label;
        final String lower;
        final Drawable icon;
        final List<Item> items = new ArrayList<>();

        Group(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.lower = label.toLowerCase(Locale.getDefault());
            this.icon = icon;
        }
    }

    private static final class GroupVH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView count;

        GroupVH(LinearLayout row, ImageView icon, TextView name, TextView count) {
            super(row);
            this.icon = icon;
            this.name = name;
            this.count = count;
        }
    }

    private static final class PageVH extends RecyclerView.ViewHolder {
        final FrameLayout box;
        final ImageView preview;
        final LinearLayout placeholder;
        final ImageView placeholderIcon;
        final TextView name;

        PageVH(LinearLayout page, FrameLayout box, ImageView preview, LinearLayout placeholder,
               ImageView placeholderIcon, TextView name) {
            super(page);
            this.box = box;
            this.preview = preview;
            this.placeholder = placeholder;
            this.placeholderIcon = placeholderIcon;
            this.name = name;
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final List<Group> all = new ArrayList<>();       // todas las apps con widgets
    private final List<Group> filtered = new ArrayList<>();  // las que coinciden con la búsqueda
    private Group selected;
    private String restoredPkg;
    private boolean loaded;

    private LinearLayout root;
    private EditText search;
    private RecyclerView list;
    private TextView leftEmpty;
    private RecyclerView pager;
    private PageIndicator indicator;
    private GroupAdapter groupAdapter;
    private PageAdapter pageAdapter;
    private int pagerW, pagerH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.applyTo(this); // 7B-2: color de acento elegido
        FontManager.init(this);
        String restoredQuery = "";
        if (savedInstanceState != null) {
            restoredPkg = savedInstanceState.getString(STATE_PKG);
            restoredQuery = savedInstanceState.getString(STATE_QUERY, "");
        }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        // ---- Encabezado: título y subtítulo centrados, "Listo" a la derecha
        FrameLayout header = new FrameLayout(this);
        header.setPadding(dp(16), dp(12), dp(16), dp(8));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this);
        title.setText(R.string.widgets);
        title.setTextColor(getColor(R.color.ink));
        title.setTextSize(22);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        titles.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.widgets_subtitulo);
        subtitle.setTextColor(getColor(R.color.mut));
        subtitle.setTextSize(12);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(4);
        titles.addView(subtitle, sp);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tp.leftMargin = dp(120);   // deja libre el espacio del botón "Listo"
        tp.rightMargin = dp(120);
        header.addView(titles, tp);

        TextView done = BottomSheet.button(this, getString(R.string.listo), false);
        done.setPadding(dp(24), 0, dp(24), 0);
        done.setOnClickListener(v -> finish());
        header.addView(done, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Cuerpo: izquierda 30 % / derecha 70 %
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(16));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Columna izquierda
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f);
        lp.rightMargin = dp(16);
        body.addView(left, lp);

        search = new EditText(this);
        search.setHint(R.string.buscar);
        search.setHintTextColor(getColor(R.color.mut));
        search.setTextColor(getColor(R.color.ink));
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setHighlightColor(ThemeUtils.accentBg(this));
        search.setBackgroundResource(R.drawable.input_bg);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setMinHeight(dp(44));
        search.setPadding(dp(14), dp(8), dp(14), dp(8));
        Drawable lupa = getDrawable(R.drawable.ic_search).mutate();
        lupa.setTint(getColor(R.color.mut));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(lupa, null, null, null);
        search.setCompoundDrawablePadding(dp(10));
        left.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setPadding(0, dp(10), 0, dp(8));
        groupAdapter = new GroupAdapter();
        list.setAdapter(groupAdapter);
        left.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        leftEmpty = new TextView(this);
        leftEmpty.setTextColor(getColor(R.color.mut));
        leftEmpty.setTextSize(16);
        leftEmpty.setGravity(Gravity.CENTER);
        leftEmpty.setVisibility(View.GONE);
        left.addView(leftEmpty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Columna derecha
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        body.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 7f));

        pager = new RecyclerView(this);
        pager.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageAdapter = new PageAdapter();
        pager.setAdapter(pageAdapter);
        new PagerSnapHelper().attachToRecyclerView(pager);
        right.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        indicator = new PageIndicator(this, null); // el mismo indicador de la Home, sin casita (main = -1)
        indicator.setPages(1, -1, 0);
        indicator.setOnSelect(i -> pager.smoothScrollToPosition(i));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        ip.topMargin = dp(8);
        right.addView(indicator, ip);

        // El tamaño de la vista previa depende del espacio disponible: si el panel cambia de tamaño, se vuelve a calcular
        pager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            if (w != pagerW || h != pagerH) {
                pagerW = w;
                pagerH = h;
                pager.post(() -> pageAdapter.notifyDataSetChanged());
            }
        });
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) { syncIndicator(); }
            @Override public void onScrollStateChanged(RecyclerView rv, int newState) { syncIndicator(); }
        });

        // Búsqueda en tiempo real
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        if (!restoredQuery.isEmpty()) search.setText(restoredQuery);

        FontManager.apply(root);
        setContentView(root);
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selected != null) outState.putString(STATE_PKG, selected.pkg);
        outState.putString(STATE_QUERY, search.getText().toString());
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    // =========================================================================
    //  Datos: AppWidgetManager.getInstalledProviders(), agrupados por paquete
    // =========================================================================

    private void load() {
        final float density = getResources().getDisplayMetrics().density;
        io.execute(() -> {
            List<Group> groups = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                List<AppWidgetProviderInfo> providers =
                        AppWidgetManager.getInstance(this).getInstalledProviders();
                Map<String, Group> byPkg = new LinkedHashMap<>();
                for (AppWidgetProviderInfo p : providers) {
                    try {
                        // Solo los que se pueden poner en la pantalla de inicio (no los exclusivos de pantalla de bloqueo)
                        if ((p.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue;
                        String pkg = p.provider.getPackageName();
                        String label = p.loadLabel(pm);
                        Group g = byPkg.get(pkg);
                        if (g == null) {
                            String appLabel;
                            Drawable appIcon;
                            try {
                                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                                appLabel = ai.loadLabel(pm).toString();
                                appIcon = ai.loadIcon(pm);
                            } catch (PackageManager.NameNotFoundException e) {
                                // App no visible para el launcher (sin ícono de menú): se usa lo que expone el widget
                                appLabel = label != null && !label.trim().isEmpty() ? label : pkg;
                                appIcon = p.loadIcon(this, 0);
                                if (appIcon == null) appIcon = pm.getDefaultActivityIcon();
                            }
                            g = new Group(pkg, appLabel, appIcon);
                            byPkg.put(pkg, g);
                        }
                        if (label == null || label.trim().isEmpty()) label = g.label;
                        g.items.add(new Item(p, label,
                                cellsFor(p, true, density), cellsFor(p, false, density)));
                    } catch (Exception ignored) {
                        // un widget con datos rotos no debe tumbar el panel: se salta
                    }
                }
                groups.addAll(byPkg.values());
            } catch (Exception ignored) {
                // sin lista: se mostrará el mensaje de "no hay widgets"
            }
            Collator col = Collator.getInstance();
            groups.sort((a, b) -> col.compare(a.label, b.label));
            for (Group g : groups) {
                g.items.sort((a, b) -> col.compare(a.label, b.label));
            }
            ui.post(() -> {
                if (isDestroyed()) return;
                all.clear();
                all.addAll(groups);
                loaded = true;
                applyFilter();
            });
        });
    }

    /**
     * Tamaño en celdas con la fórmula estándar de Android: celdas = (dp + 30) / 70.
     * minWidth/minHeight llegan en píxeles, así que primero se pasan a dp. En Android 12+ se usa el tamaño
     * por celdas que declara el propio widget (targetCellWidth/Height) si lo trae.
     */
    private static int cellsFor(AppWidgetProviderInfo p, boolean width, float density) {
        if (Build.VERSION.SDK_INT >= 31) {
            int target = width ? p.targetCellWidth : p.targetCellHeight;
            if (target > 0) return target;
        }
        float dp = (width ? p.minWidth : p.minHeight) / density;
        return Math.max(1, (int) ((dp + 30f) / 70f));
    }

    // =========================================================================
    //  Búsqueda y selección
    // =========================================================================

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        filtered.clear();
        for (Group g : all) {
            if (q.isEmpty() || g.lower.contains(q)) filtered.add(g);
        }
        boolean none = filtered.isEmpty();
        list.setVisibility(none ? View.GONE : View.VISIBLE);
        leftEmpty.setText(all.isEmpty() ? R.string.sin_widgets_instalados : R.string.no_se_encontraron_widgets);
        leftEmpty.setVisibility(loaded && none ? View.VISIBLE : View.GONE);

        // Primera carga: la app que se estaba viendo (si se giró la pantalla) o la primera de la lista.
        // Después, si la app elegida ya no está entre los resultados, pasa a la primera.
        Group keep = selected;
        if (keep == null && restoredPkg != null) {
            for (Group g : filtered) {
                if (g.pkg.equals(restoredPkg)) keep = g;
            }
        }
        if (loaded) restoredPkg = null; // antes de cargar los datos todavía no hay nada que restaurar
        if (keep == null || !filtered.contains(keep)) keep = filtered.isEmpty() ? null : filtered.get(0);

        boolean changed = keep != selected;
        selected = keep;
        groupAdapter.notifyDataSetChanged();
        if (changed || pageAdapter.getItemCount() == 0) showSelected();
    }

    private void select(Group g) {
        hideKeyboard();
        if (g == selected) return;
        selected = g;
        groupAdapter.notifyDataSetChanged();
        showSelected();
    }

    /** Muestra los widgets de la app elegida en la columna derecha. */
    private void showSelected() {
        int n = selected == null ? 0 : selected.items.size();
        pageAdapter.notifyDataSetChanged();
        pager.scrollToPosition(0);
        pager.setVisibility(n == 0 ? View.INVISIBLE : View.VISIBLE);
        indicator.setPages(Math.max(1, n), -1, 0);
        indicator.setVisibility(n > 1 ? View.VISIBLE : View.INVISIBLE);
    }

    private void syncIndicator() {
        LinearLayoutManager lm = (LinearLayoutManager) pager.getLayoutManager();
        if (lm == null) return;
        int first = lm.findFirstVisibleItemPosition();
        if (first < 0) return;
        View v = lm.findViewByPosition(first);
        int pos = first;
        if (v != null && v.getRight() < pager.getWidth() / 2) pos = first + 1;
        indicator.setCurrent(Math.max(0, Math.min(pageAdapter.getItemCount() - 1, pos)));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    // =========================================================================
    //  Lista de apps (izquierda)
    // =========================================================================

    private final class GroupAdapter extends RecyclerView.Adapter<GroupVH> {
        @Override
        public GroupVH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(WidgetsActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundResource(R.drawable.cell_bg); // activado = dorado tenue (la app elegida)
            row.setClickable(true);
            row.setFocusable(true);
            RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(4);
            row.setLayoutParams(rlp);

            ImageView icon = new ImageView(WidgetsActivity.this);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout texts = new LinearLayout(WidgetsActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            xp.leftMargin = dp(12);
            row.addView(texts, xp);

            TextView name = new TextView(WidgetsActivity.this);
            name.setTextColor(getColor(R.color.ink));
            name.setTextSize(16);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView count = new TextView(WidgetsActivity.this);
            count.setTextColor(getColor(R.color.mut));
            count.setTextSize(12);
            count.setSingleLine(true);
            texts.addView(count, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            return new GroupVH(row, icon, name, count);
        }

        @Override
        public void onBindViewHolder(GroupVH h, int position) {
            Group g = filtered.get(position);
            h.icon.setImageDrawable(copyOf(g.icon));
            h.name.setText(g.label);
            int n = g.items.size();
            h.count.setText(getResources().getQuantityString(R.plurals.widgets_cantidad, n, n));
            FontManager.applyTo(h.name);
            FontManager.applyTo(h.count);
            h.itemView.setActivated(g == selected);
            h.itemView.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) select(filtered.get(p));
            });
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }
    }

    // =========================================================================
    //  Vista previa (derecha): una página por widget de la app elegida
    // =========================================================================

    private final class PageAdapter extends RecyclerView.Adapter<PageVH> {
        @Override
        public PageVH onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout page = new LinearLayout(WidgetsActivity.this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER);
            page.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout box = new FrameLayout(WidgetsActivity.this);
            box.setBackgroundResource(R.drawable.input_bg);
            page.addView(box, new LinearLayout.LayoutParams(dp(100), dp(100)));

            ImageView preview = new ImageView(WidgetsActivity.this);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.setPadding(dp(6), dp(6), dp(6), dp(6));
            preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            box.addView(preview, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Sin vista previa: el ícono de la app y un aviso discreto
            LinearLayout ph = new LinearLayout(WidgetsActivity.this);
            ph.setOrientation(LinearLayout.VERTICAL);
            ph.setGravity(Gravity.CENTER);
            ImageView phIcon = new ImageView(WidgetsActivity.this);
            phIcon.setAlpha(0.8f);
            phIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ph.addView(phIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView phText = new TextView(WidgetsActivity.this);
            phText.setText(R.string.sin_vista_previa);
            phText.setTextColor(getColor(R.color.mut));
            phText.setTextSize(12);
            phText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pt = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pt.topMargin = dp(6);
            ph.addView(phText, pt);
            box.addView(ph, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FontManager.applyTo(phText);

            TextView name = new TextView(WidgetsActivity.this);
            name.setTextColor(getColor(R.color.ink));
            name.setTextSize(16);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.topMargin = dp(16);
            page.addView(name, np);

            return new PageVH(page, box, preview, ph, phIcon, name);
        }

        @Override
        public void onBindViewHolder(PageVH h, int position) {
            if (selected == null || position >= selected.items.size()) return;
            Item it = selected.items.get(position);

            // Tamaño real del widget (aspecto respetado); solo se reduce si no cabe en el panel
            float w = it.info.minWidth;
            float hh = it.info.minHeight;
            if (w <= 0 || hh <= 0) {
                w = dp(110);
                hh = dp(110);
            }
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            float availW = (pagerW > 0 ? pagerW : dm.widthPixels * 0.7f) - dp(32);
            float availH = (pagerH > 0 ? pagerH : dm.heightPixels * 0.6f) - dp(32) - dp(56);
            float s = Math.min(1f, Math.min(availW / w, availH / hh));
            if (s <= 0f) s = 1f;
            ViewGroup.LayoutParams lp = h.box.getLayoutParams();
            lp.width = Math.max(1, Math.round(w * s));
            lp.height = Math.max(1, Math.round(hh * s));
            h.box.setLayoutParams(lp);

            Drawable img = null;
            try {
                img = it.info.loadPreviewImage(WidgetsActivity.this, 0);
            } catch (Exception ignored) {
                // vista previa ilegible: se queda el marcador
            }
            if (img != null) {
                h.preview.setImageDrawable(img);
                h.preview.setVisibility(View.VISIBLE);
                h.placeholder.setVisibility(View.GONE);
            } else {
                h.preview.setImageDrawable(null);
                h.preview.setVisibility(View.GONE);
                h.placeholderIcon.setImageDrawable(copyOf(selected.icon));
                h.placeholder.setVisibility(View.VISIBLE);
            }

            h.name.setText(getString(R.string.widget_nombre_tamano, it.label, it.cellsW, it.cellsH));
            FontManager.applyTo(h.name);

            // 6B-1: mantener presionado = devolver este widget a la Home y cerrar el panel
            final AppWidgetProviderInfo chosen = it.info;
            h.box.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                setResult(RESULT_OK, new android.content.Intent()
                        .putExtra(EXTRA_PROVIDER, chosen.provider.flattenToString()));
                finish();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return selected == null ? 0 : selected.items.size();
        }
    }

    /** Un mismo Drawable no debe mostrarse en dos vistas a la vez: cada vista pide su copia. */
    private Drawable copyOf(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable(getResources()).mutate() : d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
