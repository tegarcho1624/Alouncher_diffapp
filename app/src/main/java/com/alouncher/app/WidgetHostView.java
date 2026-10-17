package com.alouncher.app;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;

/**
 * Vista de un widget colocado en la Home (6B-2). Es la AppWidgetHostView de siempre, solo que detecta la pulsación
 * larga ANTES de que el widget (que suele tener sus propios toques) se la quede. Al detectarla avisa y, a partir de
 * ahí, intercepta el resto del gesto para que el widget no reciba un toque a medias.
 * Un toque corto o un arrastre normal siguen llegando al widget igual que antes.
 */
final class WidgetHostView extends AppWidgetHostView {

    interface OnLongPress {
        void onLongPress(WidgetHostView v);
    }

    private OnLongPress listener;
    private final float slop;
    private final Runnable task;
    private float downX, downY;
    private boolean fired;

    WidgetHostView(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        task = () -> {
            fired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            ViewParent p = getParent();
            if (p != null) p.requestDisallowInterceptTouchEvent(true); // que no se desplace la página
            if (listener != null) listener.onLongPress(this);
        };
    }

    void setOnLongPress(OnLongPress l) {
        listener = l;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                downX = ev.getRawX();
                downY = ev.getRawY();
                removeCallbacks(task);
                if (listener != null) postDelayed(task, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (!fired && Math.hypot(ev.getRawX() - downX, ev.getRawY() - downY) > slop) {
                    removeCallbacks(task);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(task);
                break;
            default:
                break;
        }
        boolean handled = super.dispatchTouchEvent(ev);
        int a = ev.getActionMasked();
        if (fired && (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)) fired = false;
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return fired; // ya fue pulsación larga: el widget no recibe el resto del gesto
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (fired) return true;
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(task);
        super.onDetachedFromWindow();
    }
}
