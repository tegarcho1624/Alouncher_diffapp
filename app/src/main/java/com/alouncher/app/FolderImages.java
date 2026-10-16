package com.alouncher.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * Imágenes de las carpetas (ícono personalizado y fondo), copiadas a la carpeta interna del launcher
 * (filesDir/folders) para que sigan ahí aunque el usuario borre la original.
 * Si un archivo falta o no se puede leer, todo devuelve null y la carpeta usa su aspecto por defecto.
 */
final class FolderImages {
    private FolderImages() {}

    private static final int ICON_PX = 512;     // el ícono se guarda con el lado largo ≤ 512 px (mantiene su proporción)
    private static final int BG_MAX_PX = 1280;  // el fondo se guarda con el lado largo ≤ 1280 px
    private static final long KEEP_MS = 2 * 60 * 1000L; // prune no toca archivos recién creados

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(6);

    static File dir(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), "folders");
    }

    static File file(Context c, String name) {
        return new File(dir(c), name);
    }

    /** true si hay un nombre guardado y el archivo sigue existiendo. */
    static boolean exists(Context c, String name) {
        return name != null && !name.isEmpty() && file(c, name).isFile();
    }

    // ---- Importar (llamar fuera del hilo principal) -----------------------------

    /** Copia la imagen elegida como ícono (recortada a cuadrado). Devuelve el nombre guardado, o null. */
    static String importIcon(Context c, Uri uri, String folderId) {
        File tmp = copyToTemp(c, uri);
        if (tmp == null) return null;
        try {
            Bitmap src = ImageUtil.decodeSampled(tmp, ICON_PX, ICON_PX);
            if (src == null) return null;
            Bitmap out = src;
            int longest = Math.max(src.getWidth(), src.getHeight());
            if (longest > ICON_PX) {
                float k = (float) ICON_PX / longest;
                out = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * k)),
                        Math.max(1, Math.round(src.getHeight() * k)), true);
            }
            String name = safe(folderId) + "-icon-" + System.currentTimeMillis() + ".png";
            return savePng(c, out, name) ? name : null;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        } finally {
            tmp.delete();
        }
    }

    /** Copia la imagen elegida como fondo (reducida si es enorme). Devuelve el nombre guardado, o null. */
    static String importBackground(Context c, Uri uri, String folderId) {
        File tmp = copyToTemp(c, uri);
        if (tmp == null) return null;
        try {
            Bitmap src = ImageUtil.decodeSampled(tmp, BG_MAX_PX, BG_MAX_PX);
            if (src == null) return null;
            int longest = Math.max(src.getWidth(), src.getHeight());
            if (longest > BG_MAX_PX) {
                float k = (float) BG_MAX_PX / longest;
                src = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * k)),
                        Math.max(1, Math.round(src.getHeight() * k)), true);
            }
            String name = safe(folderId) + "-bg-" + System.currentTimeMillis() + ".jpg";
            File dest = file(c, name);
            if (!ensureDir(c)) return null;
            try (OutputStream out = new FileOutputStream(dest)) {
                if (!src.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
                    dest.delete();
                    return null;
                }
            } catch (IOException e) {
                dest.delete();
                return null;
            }
            return name;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        } finally {
            tmp.delete();
        }
    }

    private static File copyToTemp(Context c, Uri uri) {
        File tmp = new File(c.getApplicationContext().getCacheDir(), "folder-pick-" + System.nanoTime() + ".tmp");
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return null;
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return tmp;
        } catch (IOException | SecurityException e) {
            tmp.delete();
            return null;
        }
    }

    private static boolean savePng(Context c, Bitmap bmp, String name) {
        if (!ensureDir(c)) return false;
        File dest = file(c, name);
        try (OutputStream out = new FileOutputStream(dest)) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                dest.delete();
                return false;
            }
            return true;
        } catch (IOException e) {
            dest.delete();
            return false;
        }
    }

    private static boolean ensureDir(Context c) {
        File d = dir(c);
        return d.isDirectory() || d.mkdirs();
    }

    private static String safe(String s) {
        return s == null ? "x" : s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    // ---- Leer ------------------------------------------------------------------------

    private static Bitmap load(Context c, String name, int reqPx) {
        if (name == null || name.isEmpty()) return null;
        File f = file(c, name);
        if (!f.isFile()) return null;
        String key = f.getAbsolutePath();
        Bitmap b = CACHE.get(key);
        if (b != null && !b.isRecycled()) return b;
        b = ImageUtil.decodeSampled(f, reqPx, reqPx);
        if (b != null) CACHE.put(key, b);
        return b;
    }

    /** Ícono personalizado de la carpeta cerrada, o null (= usar las miniaturas). */
    static Drawable iconDrawable(Context c, String name, int sizePx) {
        Bitmap b = load(c, name, ICON_PX);
        return b == null ? null : FolderCoverDrawable.icon(b, sizePx);
    }

    /** Ícono personalizado como carátula de una carpeta redimensionada (cubre todo el rectángulo), o null. */
    static Drawable iconCoverDrawable(Context c, String name, float radiusPx) {
        Bitmap b = load(c, name, ICON_PX);
        return b == null ? null : FolderCoverDrawable.iconRect(b, radiusPx);
    }

    /** Fondo del panel abierto (imagen o color), o null (= sin fondo). */
    static Drawable backgroundDrawable(Context c, HomeStore.Folder f, float radiusPx) {
        Bitmap b = load(c, f.bgImage, 1080);
        if (b != null) return FolderCoverDrawable.image(b, radiusPx);
        if (f.hasBgColor && f.bgImage.isEmpty()) return FolderCoverDrawable.color(f.bgColor, radiusPx);
        return null; // sin fondo, o el archivo ya no existe
    }

    // ---- Borrar ----------------------------------------------------------------------

    static void delete(Context c, String name) {
        if (name == null || name.isEmpty()) return;
        File f = file(c, name);
        CACHE.remove(f.getAbsolutePath());
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Borra los archivos que ninguna carpeta usa (carpetas eliminadas, fusionadas, etc.). Fuera del hilo principal. */
    static void prune(Context c, Set<String> inUse) {
        File[] all = dir(c).listFiles();
        if (all == null) return;
        long now = System.currentTimeMillis();
        for (File f : all) {
            if (inUse.contains(f.getName())) continue;
            if (now - f.lastModified() < KEEP_MS) continue; // recién creado: aún puede estar por guardarse
            CACHE.remove(f.getAbsolutePath());
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
