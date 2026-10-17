package com.alouncher.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Color de acento del launcher (Fases 7B-1 y 7B-2).
 *  - Se guarda como entero ARGB en SharedPreferences ("settings", clave "accent"), junto a la fuente.
 *  - Sin color guardado: el dorado de siempre (colors.xml: gold / gold_hi).
 *  - version() sube cada vez que cambia; MainActivity la compara en onResume y se recrea para tomar el tema nuevo.
 *  - Código Java: accent() / accentHi() / accentBg() / dropHl() / dropHlLine() (equivalen a gold, gold_hi, gold_bg, drop_hl, drop_hl_line).
 *  - Drawables XML: leen ?attr/alAccent... y applyTo() superpone a cada Activity el estilo AccentOverlay del color elegido.
 */
final class ThemeUtils {
    private ThemeUtils() {}

    private static final String PREFS = "settings";
    private static final String KEY = "accent";

    /** Mismos valores que gold y gold_hi de colors.xml. */
    static final int DEFAULT_ACCENT = 0xFFC9A24B;
    static final int DEFAULT_ACCENT_HI = 0xFFE2BD67;

    /** Paleta del selector (el primero es el predeterminado). */
    static final int[] ACCENT_COLORS = {
            0xFFC9A24B, // Dorado
            0xFFE0C23C, // Amarillo
            0xFFE08A3C, // Naranja
            0xFFD6564F, // Rojo
            0xFFD46A9E, // Rosa
            0xFFA17BD6, // Morado
            0xFF5B92DB, // Azul
            0xFF45B5CC, // Cian
            0xFF3FB59B, // Turquesa
            0xFF6FBA64, // Verde
            0xFFA8C44F, // Lima
            0xFFB9B3A6  // Plata
    };

    /** accentHi de cada color de la paleta (mismo orden); es lo que usan también los estilos AccentOverlay. */
    private static final int[] ACCENT_HI = {
            0xFFE2BD67,
            0xFFE6CF66,
            0xFFE6A366,
            0xFFDF7B75,
            0xFFDD8AB3,
            0xFFB598DF,
            0xFF7FA9E2,
            0xFF6DC5D7,
            0xFF69C5B1,
            0xFF8EC986,
            0xFFBBD075,
            0xFFC8C3B9
    };

    private static final int[] OVERLAYS = {
            R.style.AccentOverlay0, R.style.AccentOverlay1, R.style.AccentOverlay2, R.style.AccentOverlay3,
            R.style.AccentOverlay4, R.style.AccentOverlay5, R.style.AccentOverlay6, R.style.AccentOverlay7,
            R.style.AccentOverlay8, R.style.AccentOverlay9, R.style.AccentOverlay10, R.style.AccentOverlay11
    };

    private static int version;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int indexOf(int color) {
        for (int i = 0; i < ACCENT_COLORS.length; i++) {
            if (ACCENT_COLORS[i] == color) return i;
        }
        return -1;
    }

    /** Color de acento actual (opaco). Si no hay uno guardado (o no es de la paleta): el dorado. */
    static int accent(Context c) {
        int a = prefs(c).getInt(KEY, DEFAULT_ACCENT) | 0xFF000000;
        return indexOf(a) < 0 ? DEFAULT_ACCENT : a;
    }

    /** Versión más clara del acento (equivale a gold_hi). El dorado devuelve exactamente gold_hi. */
    static int accentHi(Context c) {
        int i = indexOf(accent(c));
        return i < 0 ? DEFAULT_ACCENT_HI : ACCENT_HI[i];
    }

    /** Equivale a gold_bg (acento muy tenue: resaltados de texto). */
    static int accentBg(Context c) {
        return withAlpha(accent(c), 0x1F);
    }

    /** Equivalen a drop_hl y drop_hl_line (relleno y línea de las zonas donde se suelta algo). */
    static int dropHl(Context c) {
        return withAlpha(accent(c), 0x33);
    }

    static int dropHlLine(Context c) {
        return withAlpha(accent(c), 0x99);
    }

    /**
     * Superpone el acento elegido al tema de la Activity (hay que llamarlo en onCreate, antes de crear vistas).
     * Los drawables de res/drawable leen ?attr/alAccent... y así toman el color sin cambiar sus usos.
     */
    static void applyTo(Activity a) {
        int i = indexOf(accent(a));
        a.getTheme().applyStyle(OVERLAYS[i < 0 ? 0 : i], true);
    }

    static synchronized void setAccent(Context c, int color) {
        color |= 0xFF000000;
        if (accent(c) == color) return;
        prefs(c).edit().putInt(KEY, color).apply();
        version++;
    }

    /** Sube cada vez que cambia el acento. */
    static synchronized int version() {
        return version;
    }

    static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Fondo de las celdas de la Home y del dock: transparente, y resaltado con el acento mientras
     * se arrastra algo encima (estado "activated"). Equivale a res/drawable/cell_bg.xml pero con el color elegido.
     */
    static Drawable cellBg(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        int accent = accent(c);

        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.RECTANGLE);
        on.setColor(withAlpha(accent, 0x33));
        on.setStroke(Math.round(d), withAlpha(accent, 0x99));
        on.setCornerRadius(18f * d);

        GradientDrawable off = new GradientDrawable();
        off.setShape(GradientDrawable.RECTANGLE);
        off.setColor(Color.TRANSPARENT);

        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_activated}, on);
        sl.addState(new int[]{}, off);
        return sl;
    }
}
