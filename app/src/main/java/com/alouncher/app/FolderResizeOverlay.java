package com.alouncher.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Capa del modo "Redimensionar": se dibuja encima de la Home mientras dura el modo.
 * Oscurece lo demás, rodea la carpeta con un marco dorado (el acento de Alouncher) y pone un punto de control
 * en el centro de cada borde. Atiende el arrastre de los puntos y avisa al exterior; tocar fuera (o "Listo") sale.
 * No conoce el modelo: la Activity le da la cuadrícula (posición de las celdas) y el rectángulo de la carpeta.
 */
final class FolderResizeOverlay extends View {
    static final int TOP = 0, BOTTOM = 1, LEFT = 2, RIGHT = 3;

    interface Listener {
        /** (x, y) = dedo en coordenadas de la capa; (grabX, grabY) = distancia entre el dedo y el centro del punto al agarrarlo. */
        void onDrag(int handle, float x, float y, float grabX, float grabY);

        void onExit();

        /** La capa cambió de tamaño (p. ej. rotación): hay que recalcular la cuadrícula. */
        void onSize();
    }

    private Listener listener;
    private float gridLeft, gridTop, cellW, cellH;
    private int row, col, w = 1, h = 1;

    private final float d;
    private final Paint scrim = new Paint();
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF pillBox = new RectF();
    private final Path hole = new Path();

    private int active = -1;      // punto que se está arrastrando
    private float grabX, grabY;
    private boolean downOutside;  // el dedo bajó fuera de la carpeta y de los puntos
    private boolean downOnPill;
    private boolean hasH = true, hasV = true; // ejes con puntos de control (6B-4)

    FolderResizeOverlay(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        scrim.setColor(0x59000000);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2.5f * d);
        frame.setColor(ThemeUtils.accent(c));
        dotFill.setColor(ThemeUtils.accentHi(c));
        dotRing.setStyle(Paint.Style.STROKE);
        dotRing.setStrokeWidth(2f * d);
        dotRing.setColor(c.getColor(R.color.pi));   // borde oscuro: se ve sobre cualquier fondo
        dotShadow.setColor(0x66000000);
        pill.setColor(c.getColor(R.color.paper));
        pillText.setColor(c.getColor(R.color.pi));
        pillText.setTextSize(15f * d);
        pillText.setTextAlign(Paint.Align.CENTER);
        pillText.setFakeBoldText(true);
        setClickable(true); // consume todos los toques mientras el modo está activo
    }

    void setListener(Listener l) { listener = l; }

    /** Posición de la esquina de la cuadrícula y tamaño de una celda, en coordenadas de esta capa. */
    void setGrid(float left, float top, float cw, float ch) {
        gridLeft = left;
        gridTop = top;
        cellW = cw;
        cellH = ch;
        invalidate();
    }

    float gridLeft() { return gridLeft; }

    float gridTop() { return gridTop; }

    float cellWidth() { return cellW; }

    float cellHeight() { return cellH; }

    /** 6B-4 (widgets): oculta los puntos de un eje que no se puede cambiar. Por defecto los 4 puntos están activos. */
    void setHandles(boolean horizontal, boolean vertical) {
        hasH = horizontal;
        hasV = vertical;
        invalidate();
    }

    private boolean handleOn(int handle) {
        return (handle == TOP || handle == BOTTOM) ? hasV : hasH;
    }

    void setFolder(int row, int col, int w, int h) {
        this.row = row;
        this.col = col;
        this.w = w;
        this.h = h;
        invalidate();
    }

    /** El marco abarca exactamente las celdas de la carpeta (sus bordes caen sobre las líneas de la cuadrícula). */
    private void frameRect(RectF out) {
        out.set(gridLeft + col * cellW, gridTop + row * cellH,
                gridLeft + (col + w) * cellW, gridTop + (row + h) * cellH);
    }

    private void handleCenter(int handle, RectF r, float[] out) {
        switch (handle) {
            case TOP: out[0] = r.centerX(); out[1] = r.top; break;
            case BOTTOM: out[0] = r.centerX(); out[1] = r.bottom; break;
            case LEFT: out[0] = r.left; out[1] = r.centerY(); break;
            default: out[0] = r.right; out[1] = r.centerY(); break;
        }
    }

    private void pillRect() {
        float pw = 92f * d;
        float ph = 40f * d;
        float top = 56f * d;
        pillBox.set((getWidth() - pw) / 2f, top, (getWidth() + pw) / 2f, top + ph);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        if (listener != null) listener.onSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        frameRect(box);
        float radius = 20f * d;

        // Fondo atenuado, con un hueco donde está la carpeta
        hole.reset();
        hole.addRoundRect(box, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipOutPath(hole);
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrim);
        canvas.restore();

        canvas.drawRoundRect(box, radius, radius, frame);

        float[] c = new float[2];
        for (int hd = 0; hd < 4; hd++) {
            if (!handleOn(hd)) continue;
            handleCenter(hd, box, c);
            canvas.drawCircle(c[0], c[1] + 1f * d, 12f * d, dotShadow);
            canvas.drawCircle(c[0], c[1], 10f * d, dotFill);
            canvas.drawCircle(c[0], c[1], 10f * d, dotRing);
        }

        pillRect();
        canvas.drawRoundRect(pillBox, pillBox.height() / 2f, pillBox.height() / 2f, pill);
        float ty = pillBox.centerY() - (pillText.descent() + pillText.ascent()) / 2f;
        canvas.drawText(getContext().getString(R.string.listo), pillBox.centerX(), ty, pillText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                active = -1;
                downOutside = false;
                downOnPill = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                frameRect(box);
                float[] c = new float[2];
                float best = 34f * d;
                for (int hd = 0; hd < 4; hd++) {
                    if (!handleOn(hd)) continue;
                    handleCenter(hd, box, c);
                    float dist = (float) Math.hypot(x - c[0], y - c[1]);
                    if (dist <= best) {
                        best = dist;
                        active = hd;
                        grabX = x - c[0];
                        grabY = y - c[1];
                    }
                }
                if (active >= 0) return true;
                pillRect();
                if (pillBox.contains(x, y)) {
                    downOnPill = true;
                    return true;
                }
                RectF inner = new RectF(box);
                inner.inset(-6f * d, -6f * d);
                downOutside = !inner.contains(x, y);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (active >= 0 && listener != null) listener.onDrag(active, x, y, grabX, grabY);
                return true;
            case MotionEvent.ACTION_UP:
                pillRect();
                boolean exit = (downOutside && !isNear(x, y)) || (downOnPill && pillBox.contains(x, y));
                active = -1;
                downOutside = false;
                downOnPill = false;
                if (exit && listener != null) listener.onExit();
                return true;
            case MotionEvent.ACTION_CANCEL:
                active = -1;
                downOutside = false;
                downOnPill = false;
                return true;
            default:
                return true;
        }
    }

    /** Un toque que se soltó sobre la carpeta no cuenta como "fuera". */
    private boolean isNear(float x, float y) {
        frameRect(box);
        RectF inner = new RectF(box);
        inner.inset(-6f * d, -6f * d);
        return inner.contains(x, y);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
