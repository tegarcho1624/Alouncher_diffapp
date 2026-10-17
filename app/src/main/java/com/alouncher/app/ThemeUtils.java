package com.alouncher.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Color de acento del launcher (Fases 7B-1 y 7B-2).
 *  - Se guarda como entero ARGB en SharedPreferences ("settings", clave "accent"), junto a la fuente.
 *  - Sin color guardado: el dorado de siempre (colors.xml: gold / gold_hi).
 *  - version() sube cada vez que cambia; MainActivity la compara en onResume y se recrea para tomar el tema nuevo.
 *  - Código Java: accent() / accentHi() / accentBg() / dropHl() / dropHlLine() (equivalen a gold, gold_hi, gold_bg, drop_hl, drop_hl_line).
 *  - Drawables XML: leen ?attr/alAccent... y applyTo() superpone a cada Activity el estilo AccentOverlay del color elegido.
 *  - Fase 7C-1: colores personalizados (HEX). Se guardan en "settings" / "accent_custom" como texto "RRGGBB,RRGGBB,..." (el más nuevo primero).
 *    En el código Java se usan EXACTOS; los drawables XML solo pueden usar uno de los 12 estilos AccentOverlay, así que con un
 *    color personalizado toman el básico más cercano (nearestIndex). Eso se corrige en la 7C-2.
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

    // ---- Colores personalizados (7C-1) ----
    private static final String KEY_CUSTOM = "accent_custom";
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    static final int ADD_OK = 0;
    static final int ADD_BASIC = 1;      // ya está entre los 12 básicos
    static final int ADD_DUPLICATE = 2;  // ya estaba guardado
    private static int[] customCache;    // null = aún no leído de SharedPreferences

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int indexOf(int color) {
        for (int i = 0; i < ACCENT_COLORS.length; i++) {
            if (ACCENT_COLORS[i] == color) return i;
        }
        return -1;
    }

    /** Color de acento actual (opaco). Si no hay uno guardado (o no es de la paleta ni personalizado): el dorado. */
    static int accent(Context c) {
        int a = prefs(c).getInt(KEY, DEFAULT_ACCENT) | 0xFF000000;
        return (indexOf(a) >= 0 || isCustom(c, a)) ? a : DEFAULT_ACCENT;
    }

    /** Versión más clara del acento (equivale a gold_hi). Los básicos usan su valor fijo; los personalizados, el acento mezclado un 22 % con blanco. */
    static int accentHi(Context c) {
        int a = accent(c);
        int i = indexOf(a);
        if (i >= 0) return ACCENT_HI[i];
        return mixWhite(a, 0.22f);
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
        int color = accent(a);
        int i = indexOf(color);
        if (i < 0) i = nearestIndex(color); // 7C-1: color personalizado -> el estilo básico más parecido (solo para los drawables XML)
        a.getTheme().applyStyle(OVERLAYS[i], true);
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

    // ---- Colores personalizados (7C-1) ----

    /** Lee un código HEX escrito por el usuario ("#FF5733" o "FF5733"). Devuelve el color opaco, o null si no es válido (exactamente 6 dígitos 0-9 / A-F). */
    static Integer parseHex(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (!t.startsWith("#")) t = "#" + t;
        if (!HEX.matcher(t).matches()) return null;
        try {
            return Color.parseColor(t) | 0xFF000000;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Colores personalizados guardados (el más nuevo primero). Copia: se puede recorrer sin riesgo. */
    static int[] customColors(Context c) {
        return loadCustom(c).clone();
    }

    private static synchronized int[] loadCustom(Context c) {
        if (customCache == null) {
            String raw = prefs(c).getString(KEY_CUSTOM, "");
            int[] tmp = new int[raw.length() / 6 + 1];
            int n = 0;
            for (String part : raw.split(",")) {
                part = part.trim();
                if (part.length() != 6) continue;
                try {
                    int v = (int) Long.parseLong(part, 16) | 0xFF000000;
                    if (indexOf(v) >= 0 || contains(tmp, n, v)) continue;
                    tmp[n++] = v;
                } catch (NumberFormatException ignored) {
                }
            }
            customCache = java.util.Arrays.copyOf(tmp, n);
        }
        return customCache;
    }

    private static boolean contains(int[] arr, int len, int v) {
        for (int i = 0; i < len; i++) {
            if (arr[i] == v) return true;
        }
        return false;
    }

    private static boolean isCustom(Context c, int color) {
        int[] list = loadCustom(c);
        return contains(list, list.length, color);
    }

    private static void saveCustom(Context c, int[] list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%06X", list[i] & 0xFFFFFF));
        }
        prefs(c).edit().putString(KEY_CUSTOM, sb.toString()).apply();
        customCache = list;
    }

    /** Añade un color personalizado (al principio de la lista). Devuelve ADD_OK, ADD_BASIC o ADD_DUPLICATE. */
    static synchronized int addCustom(Context c, int color) {
        color |= 0xFF000000;
        if (indexOf(color) >= 0) return ADD_BASIC;
        int[] cur = loadCustom(c);
        if (contains(cur, cur.length, color)) return ADD_DUPLICATE;
        int[] next = new int[cur.length + 1];
        next[0] = color;
        System.arraycopy(cur, 0, next, 1, cur.length);
        saveCustom(c, next);
        return ADD_OK;
    }

    /**
     * Quita un color personalizado. OJO: si es el acento aplicado, primero hay que llamar a setAccent(DEFAULT_ACCENT)
     * (mientras el color aún es válido) para que la versión suba y la Home se recree; después, removeCustom.
     */
    static synchronized void removeCustom(Context c, int color) {
        color |= 0xFF000000;
        int[] cur = loadCustom(c);
        if (!contains(cur, cur.length, color)) return;
        int[] next = new int[cur.length - 1];
        int n = 0;
        for (int v : cur) {
            if (v != color) next[n++] = v;
        }
        saveCustom(c, next);
    }

    /** Índice del color de la paleta más cercano (distancia RGB): lo usan los drawables XML con un color personalizado. */
    private static int nearestIndex(int color) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < ACCENT_COLORS.length; i++) {
            int dr = Color.red(color) - Color.red(ACCENT_COLORS[i]);
            int dg = Color.green(color) - Color.green(ACCENT_COLORS[i]);
            int db = Color.blue(color) - Color.blue(ACCENT_COLORS[i]);
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static int mixWhite(int color, float amount) {
        int r = Color.red(color) + Math.round((255 - Color.red(color)) * amount);
        int g = Color.green(color) + Math.round((255 - Color.green(color)) * amount);
        int b = Color.blue(color) + Math.round((255 - Color.blue(color)) * amount);
        return Color.argb(0xFF, r, g, b);
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
