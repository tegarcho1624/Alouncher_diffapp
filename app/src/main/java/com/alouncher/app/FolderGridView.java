package com.alouncher.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo de una carpeta redimensionada en la Home: un panel redondeado (o el fondo personalizado de la
 * carpeta) con las apps en un grid interno que se adapta al espacio: más apps = íconos más pequeños.
 * Solo dibuja; los toques los atiende la vista contenedora.
 */
final class FolderGridView extends View {
    private final List<Drawable> icons = new ArrayList<>();
    private Drawable custom; // fondo personalizado (imagen o color), o null
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;

    FolderGridView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0x66161310);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0x40F1E9D8);
        line.setStrokeWidth(Math.max(1f, d));
    }

    void setContent(List<Drawable> drawables, Drawable customBackground) {
        icons.clear();
        icons.addAll(drawables);
        custom = customBackground;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (custom != null) custom.setBounds(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = 20f * d;
        if (custom != null) {
            custom.setBounds(0, 0, w, h);
            custom.draw(canvas);
        } else {
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, radius, radius, bg);
            canvas.drawRoundRect(rect, radius, radius, line);
        }

        int total = icons.size();
        if (total == 0) return;
        float pad = 12f * d;
        float availW = w - 2f * pad;
        float availH = h - 2f * pad;
        float minCell = 26f * d;
        float maxCell = 88f * d;
        int maxCols = Math.max(1, (int) (availW / minCell));
        int maxRows = Math.max(1, (int) (availH / minCell));
        int n = Math.min(total, maxCols * maxRows); // lo que no cabe ni en miniatura no se dibuja

        // Elige las columnas que dejan las celdas más grandes
        int bestCols = 1;
        float bestCell = -1f;
        for (int cols = 1; cols <= Math.min(n, maxCols); cols++) {
            int rows = (n + cols - 1) / cols;
            if (rows > maxRows) continue;
            float cell = Math.min(availW / cols, availH / rows);
            if (cell >= bestCell) { // a igualdad, más columnas
                bestCell = cell;
                bestCols = cols;
            }
        }
        float cell = Math.min(bestCell, maxCell);
        int rows = (n + bestCols - 1) / bestCols;
        float icon = Math.min(cell * 0.78f, 64f * d);
        float x0 = pad + (availW - cell * bestCols) / 2f;
        float y0 = pad + (availH - cell * rows) / 2f;
        for (int i = 0; i < n; i++) {
            float cx = x0 + (i % bestCols + 0.5f) * cell;
            float cy = y0 + (i / bestCols + 0.5f) * cell;
            Drawable ic = icons.get(i);
            ic.setBounds(Math.round(cx - icon / 2f), Math.round(cy - icon / 2f),
                    Math.round(cx + icon / 2f), Math.round(cy + icon / 2f));
            ic.draw(canvas);
        }
    }
}
