package com.alouncher.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

/**
 * Capa del modo "Mover" de un widget (6B-2): se dibuja encima de la Home mientras dura el modo.
 * Oscurece lo demás y rodea el widget con el marco dorado. Al arrastrar el widget, una copia (sombra + marco dorado)
 * sigue al dedo y la celda de destino se resalta: dorado si cabe, rojizo si no. Al soltar:
 *  - destino válido (celdas libres y dentro del grid): la copia se asienta en la celda y se avisa con onDrop;
 *  - destino inválido: la copia vuelve a su sitio con una animación corta y se sale del modo.
 * Tocar fuera del widget (o "atrás") cancela. No conoce el modelo: la Activity le da la cuadrícula y pregunta por canDrop.
 */
final class WidgetMoveOverlay extends View {

    interface Listener {
        /** ¿Cabe el widget con su esquina superior izquierda en (row, col)? */
        boolean canDrop(int row, int col);

        /** El dedo empezó a arrastrar el widget. */
        void onDragStart();

        /** Soltó sobre un destino válido (la animación de asentarse ya terminó). */
        void onDrop(int row, int col);

        /** Salir del modo sin mover (soltó en lugar inválido, tocó fuera o canceló). */
        void onExit();

        /** La capa cambió de tamaño (p. ej. rotación): hay que recalcular la cuadrícula. */
        void onSize();
    }

    private Listener listener;
    private float gridLeft, gridTop, cellW, cellH;
    private int row, col, w = 1, h = 1;
    private Bitmap snapshot;

    private final float d;
    private final float margin;
    private final float slop;
    private final Paint scrim = new Paint();
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostFrame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF origin = new RectF();
    private final RectF box = new RectF();
    private final RectF pillBox = new RectF();
    private final Path hole = new Path();
    private final int okFill, okLine, badFill, badLine;

    private boolean downInside, downOutside, dragging, busy;
    private float downX, downY, grabX, grabY;
    private float fx, fy;               // esquina superior izquierda de la copia (coordenadas de la capa)
    private int tRow, tCol;             // celda de destino actual
    private boolean tValid;
    private ValueAnimator anim;

    WidgetMoveOverlay(Context c, int cellMarginPx) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        margin = cellMarginPx;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        scrim.setColor(0x59000000);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2.5f * d);
        frame.setColor(ThemeUtils.accent(c));
        ghostFrame.setStyle(Paint.Style.STROKE);
        ghostFrame.setStrokeWidth(2.5f * d);
        ghostFrame.setColor(ThemeUtils.accentHi(c));
        ghostShadow.setColor(0x73000000);
        ghostFill.setColor(c.getColor(R.color.sf2));
        bmpPaint.setAlpha(235);
        okFill = ThemeUtils.dropHl(c);
        okLine = ThemeUtils.dropHlLine(c);
        int danger = c.getColor(R.color.danger) & 0x00FFFFFF;
        badFill = danger | 0x33000000;
        badLine = danger | 0x99000000;
        tLine.setStyle(Paint.Style.STROKE);
        tLine.setStrokeWidth(2f * d);
        pill.setColor(c.getColor(R.color.paper));
        pillText.setColor(c.getColor(R.color.pi));
        pillText.setTextSize(14f * d);
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

    /** Celdas que ocupa el widget ahora (esquina superior izquierda y tamaño) y la vista de la que se toma la copia. */
    void setWidget(int row, int col, int w, int h, View view) {
        this.row = row;
        this.col = col;
        this.w = w;
        this.h = h;
        releaseSnapshot();
        try {
            if (view != null && view.getWidth() > 0 && view.getHeight() > 0) {
                Bitmap b = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(b));
                snapshot = b;
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            snapshot = null; // sin copia de la imagen: se dibuja un marco liso
        }
        invalidate();
    }

    private void releaseSnapshot() {
        if (snapshot != null) {
            snapshot.recycle();
            snapshot = null;
        }
    }

    private void originRect(RectF out) {
        out.set(gridLeft + col * cellW, gridTop + row * cellH,
                gridLeft + (col + w) * cellW, gridTop + (row + h) * cellH);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Celda de destino según dónde está la copia; válida solo dentro del grid y con celdas libres (opción B). */
    private void updateTarget() {
        tCol = Math.round((fx - gridLeft) / cellW);
        tRow = Math.round((fy - gridTop) / cellH);
        boolean inside = tCol >= 0 && tRow >= 0 && tCol + w <= HomeStore.HOME_COLS && tRow + h <= HomeStore.HOME_ROWS;
        tValid = inside && listener != null && listener.canDrop(tRow, tCol);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        if (listener != null) listener.onSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        originRect(origin);
        float radius = 20f * d;

        // Fondo atenuado, con un hueco donde está el widget
        hole.reset();
        hole.addRoundRect(origin, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipOutPath(hole);
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrim);
        canvas.restore();
        canvas.drawRoundRect(origin, radius, radius, frame);

        if (!dragging) {
            drawHint(canvas);
            return;
        }

        // Celda de destino (si se sale del grid se dibuja pegada al borde, en rojizo)
        int cc = clamp(tCol, 0, HomeStore.HOME_COLS - w);
        int rr = clamp(tRow, 0, HomeStore.HOME_ROWS - h);
        box.set(gridLeft + cc * cellW + margin, gridTop + rr * cellH + margin,
                gridLeft + (cc + w) * cellW - margin, gridTop + (rr + h) * cellH - margin);
        tFill.setColor(tValid ? okFill : badFill);
        tLine.setColor(tValid ? okLine : badLine);
        canvas.drawRoundRect(box, 18f * d, 18f * d, tFill);
        canvas.drawRoundRect(box, 18f * d, 18f * d, tLine);

        // Copia del widget siguiendo al dedo: sombra, imagen y marco dorado, un poco ampliada
        RectF g = new RectF(fx + margin, fy + margin, fx + w * cellW - margin, fy + h * cellH - margin);
        canvas.save();
        canvas.scale(1.03f, 1.03f, g.centerX(), g.centerY());
        RectF sh = new RectF(g);
        sh.offset(0, 6f * d);
        canvas.drawRoundRect(sh, 18f * d, 18f * d, ghostShadow);
        if (snapshot != null) {
            canvas.drawBitmap(snapshot, null, g, bmpPaint);
        } else {
            canvas.drawRoundRect(g, 18f * d, 18f * d, ghostFill);
        }
        canvas.drawRoundRect(g, 18f * d, 18f * d, ghostFrame);
        canvas.restore();
    }

    private void drawHint(Canvas canvas) {
        String t = getContext().getString(R.string.mover_ayuda);
        float pw = Math.min(getWidth() - 32f * d, pillText.measureText(t) + 36f * d);
        float ph = 40f * d;
        float top = 56f * d;
        pillBox.set((getWidth() - pw) / 2f, top, (getWidth() + pw) / 2f, top + ph);
        canvas.drawRoundRect(pillBox, ph / 2f, ph / 2f, pill);
        float ty = pillBox.centerY() - (pillText.descent() + pillText.ascent()) / 2f;
        canvas.drawText(t, pillBox.centerX(), ty, pillText);
    }

    /** Mueve la copia hasta (toX, toY) con una animación corta y luego ejecuta end. */
    private void settle(float toX, float toY, Runnable end) {
        busy = true;
        final float sx = fx;
        final float sy = fy;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(160);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            fx = sx + (toX - sx) * t;
            fy = sy + (toY - sy) * t;
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                busy = false;
                if (!cancelled) end.run();
            }
        });
        anim.start();
    }

    private void finishDrag() {
        final int r = tRow;
        final int c = tCol;
        if (tValid) {
            settle(gridLeft + c * cellW, gridTop + r * cellH, () -> {
                if (listener != null) listener.onDrop(r, c);
            });
        } else { // lugar inválido: vuelve a su sitio y se sale
            originRect(origin);
            settle(origin.left, origin.top, () -> {
                if (listener != null) listener.onExit();
            });
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (busy) return true; // la copia se está asentando
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = false;
                downInside = false;
                downOutside = false;
                downX = x;
                downY = y;
                originRect(origin);
                RectF slack = new RectF(origin);
                slack.inset(-6f * d, -6f * d);
                if (cellW > 0 && cellH > 0 && slack.contains(x, y)) {
                    downInside = true;
                    grabX = x - origin.left;
                    grabY = y - origin.top;
                } else {
                    downOutside = true;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (downInside) {
                    if (!dragging && Math.hypot(x - downX, y - downY) > slop) {
                        dragging = true;
                        if (listener != null) listener.onDragStart();
                    }
                    if (dragging) {
                        fx = x - grabX;
                        fy = y - grabY;
                        updateTarget();
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    finishDrag();
                } else if (downOutside && listener != null) {
                    listener.onExit(); // tocar fuera cancela
                }
                downInside = false;
                downOutside = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    tValid = false;
                    finishDrag(); // vuelve a su sitio
                }
                downInside = false;
                downOutside = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        releaseSnapshot();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
