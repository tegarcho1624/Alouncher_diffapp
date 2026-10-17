package com.alouncher.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.core.graphics.ColorUtils;

/**
 * Color de acento del launcher (Fase 7B-1).
 *  - Se guarda como entero ARGB en SharedPreferences ("settings", clave "accent"), junto a la fuente.
 *  - Sin color guardado: el dorado de siempre (colors.xml: gold / gold_hi).
 *  - version() sube cada vez que cambia; las pantallas la comparan para volver a aplicarlo (igual que FontManager).
 * En esta fase solo la Home usa estos valores; el resto del launcher sigue leyendo colors.xml.
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

    private static int version;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Color de acento actual (opaco). Si no hay uno guardado: el dorado. */
    static int accent(Context c) {
        return prefs(c).getInt(KEY, DEFAULT_ACCENT) | 0xFF000000;
    }

    /** Versión más clara del acento (equivale a gold_hi). El dorado devuelve exactamente gold_hi. */
    static int accentHi(Context c) {
        int a = accent(c);
        if (a == DEFAULT_ACCENT) return DEFAULT_ACCENT_HI;
        return ColorUtils.blendARGB(a, Color.WHITE, 0.22f);
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
