package com.alouncher.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Capa que va encima de la cuadrícula de una página de la Home y coloca las carpetas redimensionadas
 * (más grandes que 1×1) sobre el rectángulo de celdas que ocupan. No intercepta toques fuera de sus hijos.
 * Usa el mismo padding y los mismos márgenes de celda que la cuadrícula, así que coinciden al píxel.
 */
final class FolderLayer extends ViewGroup {
    static final class LP extends ViewGroup.LayoutParams {
        final int row, col, w, h;

        LP(int row, int col, int w, int h) {
            super(MATCH_PARENT, MATCH_PARENT);
            this.row = row;
            this.col = col;
            this.w = w;
            this.h = h;
        }
    }

    private final int cellMargin;

    FolderLayer(Context c, int cellMarginPx) {
        super(c);
        this.cellMargin = cellMarginPx;
        setClipChildren(false);
    }

    void addFolder(View v, int row, int col, int w, int h) {
        addView(v, new LP(row, col, w, h));
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LP;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LP(0, 0, 1, 1);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LP(0, 0, 1, 1);
    }

    /** Rectángulo {izq, arriba, der, abajo} de las celdas que cubre el hijo, dentro de esta capa. */
    private int[] rect(LP lp, int width, int height) {
        float cw = (width - getPaddingLeft() - getPaddingRight()) / (float) HomeStore.HOME_COLS;
        float ch = (height - getPaddingTop() - getPaddingBottom()) / (float) HomeStore.HOME_ROWS;
        int l = Math.round(getPaddingLeft() + lp.col * cw + cellMargin);
        int t = Math.round(getPaddingTop() + lp.row * ch + cellMargin);
        int r = Math.round(getPaddingLeft() + (lp.col + lp.w) * cw - cellMargin);
        int b = Math.round(getPaddingTop() + (lp.row + lp.h) * ch - cellMargin);
        return new int[]{l, t, Math.max(l + 1, r), Math.max(t + 1, b)};
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int[] r = rect((LP) v.getLayoutParams(), w, h);
            v.measure(MeasureSpec.makeMeasureSpec(r[2] - r[0], MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(r[3] - r[1], MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int[] q = rect((LP) v.getLayoutParams(), w, h);
            v.layout(q[0], q[1], q[2], q[3]);
        }
    }
}
