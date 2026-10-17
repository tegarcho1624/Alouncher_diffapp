package com.alouncher.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Configuración de pantalla principal: Estilo de Escritura (fuentes), disposición (próximamente) y Color de acento (7B-1, colores personalizados 7C-1). */
public class HomeSettingsActivity extends Activity {

    private LinearLayout root;
    private GradientDrawable accentDot; // círculo del color actual en la fila "Color de acento"
    // Hoja del selector (7C-1): partes que se actualizan al añadir o eliminar colores personalizados
    private LinearLayout customGrid;
    private TextView customHint;
    private TextView hexError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.applyTo(this); // 7B-2: color de acento elegido
        FontManager.init(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        // Encabezado: flecha de regreso + título
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackgroundResource(R.drawable.item_bg);
        back.setContentDescription(getString(R.string.atras));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(this);
        title.setText(R.string.config_home);
        title.setTextColor(getColor(R.color.ink));
        title.setTextSize(22);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(12);
        header.addView(title, tp);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(row(getString(R.string.estilo_escritura), getString(R.string.estilos),
                v -> startActivity(new Intent(this, FontsActivity.class))));
        root.addView(divider());
        root.addView(row(getString(R.string.disposicion),
                HomeStore.HOME_COLS + "\u00d7" + HomeStore.HOME_ROWS,
                v -> Toast.makeText(this, R.string.proximamente, Toast.LENGTH_SHORT).show()));
        root.addView(divider());
        root.addView(accentRow());
        root.addView(divider());

        setContentView(root);
        FontManager.apply(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FontManager.apply(root); // por si se cambió la fuente en la pantalla de Estilos
    }

    /** Fila "Color de acento": en lugar de texto lleva un círculo con el color actual. Tocar la fila abre el selector. */
    private View accentRow() {
        LinearLayout r = (LinearLayout) row(getString(R.string.color_acento), "", v -> showAccentSheet());
        r.removeViewAt(1); // el texto del valor: aquí va el círculo

        accentDot = new GradientDrawable();
        accentDot.setShape(GradientDrawable.OVAL);
        accentDot.setStroke(dp(1), getColor(R.color.ln2));
        accentDot.setColor(ThemeUtils.accent(this));
        View dot = new View(this);
        dot.setBackground(accentDot);
        r.addView(dot, 1, new LinearLayout.LayoutParams(dp(28), dp(28)));
        return r;
    }

    /**
     * Selector: los 12 básicos y, debajo, "Añadir color" (código HEX) con los colores personalizados (7C-1).
     * Al elegir uno (básico o personalizado) se guarda, se cierra la hoja y el círculo de la fila se actualiza.
     * El cuerpo de la hoja ya es desplazable (BottomSheet), así que con muchos colores personalizados no se corta.
     */
    private void showAccentSheet() {
        BottomSheet s = new BottomSheet(this, getString(R.string.elegir_color), null);
        s.resizeForKeyboard(); // el teclado no tapa el campo HEX, pero no se abre solo

        // ---- 12 básicos (igual que antes)
        LinearLayout basic = new LinearLayout(this);
        basic.setOrientation(LinearLayout.VERTICAL);
        fillSwatches(basic, ThemeUtils.ACCENT_COLORS, s, false);
        s.body().addView(basic, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Separador y "Añadir color"
        View sep = new View(this);
        sep.setBackgroundColor(getColor(R.color.ln));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        sp.topMargin = dp(20);
        s.body().addView(sep, sp);

        TextView label = new TextView(this);
        label.setText(R.string.anadir_color);
        label.setTextColor(getColor(R.color.ink));
        label.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        s.body().addView(label, lp);

        EditText et = new EditText(this);
        et.setHint(R.string.hex_placeholder);
        et.setHintTextColor(getColor(R.color.mut));
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(7)});
        et.setTextColor(getColor(R.color.ink));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setHighlightColor(ThemeUtils.accentBg(this));
        et.setBackgroundResource(R.drawable.input_bg);
        et.setMinHeight(dp(44));
        et.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView add = BottomSheet.button(this, getString(R.string.agregar), true);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = dp(10);
        inputRow.addView(add, ap);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(10);
        s.body().addView(inputRow, ip);

        // Mensaje de error (texto rojo); se esconde al volver a escribir
        hexError = new TextView(this);
        hexError.setTextColor(getColor(R.color.danger));
        hexError.setTextSize(13);
        hexError.setVisibility(View.GONE);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(8);
        s.body().addView(hexError, ep);

        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence t, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { hexError.setVisibility(View.GONE); }
        });

        Runnable addColor = () -> {
            Integer color = ThemeUtils.parseHex(et.getText().toString());
            if (color == null) {
                showHexError(R.string.hex_invalido);
                return;
            }
            int r = ThemeUtils.addCustom(this, color);
            if (r == ThemeUtils.ADD_BASIC) {
                showHexError(R.string.color_ya_basico);
            } else if (r == ThemeUtils.ADD_DUPLICATE) {
                showHexError(R.string.color_ya_guardado);
            } else {
                et.setText("");
                hexError.setVisibility(View.GONE);
                refreshCustom(s);
            }
        };
        add.setOnClickListener(v -> addColor.run());
        et.setOnEditorActionListener((v, actionId, ev) -> {
            addColor.run();
            return true;
        });

        // ---- Colores personalizados (debajo del campo)
        customHint = new TextView(this);
        customHint.setTextColor(getColor(R.color.mut));
        customHint.setTextSize(13);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(16);
        s.body().addView(customHint, hp);

        customGrid = new LinearLayout(this);
        customGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(12);
        s.body().addView(customGrid, gp);
        refreshCustom(s);

        TextView cancel = BottomSheet.button(this, getString(R.string.cancelar), false);
        cancel.setOnClickListener(v -> s.dismiss());
        s.footer().addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        s.show();
    }

    private void showHexError(int res) {
        hexError.setText(res);
        hexError.setVisibility(View.VISIBLE);
    }

    /** Vuelve a dibujar la lista de colores personalizados y su texto de ayuda. */
    private void refreshCustom(BottomSheet s) {
        int[] list = ThemeUtils.customColors(this);
        customHint.setText(list.length == 0 ? R.string.sin_colores_personalizados : R.string.mantener_eliminar_color);
        customGrid.removeAllViews();
        fillSwatches(customGrid, list, s, true);
    }

    /**
     * Dibuja círculos de color en filas de 4 (mismo diseño para básicos y personalizados).
     * Tocar = aplicar. Mantener presionado = eliminar, solo en los personalizados.
     */
    private void fillSwatches(LinearLayout parent, int[] colors, BottomSheet s, boolean custom) {
        final int current = ThemeUtils.accent(this);
        final int cols = 4;
        LinearLayout line = null;
        for (int i = 0; i < colors.length; i++) {
            if (i % cols == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rp.topMargin = dp(14);
                parent.addView(line, rp);
            }
            final int color = colors[i];
            boolean isCurrent = color == current;

            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            d.setStroke(dp(isCurrent ? 3 : 1), getColor(isCurrent ? R.color.ink : R.color.ln2));

            View swatch = new View(this);
            swatch.setBackground(d);
            swatch.setClickable(true);
            swatch.setFocusable(true);
            swatch.setOnClickListener(v -> chooseAccent(s, color));
            if (custom) {
                swatch.setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    confirmDeleteCustom(s, color);
                    return true;
                });
            }
            FrameLayout box = new FrameLayout(this);
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(48), dp(48));
            sp.gravity = Gravity.CENTER;
            box.addView(swatch, sp);
            line.addView(box, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        // La última fila se completa con huecos para que los círculos queden alineados con la cuadrícula
        if (line != null) {
            for (int k = colors.length % cols; k != 0 && k < cols; k++) {
                line.addView(new View(this), new LinearLayout.LayoutParams(0, dp(48), 1f));
            }
        }
    }

    /** Aplica el color elegido (igual para básicos y personalizados): guarda, cierra la hoja y recrea la pantalla. */
    private void chooseAccent(BottomSheet s, int color) {
        s.dismiss();
        int before = ThemeUtils.version();
        ThemeUtils.setAccent(this, color);
        accentDot.setColor(ThemeUtils.accent(this));
        if (ThemeUtils.version() != before) recreate(); // esta pantalla también toma el color nuevo
    }

    /** "¿Deseas eliminar este color?" Sí (rojo) / No. Si era el color aplicado, el launcher vuelve al dorado. */
    private void confirmDeleteCustom(BottomSheet parent, int color) {
        BottomSheet c = new BottomSheet(this, getString(R.string.confirmar_eliminar_color), null);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(1), getColor(R.color.ln2));
        View dot = new View(this);
        dot.setBackground(d);
        preview.addView(dot, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView hex = new TextView(this);
        hex.setText(String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF));
        hex.setTextColor(getColor(R.color.mut));
        hex.setTextSize(15);
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xp.leftMargin = dp(12);
        preview.addView(hex, xp);
        c.body().addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView yes = BottomSheet.button(this, getString(R.string.si), false);
        yes.setTextColor(getColor(R.color.danger));
        TextView no = BottomSheet.button(this, getString(R.string.no), true);
        yes.setOnClickListener(v -> {
            c.dismiss();
            if (ThemeUtils.accent(this) == color) {
                // Era el acento aplicado: primero vuelve al dorado (sube la versión), luego se borra
                parent.dismiss();
                ThemeUtils.setAccent(this, ThemeUtils.DEFAULT_ACCENT);
                ThemeUtils.removeCustom(this, color);
                accentDot.setColor(ThemeUtils.accent(this));
                recreate();
            } else {
                ThemeUtils.removeCustom(this, color);
                refreshCustom(parent);
            }
        });
        no.setOnClickListener(v -> c.dismiss());
        buttons.addView(yes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        np.leftMargin = dp(10);
        buttons.addView(no, np);
        c.footer().addView(buttons);
        c.show();
    }

    private View row(String label, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(28), 0, dp(24), 0);
        row.setBackgroundResource(R.drawable.item_bg);
        row.setOnClickListener(click);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getColor(R.color.ink));
        l.setTextSize(17);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(getColor(R.color.mut));
        v.setTextSize(15);
        row.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(20), dp(20));
        cp.leftMargin = dp(8);
        row.addView(chevron, cp);
        return row;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(getColor(R.color.ln));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(28), 0, dp(24), 0);
        d.setLayoutParams(lp);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
