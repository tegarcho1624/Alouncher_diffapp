package com.alouncher.app;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Imagen o color con esquinas redondeadas, recortado tipo "cover" al tamaño disponible.
 * Se usa para el ícono personalizado de la carpeta cerrada y para el fondo del panel abierto.
 */
final class FolderCoverDrawable extends Drawable {
    private final Bitmap bmp;
    private final BitmapShader shader;
    private final int color;
    private final float radiusFrac; // > 0: radio = fracción del lado menor
    private final float radiusPx;
    private final int scrim;        // velo oscuro sobre la imagen (0 = ninguno)
    private final int size;         // tamaño intrínseco (0 = sin)
    private final boolean border;   // borde tenue (ícono de carpeta)
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private FolderCoverDrawable(Bitmap bmp, int color, float radiusFrac, float radiusPx, int scrim, int size,
                                boolean border) {
        this.bmp = bmp;
        this.color = color;
        this.radiusFrac = radiusFrac;
        this.radiusPx = radiusPx;
        this.scrim = scrim;
        this.size = size;
        this.border = border;
        if (bmp != null) {
            shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            fill.setShader(shader);
        } else {
            shader = null;
            fill.setColor(color);
        }
        veil.setColor(scrim);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0x40F1E9D8);
        line.setStrokeWidth(Math.max(1f, (size > 0 ? size : 64) / 64f));
    }

    /** Ícono de carpeta cerrada: cuadrado con el mismo radio y borde que las miniaturas por defecto. */
    static FolderCoverDrawable icon(Bitmap b, int sizePx) {
        return new FolderCoverDrawable(b, 0, 0.26f, 0, 0, sizePx, true);
    }

    /** Ícono de una carpeta redimensionada: cubre todo el rectángulo (recorte "cover") con esquinas redondeadas. */
    static FolderCoverDrawable iconRect(Bitmap b, float radiusPx) {
        return new FolderCoverDrawable(b, 0, 0, radiusPx, 0, 0, true);
    }

    /** Fondo con imagen, con un velo ligero para que los nombres de las apps se lean. */
    static FolderCoverDrawable image(Bitmap b, float radiusPx) {
        return new FolderCoverDrawable(b, 0, 0, radiusPx, 0x59000000, 0, false);
    }

    /** Fondo de color sólido. */
    static FolderCoverDrawable color(int argb, float radiusPx) {
        return new FolderCoverDrawable(null, argb | 0xFF000000, 0, radiusPx, 0, 0, false);
    }

    @Override
    public int getIntrinsicWidth() {
        return size > 0 ? size : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return size > 0 ? size : -1;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        if (shader != null && bmp != null && b.width() > 0 && b.height() > 0) {
            float s = Math.max((float) b.width() / bmp.getWidth(), (float) b.height() / bmp.getHeight());
            matrix.setScale(s, s);
            matrix.postTranslate(b.left + (b.width() - bmp.getWidth() * s) / 2f,
                    b.top + (b.height() - bmp.getHeight() * s) / 2f);
            shader.setLocalMatrix(matrix);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        float w, h;
        if (radiusFrac > 0) { // ícono: cuadrado
            w = h = Math.min(b.width(), b.height());
        } else {
            w = b.width();
            h = b.height();
        }
        rect.set(b.left, b.top, b.left + w, b.top + h);
        float r = radiusFrac > 0 ? radiusFrac * Math.min(w, h) : radiusPx;
        canvas.drawRoundRect(rect, r, r, fill);
        if (scrim != 0) canvas.drawRoundRect(rect, r, r, veil);
        if (border) canvas.drawRoundRect(rect, r, r, line);
    }

    @Override
    public void setAlpha(int alpha) { }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
