package com.alouncher.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.OvershootInterpolator;

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

    // Arrastrar apps dentro de la carpeta (4E-3)
    private final List<String> ids = new ArrayList<>(); // id de miembro de cada ícono (mismo orden que icons)
    private String liftedId;   // la app "levantada" (se está arrastrando): se ve tenue
    private String hoverId;    // la app sobre la que está el dedo (se resalta)
    private String popId;      // la app recién soltada (animación de "soltar")
    private float popT = 1f;
    private ValueAnimator popAnim;
    private boolean outside;   // el dedo salió del marco: se va a sacar de la carpeta
    private final Paint hoverFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hoverLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outLine = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Geometría del grid interno (la calcula computeGeometry; la comparten el dibujo y la detección de toques)
    private int gN;
    private int gCols;
    private float gCell;
    private float gIcon;
    private float gX0;
    private float gY0;

    FolderGridView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0x66161310);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0x40F1E9D8);
        line.setStrokeWidth(Math.max(1f, d));
        hoverFill.setColor(c.getColor(R.color.drop_hl));
        hoverLine.setStyle(Paint.Style.STROKE);
        hoverLine.setColor(c.getColor(R.color.drop_hl_line));
        hoverLine.setStrokeWidth(Math.max(1f, d));
        outLine.setStyle(Paint.Style.STROKE);
        outLine.setColor(c.getColor(R.color.gold_hi));
        outLine.setStrokeWidth(2f * d);
        outLine.setPathEffect(new DashPathEffect(new float[]{10f * d, 7f * d}, 0f));
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

        if (computeGeometry(w, h)) {
            for (int i = 0; i < gN; i++) {
                float cx = gX0 + (i % gCols + 0.5f) * gCell;
                float cy = gY0 + (i / gCols + 0.5f) * gCell;
                Drawable ic = icons.get(i);
                String id = i < ids.size() ? ids.get(i) : null;
                if (id != null && id.equals(hoverId)) { // el dedo está encima: se resalta como las celdas de la Home
                    float half = Math.min(gCell, 88f * d) / 2f;
                    rect.set(cx - half, cy - half, cx + half, cy + half);
                    canvas.drawRoundRect(rect, 14f * d, 14f * d, hoverFill);
                    canvas.drawRoundRect(rect, 14f * d, 14f * d, hoverLine);
                }
                int save = canvas.save();
                float alpha = 1f;
                if (id != null && id.equals(liftedId)) {
                    alpha = 0.3f; // "levantada": el ícono viaja con el dedo, aquí queda su lugar tenue
                } else if (id != null && id.equals(popId) && popT < 1f) {
                    float k = 1f - popT; // animación de soltar: empieza grande y se asienta
                    canvas.scale(1f + 0.22f * k, 1f + 0.22f * k, cx, cy);
                    alpha = 0.55f + 0.45f * popT;
                }
                ic.setBounds(Math.round(cx - gIcon / 2f), Math.round(cy - gIcon / 2f),
                        Math.round(cx + gIcon / 2f), Math.round(cy + gIcon / 2f));
                ic.setAlpha(Math.round(255 * alpha));
                ic.draw(canvas);
                ic.setAlpha(255);
                canvas.restoreToCount(save);
            }
        }
        if (outside) { // indicador: soltar aquí la saca de la carpeta
            rect.set(2f * d, 2f * d, w - 2f * d, h - 2f * d);
            canvas.drawRoundRect(rect, radius, radius, outLine);
        }
    }

    /**
     * Calcula dónde va cada ícono (columnas, tamaño de celda, origen). Es el mismo cálculo de siempre,
     * ahora en un solo sitio para que el dibujo y la detección de toques coincidan al píxel.
     */
    private boolean computeGeometry(int w, int h) {
        int total = icons.size();
        if (total == 0 || w <= 0 || h <= 0) return false;
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
        gN = n;
        gCols = bestCols;
        gCell = cell;
        gIcon = Math.min(cell * 0.78f, 64f * d);
        gX0 = pad + (availW - cell * bestCols) / 2f;
        gY0 = pad + (availH - cell * rows) / 2f;
        return true;
    }

    // ---- Arrastrar apps dentro de la carpeta (4E-3) ----------------------------------------

    /** Ids de miembro de los íconos, en el mismo orden que la lista de setContent. */
    void setMemberIds(List<String> memberIds) {
        ids.clear();
        ids.addAll(memberIds);
    }

    /** Id del miembro cuyo ícono (celda) está en ese punto (coordenadas de esta vista), o null. */
    String memberIdAt(float x, float y) {
        if (!computeGeometry(getWidth(), getHeight())) return null;
        for (int i = 0; i < gN && i < ids.size(); i++) {
            float cx = gX0 + (i % gCols + 0.5f) * gCell;
            float cy = gY0 + (i / gCols + 0.5f) * gCell;
            float half = gCell / 2f;
            if (Math.abs(x - cx) <= half && Math.abs(y - cy) <= half) return ids.get(i);
        }
        return null;
    }

    /** Tamaño en píxeles con el que se dibuja cada ícono ahora mismo (para la sombra del arrastre). */
    int iconSizePx() {
        return computeGeometry(getWidth(), getHeight()) ? Math.round(gIcon) : 0;
    }

    void setLifted(String id) {
        liftedId = id;
        invalidate();
    }

    void setHover(String id) {
        if ((id == null && hoverId == null) || (id != null && id.equals(hoverId))) return;
        hoverId = id;
        invalidate();
    }

    /** El dedo salió (o volvió) del marco: el panel se achica y aparece un borde dorado discontinuo. */
    void setOutside(boolean out) {
        if (outside == out) return;
        outside = out;
        animate().cancel();
        animate().scaleX(out ? 0.93f : 1f).scaleY(out ? 0.93f : 1f).setDuration(150).start();
        invalidate();
    }

    /** Termina cualquier efecto del arrastre. */
    void clearDragState() {
        liftedId = null;
        hoverId = null;
        if (outside) {
            outside = false;
            animate().cancel();
            setScaleX(1f);
            setScaleY(1f);
        }
        invalidate();
    }

    /** Animación de "soltar": el ícono de esa app aparece un poco grande y se asienta. */
    void popMember(String id) {
        if (id == null) return;
        popId = id;
        if (popAnim != null) popAnim.cancel();
        popT = 0f;
        popAnim = ValueAnimator.ofFloat(0f, 1f);
        popAnim.setDuration(320);
        popAnim.setInterpolator(new OvershootInterpolator(1.6f));
        popAnim.addUpdateListener(a -> {
            popT = Math.min(1f, (float) a.getAnimatedValue());
            invalidate();
        });
        popAnim.start();
    }
}
