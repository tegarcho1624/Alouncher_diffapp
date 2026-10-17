package com.alouncher.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado de la Home en SharedPreferences ("home_layout", clave "layout3"):
 * {"pages":[...], "main":id, "next":n,
 *  "items":[{"i":idInstancia,"k":"paquete/actividad","p":página,"r":fila,"c":columna} | {... "d":true ...}],
 *  "folders":[{"i":id,"n":nombre,"p":página,"r":fila,"c":columna,"x":ampliada,"ic":archivoÍcono,
 *              "bi":archivoFondo,"bc":colorFondo,"m":[{"i":idMiembro,"k":"paquete/actividad"}]}],
 *  "widgets":[{"i":appWidgetId,"pk":"paquete/clase del proveedor","p":página,"r":fila,"c":columna,"w":ancho,"h":alto}]}
 *
 * Una celda de la Home contiene UN elemento: una instancia de app o una carpeta. El dock solo lleva apps.
 * Las personalizaciones de las apps (ícono, nombre, oculta) NO viven aquí.
 */
final class HomeStore {
    static final int HOME_COLS = 6;
    static final int HOME_ROWS = 4;
    static final int DOCK_SLOTS = 7;
    static final int FOLDER_MAX = 3; // una carpeta mide entre 1x1 y 3x3 celdas
    static final int MAX_PAGES = 7;

    static final int OK = 0;
    static final int FULL = 1;

    /** Un ícono de app colocado. Inmutable: mover = crear otra con la nueva posición y el mismo id. */
    static final class Instance {
        final String id;
        final String key;   // "paquete/actividad" de la app
        final boolean dock;
        final int page;     // 0 en el dock
        final int row;
        final int col;

        Instance(String id, String key, boolean dock, int page, int row, int col) {
            this.id = id;
            this.key = key;
            this.dock = dock;
            this.page = page;
            this.row = row;
            this.col = col;
        }

        Instance at(boolean dock, int page, int row, int col) {
            return new Instance(id, key, dock, page, row, col);
        }

        String packageName() {
            int i = key.indexOf('/');
            return i < 0 ? key : key.substring(0, i);
        }
    }

    /** Una app dentro de una carpeta (con id propio, así puede repetirse la misma app). */
    static final class Member {
        final String id;
        final String key;

        Member(String id, String key) {
            this.id = id;
            this.key = key;
        }
    }

    /** Una carpeta de la Home. Lo que devuelve el almacén son COPIAS: para cambiar algo se usan sus métodos. */
    static final class Folder {
        final String id;
        String name = "";          // "" = nombre por defecto ("Carpeta")
        int page;
        int row;
        int col;
        int w = 1;                 // ancho en celdas (1..FOLDER_MAX); row/col es la esquina superior izquierda
        int h = 1;                 // alto en celdas (1..FOLDER_MAX)
        boolean expanded;          // vista ampliada (sub-fase siguiente)
        String icon = "";          // archivo del ícono personalizado ("" = miniaturas)
        String bgImage = "";       // archivo de la imagen de fondo ("" = ninguna)
        boolean hasBgColor;        // color sólido de fondo
        int bgColor;
        final List<Member> members = new ArrayList<>();

        Folder(String id, int page, int row, int col) {
            this.id = id;
            this.page = page;
            this.row = row;
            this.col = col;
        }

        Folder copy() {
            Folder f = new Folder(id, page, row, col);
            f.name = name;
            f.w = w;
            f.h = h;
            f.expanded = expanded;
            f.icon = icon;
            f.bgImage = bgImage;
            f.hasBgColor = hasBgColor;
            f.bgColor = bgColor;
            f.members.addAll(members);
            return f;
        }
    }

    private final SharedPreferences sp;
    private final LinkedHashMap<String, Instance> map = new LinkedHashMap<>();
    private final LinkedHashMap<String, Folder> folders = new LinkedHashMap<>();

    /** Un widget colocado en la Home (6B-1). Inmutable. id = appWidgetId. */
    static final class Widget {
        final int id;
        final int page;
        final int row;
        final int col;
        final int w;
        final int h;
        final String provider; // ComponentName del proveedor en texto (paquete/clase); "" si no se conoce (6B-3)

        Widget(int id, int page, int row, int col, int w, int h, String provider) {
            this.id = id;
            this.page = page;
            this.row = row;
            this.col = col;
            this.w = w;
            this.h = h;
            this.provider = provider != null ? provider : "";
        }

        /** Paquete de la app que provee el widget. */
        String packageName() {
            int i = provider.indexOf('/');
            return i < 0 ? provider : provider.substring(0, i);
        }
    }

    /** Widgets colocados (6B-3: se guardan en "home_layout", junto con apps y carpetas). */
    private final LinkedHashMap<Integer, Widget> widgets = new LinkedHashMap<>();
    private final ArrayList<Integer> pages = new ArrayList<>();
    private int mainId = 1;
    private int nextId = 2;
    private boolean widgetsDropped;                                  // al leer, algún widget ya no cabía: hay que guardar
    private final ArrayList<Integer> droppedWidgetIds = new ArrayList<>(); // ids de esos widgets (el host debe liberarlos)

    HomeStore(Context ctx) {
        sp = ctx.getSharedPreferences("home_layout", Context.MODE_PRIVATE);
        boolean migrated = load();
        if (migrated || widgetsDropped) save(); // deja los ids nuevos guardados
    }

    private boolean valid(boolean dock, int page, int r, int c) {
        if (dock) return r == 0 && c >= 0 && c < DOCK_SLOTS;
        return pages.contains(page) && r >= 0 && r < HOME_ROWS && c >= 0 && c < HOME_COLS;
    }

    /** @return true si se leyó un formato anterior (hay que volver a guardar). */
    private boolean load() {
        boolean migrated = false;
        try {
            String raw3 = sp.getString("layout3", null);
            String raw2 = sp.getString("layout2", null);
            String raw = raw3 != null ? raw3 : raw2;
            if (raw != null) {
                migrated = raw3 == null;
                JSONObject o = new JSONObject(raw);
                JSONArray pa = o.optJSONArray("pages");
                if (pa != null) {
                    for (int i = 0; i < pa.length(); i++) {
                        int id = pa.getInt(i);
                        if (!pages.contains(id)) pages.add(id);
                    }
                }
                mainId = o.optInt("main", 1);
                nextId = o.optInt("next", 2);
                readItems(o.optJSONArray("items"));
                readFolders(o.optJSONArray("folders"));
                readWidgets(o.optJSONArray("widgets"));
            } else {
                String legacy = sp.getString("layout", null);
                if (legacy != null) {
                    migrated = true;
                    pages.add(1);
                    readItems(new JSONArray(legacy));
                }
            }
        } catch (JSONException e) {
            map.clear();
            folders.clear();
            widgets.clear();
            pages.clear();
        }
        if (pages.isEmpty()) {
            pages.add(1);
            mainId = 1;
        }
        if (!pages.contains(mainId)) mainId = pages.get(0);
        int max = 0;
        for (int id : pages) max = Math.max(max, id);
        nextId = Math.max(nextId, max + 1);
        return migrated;
    }

    private void readItems(JSONArray a) throws JSONException {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String k = o.getString("k");
            String id = o.has("i") ? o.getString("i") : UUID.randomUUID().toString();
            boolean d = o.optBoolean("d", false);
            int p = d ? 0 : o.optInt("p", 1);
            int r = o.optInt("r", 0);
            int c = o.optInt("c", 0);
            if (valid(d, p, r, c) && !occupied(d, p, r, c) && !map.containsKey(id)) {
                map.put(id, new Instance(id, k, d, p, r, c));
            }
        }
    }

    private void readFolders(JSONArray a) throws JSONException {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String id = o.getString("i");
            int p = o.optInt("p", 1);
            int r = o.optInt("r", 0);
            int c = o.optInt("c", 0);
            if (!valid(false, p, r, c) || occupied(false, p, r, c) || folders.containsKey(id)) continue;
            Folder f = new Folder(id, p, r, c);
            // Tamaño guardado como "2x3" (ancho x alto). Si ya no cabe o está ocupado, vuelve a 1x1.
            String sz = o.optString("sz", "");
            int x = sz.indexOf('x');
            if (x > 0) {
                try {
                    int sw = Integer.parseInt(sz.substring(0, x));
                    int sh = Integer.parseInt(sz.substring(x + 1));
                    if (rectFree(id, p, r, c, sw, sh)) {
                        f.w = sw;
                        f.h = sh;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            f.name = o.optString("n", "");
            f.expanded = o.optBoolean("x", false);
            f.icon = o.optString("ic", "");
            f.bgImage = o.optString("bi", "");
            if (o.has("bc")) {
                f.hasBgColor = true;
                f.bgColor = o.getInt("bc");
            }
            JSONArray ma = o.optJSONArray("m");
            if (ma != null) {
                for (int k = 0; k < ma.length(); k++) {
                    JSONObject m = ma.getJSONObject(k);
                    f.members.add(new Member(m.getString("i"), m.getString("k")));
                }
            }
            if (!f.members.isEmpty()) folders.put(id, f); // una carpeta vacía no se conserva
        }
    }

    /**
     * Widgets guardados (6B-3). Se leen después de apps y carpetas. Uno dañado, en una página que ya no existe
     * o cuyo rectángulo ya no está libre se descarta sin errores; su id queda en droppedWidgetIds para que el host lo libere.
     */
    private void readWidgets(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !o.has("i")) {
                widgetsDropped = true;
                continue;
            }
            int id = o.optInt("i", -1);
            int p = o.optInt("p", 1);
            int r = o.optInt("r", 0);
            int c = o.optInt("c", 0);
            int w = o.optInt("w", 1);
            int h = o.optInt("h", 1);
            if (id < 0 || widgets.containsKey(id)) {
                widgetsDropped = true;
                continue;
            }
            if (!pages.contains(p) || !widgetRectFree(p, r, c, w, h)) {
                widgetsDropped = true;
                droppedWidgetIds.add(id);
                continue;
            }
            widgets.put(id, new Widget(id, p, r, c, w, h, o.optString("pk", "")));
        }
    }

    /** Ids de widgets que no se pudieron conservar al leer lo guardado (se entregan una sola vez). */
    synchronized List<Integer> takeDroppedWidgetIds() {
        List<Integer> out = new ArrayList<>(droppedWidgetIds);
        droppedWidgetIds.clear();
        return out;
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            JSONArray pa = new JSONArray();
            for (int id : pages) pa.put(id);
            o.put("pages", pa);
            o.put("main", mainId);
            o.put("next", nextId);
            JSONArray items = new JSONArray();
            for (Instance in : map.values()) {
                JSONObject it = new JSONObject();
                it.put("i", in.id);
                it.put("k", in.key);
                if (in.dock) it.put("d", true); else it.put("p", in.page);
                it.put("r", in.row);
                it.put("c", in.col);
                items.put(it);
            }
            o.put("items", items);
            JSONArray fa = new JSONArray();
            for (Folder f : folders.values()) {
                JSONObject fo = new JSONObject();
                fo.put("i", f.id);
                fo.put("n", f.name);
                fo.put("p", f.page);
                fo.put("r", f.row);
                fo.put("c", f.col);
                if (f.expanded) fo.put("x", true);
                if (f.w > 1 || f.h > 1) fo.put("sz", f.w + "x" + f.h);
                if (!f.icon.isEmpty()) fo.put("ic", f.icon);
                if (!f.bgImage.isEmpty()) fo.put("bi", f.bgImage);
                if (f.hasBgColor) fo.put("bc", f.bgColor);
                JSONArray ma = new JSONArray();
                for (Member m : f.members) {
                    JSONObject mo = new JSONObject();
                    mo.put("i", m.id);
                    mo.put("k", m.key);
                    ma.put(mo);
                }
                fo.put("m", ma);
                fa.put(fo);
            }
            o.put("folders", fa);
            JSONArray wa = new JSONArray();
            for (Widget wd : widgets.values()) {
                JSONObject wo = new JSONObject();
                wo.put("i", wd.id);
                if (!wd.provider.isEmpty()) wo.put("pk", wd.provider);
                wo.put("p", wd.page);
                wo.put("r", wd.row);
                wo.put("c", wd.col);
                wo.put("w", wd.w);
                wo.put("h", wd.h);
                wa.put(wo);
            }
            o.put("widgets", wa);
            sp.edit().putString("layout3", o.toString()).remove("layout2").remove("layout").apply();
        } catch (JSONException ignored) {
        }
    }

    // ---- Páginas ------------------------------------------------------------

    synchronized int pageCount() { return pages.size(); }

    synchronized int pageId(int index) { return pages.get(index); }

    synchronized int indexOf(int id) { return pages.indexOf(id); }

    synchronized int mainId() { return mainId; }

    synchronized int mainIndex() { return pages.indexOf(mainId); }

    /** Inserta una página vacía en esa posición. Devuelve su id, o -1 si ya se llegó al máximo. */
    synchronized int insertPage(int index) {
        if (pages.size() >= MAX_PAGES) return -1;
        int id = nextId++;
        int at = Math.max(0, Math.min(pages.size(), index));
        pages.add(at, id);
        save();
        return id;
    }

    /** No elimina la principal ni la última. Lo que hubiera en esa página (apps y carpetas) deja de estar en la Home. */
    synchronized boolean removePage(int id) {
        if (pages.size() <= 1 || id == mainId || !pages.contains(id)) return false;
        pages.remove(Integer.valueOf(id));
        Iterator<Instance> it = map.values().iterator();
        while (it.hasNext()) {
            Instance in = it.next();
            if (!in.dock && in.page == id) it.remove();
        }
        Iterator<Folder> fi = folders.values().iterator();
        while (fi.hasNext()) {
            if (fi.next().page == id) fi.remove();
        }
        widgets.values().removeIf(wd -> wd.page == id);
        save();
        return true;
    }

    synchronized void movePage(int from, int to) {
        if (from < 0 || to < 0 || from >= pages.size() || to >= pages.size() || from == to) return;
        Integer id = pages.remove(from);
        pages.add(to, id);
        save();
    }

    // ---- Apps ------------------------------------------------------------------

    synchronized List<Instance> snapshot() {
        return new ArrayList<>(map.values());
    }

    synchronized Instance get(String id) {
        return map.get(id);
    }

    synchronized Instance instanceAt(boolean dock, int page, int r, int c) {
        for (Instance in : map.values()) {
            if (in.dock != dock) continue;
            if (!dock && in.page != page) continue;
            if (in.row == r && in.col == c) return in;
        }
        return null;
    }

    private Folder folderAtRaw(int page, int r, int c) {
        for (Folder f : folders.values()) {
            if (f.page == page && r >= f.row && r < f.row + f.h && c >= f.col && c < f.col + f.w) return f;
        }
        return null;
    }

    /** ¿Cabe ese rectángulo dentro del grid y está libre (ignorando a la propia carpeta)? */
    private boolean rectFree(String selfId, int page, int row, int col, int w, int h) {
        if (w < 1 || h < 1 || w > FOLDER_MAX || h > FOLDER_MAX) return false;
        if (row < 0 || col < 0 || row + h > HOME_ROWS || col + w > HOME_COLS) return false;
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                if (instanceAt(false, page, r, c) != null) return false;
                Folder other = folderAtRaw(page, r, c);
                if (other != null && !other.id.equals(selfId)) return false;
                if (widgetAtRaw(page, r, c) != null) return false;
            }
        }
        return true;
    }

    /** ¿Hay algo (app o carpeta) en esa celda? El dock solo lleva apps. */
    private boolean occupied(boolean dock, int page, int r, int c) {
        return instanceAt(dock, page, r, c) != null
                || (!dock && (folderAtRaw(page, r, c) != null || widgetAtRaw(page, r, c) != null));
    }

    /** Quita SOLO esa instancia. */
    synchronized void remove(String id) {
        if (map.remove(id) != null) save();
    }

    /** Desinstalación: quita todas las instancias de ese paquete y lo saca de las carpetas (las que queden vacías se eliminan). */
    synchronized void removePackage(String pkg) {
        boolean changed = false;
        Iterator<Instance> it = map.values().iterator();
        while (it.hasNext()) {
            if (it.next().key.startsWith(pkg + "/")) {
                it.remove();
                changed = true;
            }
        }
        for (Folder f : folders.values()) {
            if (f.members.removeIf(m -> m.key.startsWith(pkg + "/"))) changed = true;
        }
        if (dropEmptyFolders()) changed = true;
        if (changed) save();
    }

    /** Al abrir el launcher: elimina lo que ya no está instalado (apps y miembros de carpetas) para no dejar celdas "ocupadas" invisibles. */
    synchronized void pruneMissing(Set<String> installedKeys) {
        boolean changed = false;
        Iterator<Instance> it = map.values().iterator();
        while (it.hasNext()) {
            if (!installedKeys.contains(it.next().key)) {
                it.remove();
                changed = true;
            }
        }
        for (Folder f : folders.values()) {
            if (f.members.removeIf(m -> !installedKeys.contains(m.key))) changed = true;
        }
        if (dropEmptyFolders()) changed = true;
        if (changed) save();
    }

    private boolean dropEmptyFolders() {
        boolean changed = false;
        Iterator<Folder> fi = folders.values().iterator();
        while (fi.hasNext()) {
            if (fi.next().members.isEmpty()) {
                fi.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Viene del cajón: crea una instancia NUEVA (con id nuevo) aunque la app ya esté en la Home.
     * Si el lugar está ocupado, va al lugar libre más cercano de esa página/dock (o FULL si no hay).
     */
    synchronized int addNew(String key, boolean dock, int page, int r, int c) {
        int pg = dock ? 0 : page;
        if (!valid(dock, pg, r, c)) return FULL;
        int rr = r;
        int cc = c;
        if (occupied(dock, pg, r, c)) {
            int[] free = nearestFree(dock, pg, r, c);
            if (free == null) return FULL;
            rr = free[0];
            cc = free[1];
        }
        String id = UUID.randomUUID().toString();
        map.put(id, new Instance(id, key, dock, pg, rr, cc));
        save();
        return OK;
    }

    /**
     * Mueve una instancia existente (no crea otra). Si el destino está ocupado por otra
     * instancia, intercambian lugares (se usa con el dock; en la Home soltar sobre una app crea una carpeta).
     */
    synchronized int move(String id, boolean dock, int page, int r, int c) {
        Instance src = map.get(id);
        if (src == null) return FULL;
        int pg = dock ? 0 : page;
        if (!valid(dock, pg, r, c)) return FULL;
        if (!dock && folderAtRaw(pg, r, c) != null) return FULL; // una carpeta no se pisa
        if (!dock && widgetAtRaw(pg, r, c) != null) return FULL;  // un widget tampoco
        Instance occ = instanceAt(dock, pg, r, c);
        if (occ != null && occ.id.equals(id)) return OK;
        if (occ == null) {
            map.put(id, src.at(dock, pg, r, c));
            save();
            return OK;
        }
        map.put(occ.id, occ.at(src.dock, src.page, src.row, src.col));
        map.put(id, src.at(dock, pg, r, c));
        save();
        return OK;
    }

    private int[] nearestFree(boolean dock, int page, int r0, int c0) {
        int rows = dock ? 1 : HOME_ROWS;
        int cols = dock ? DOCK_SLOTS : HOME_COLS;
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (occupied(dock, page, r, c)) continue;
                int d = (r - r0) * (r - r0) + (c - c0) * (c - c0);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{r, c};
                }
            }
        }
        return best;
    }

    // ---- Widgets (6B-1 colocar, 6B-3 guardar) ---------------------------------------

    synchronized List<Widget> widgets() {
        return new ArrayList<>(widgets.values());
    }

    private Widget widgetAtRaw(int page, int r, int c) {
        for (Widget wd : widgets.values()) {
            if (wd.page == page && r >= wd.row && r < wd.row + wd.h && c >= wd.col && c < wd.col + wd.w) return wd;
        }
        return null;
    }

    /** ¿Cabe un widget de w x h celdas con su esquina superior izquierda ahí, sin pisar apps, carpetas ni otros widgets? */
    private boolean widgetRectFree(int page, int row, int col, int w, int h) {
        if (w < 1 || h < 1 || row < 0 || col < 0 || row + h > HOME_ROWS || col + w > HOME_COLS) return false;
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                if (instanceAt(false, page, r, c) != null) return false;
                if (folderAtRaw(page, r, c) != null) return false;
                if (widgetAtRaw(page, r, c) != null) return false;
            }
        }
        return true;
    }

    /**
     * Primer lugar libre para un widget de w x h celdas: empieza en la página de índice startIndex,
     * sigue con las siguientes y después con las anteriores; dentro de cada página va por filas.
     * @return {idPágina, fila, columna} o null si no cabe en ninguna.
     */
    synchronized int[] findWidgetSpot(int startIndex, int w, int h) {
        int n = pages.size();
        if (n == 0) return null;
        int start = Math.max(0, Math.min(n - 1, startIndex));
        for (int k = 0; k < n; k++) {
            int page = pages.get((start + k) % n);
            for (int r = 0; r + h <= HOME_ROWS; r++) {
                for (int c = 0; c + w <= HOME_COLS; c++) {
                    if (widgetRectFree(page, r, c, w, h)) return new int[]{page, r, c};
                }
            }
        }
        return null;
    }

    /** Registra un widget ya vinculado y lo guarda. false si ese rectángulo ya no está libre. */
    synchronized boolean addWidget(int id, int page, int row, int col, int w, int h, String provider) {
        if (!pages.contains(page) || !widgetRectFree(page, row, col, w, h)) return false;
        widgets.put(id, new Widget(id, page, row, col, w, h, provider));
        save();
        return true;
    }

    synchronized Widget widget(int id) {
        return widgets.get(id);
    }

    /**
     * 6B-2: ¿cabe el widget (con su tamaño actual) con su esquina superior izquierda en (row, col) de esa página?
     * Sus propias celdas actuales cuentan como libres; apps, carpetas y otros widgets, no. Fuera del grid: no.
     */
    synchronized boolean canMoveWidget(int id, int page, int row, int col) {
        Widget wd = widgets.get(id);
        if (wd == null || !pages.contains(page)) return false;
        if (row < 0 || col < 0 || row + wd.h > HOME_ROWS || col + wd.w > HOME_COLS) return false;
        for (int r = row; r < row + wd.h; r++) {
            for (int c = col; c < col + wd.w; c++) {
                if (instanceAt(false, page, r, c) != null) return false;
                if (folderAtRaw(page, r, c) != null) return false;
                Widget other = widgetAtRaw(page, r, c);
                if (other != null && other.id != id) return false;
            }
        }
        return true;
    }

    /** 6B-2: cambia de sitio un widget ya colocado (mismo tamaño) y lo guarda. false (sin cambios) si no cabe. */
    synchronized boolean moveWidget(int id, int page, int row, int col) {
        if (!canMoveWidget(id, page, row, col)) return false;
        Widget wd = widgets.get(id);
        widgets.put(id, new Widget(id, page, row, col, wd.w, wd.h, wd.provider));
        save();
        return true;
    }

    /** Quita el registro de un widget que no se pudo restaurar (sus celdas quedan libres). No hay menú de usuario para esto (6B-5). */
    synchronized boolean removeWidget(int id) {
        if (widgets.remove(id) == null) return false;
        save();
        return true;
    }

    // ---- Carpetas ------------------------------------------------------------------

    synchronized List<Folder> folders() {
        List<Folder> out = new ArrayList<>();
        for (Folder f : folders.values()) out.add(f.copy());
        return out;
    }

    synchronized Folder folder(String id) {
        Folder f = folders.get(id);
        return f == null ? null : f.copy();
    }

    synchronized Folder folderAt(int page, int r, int c) {
        Folder f = folderAtRaw(page, r, c);
        return f == null ? null : f.copy();
    }

    /**
     * Se suelta una app SOBRE otra app de la Home: se crea una carpeta nueva con ambas, en el lugar de la receptora.
     * draggedInstanceId == null: la app viene del cajón (no hay instancia que quitar).
     */
    synchronized int dropOnInstance(String key, String draggedInstanceId, String receptorId) {
        Instance rec = map.get(receptorId);
        if (rec == null || rec.dock) return FULL;
        if (receptorId.equals(draggedInstanceId)) return OK;
        Folder f = new Folder(UUID.randomUUID().toString(), rec.page, rec.row, rec.col);
        f.members.add(new Member(rec.id, rec.key));
        String mid = draggedInstanceId != null ? draggedInstanceId : UUID.randomUUID().toString();
        f.members.add(new Member(mid, key));
        map.remove(receptorId);
        if (draggedInstanceId != null) map.remove(draggedInstanceId);
        folders.put(f.id, f);
        save();
        return OK;
    }

    /** Se suelta una app SOBRE una carpeta: se añade a ella (no se crea otra). */
    synchronized int dropOnFolder(String key, String draggedInstanceId, String folderId) {
        Folder f = folders.get(folderId);
        if (f == null) return FULL;
        String mid = draggedInstanceId != null ? draggedInstanceId : UUID.randomUUID().toString();
        if (draggedInstanceId != null) map.remove(draggedInstanceId);
        f.members.add(new Member(mid, key));
        save();
        return OK;
    }

    /** Añade una app al final de la carpeta (desde el botón "+"). Se permite repetir una app, como con las instancias. */
    synchronized int addMember(String folderId, String key) {
        Folder f = folders.get(folderId);
        if (f == null) return FULL;
        f.members.add(new Member(UUID.randomUUID().toString(), key));
        save();
        return OK;
    }

    /** Se suelta una carpeta sobre otra: las apps de la primera pasan a la segunda y la primera desaparece. */
    synchronized int mergeFolders(String srcId, String dstId) {
        if (srcId.equals(dstId)) return OK;
        Folder src = folders.get(srcId);
        Folder dst = folders.get(dstId);
        if (src == null || dst == null) return FULL;
        dst.members.addAll(src.members);
        folders.remove(srcId);
        save();
        return OK;
    }

    /**
     * Mueve una carpeta en la Home. Destino libre: se mueve. Sobre una app: intercambian lugares.
     * Sobre otra carpeta: se fusionan.
     */
    synchronized int moveFolder(String id, int page, int r, int c) {
        Folder f = folders.get(id);
        if (f == null || !valid(false, page, r, c)) return FULL;
        if (f.w > 1 || f.h > 1) {
            // Carpeta grande: no se intercambia ni se fusiona; solo se coloca si todo su rectángulo está libre.
            int rr = Math.min(r, HOME_ROWS - f.h);
            int cc = Math.min(c, HOME_COLS - f.w);
            if (f.page == page && f.row == rr && f.col == cc) return OK;
            if (!rectFree(id, page, rr, cc, f.w, f.h)) return FULL;
            f.page = page;
            f.row = rr;
            f.col = cc;
            save();
            return OK;
        }
        if (f.page == page && f.row == r && f.col == c) return OK;
        if (widgetAtRaw(page, r, c) != null) return FULL; // un widget no se pisa
        Folder dst = folderAtRaw(page, r, c);
        if (dst != null) return mergeFolders(id, dst.id);
        Instance occ = instanceAt(false, page, r, c);
        if (occ != null) map.put(occ.id, occ.at(false, f.page, f.row, f.col)); // la app ocupa el lugar de la carpeta
        f.page = page;
        f.row = r;
        f.col = c;
        save();
        return OK;
    }

    /** ¿Podría la carpeta ocupar ese rectángulo (dentro del grid, tamaño 1..3, celdas libres)? No cambia nada. */
    synchronized boolean canPlaceFolder(String id, int row, int col, int w, int h) {
        Folder f = folders.get(id);
        return f != null && rectFree(id, f.page, row, col, w, h);
    }

    /**
     * Cambia el rectángulo de la carpeta (esquina superior izquierda + tamaño). Solo si cabe en el grid
     * y todas sus celdas están libres: si no, no cambia nada y devuelve FULL (las apps nunca se desplazan).
     */
    synchronized int resizeFolder(String id, int row, int col, int w, int h) {
        Folder f = folders.get(id);
        if (f == null) return FULL;
        if (!rectFree(id, f.page, row, col, w, h)) return FULL;
        f.row = row;
        f.col = col;
        f.w = w;
        f.h = h;
        save();
        return OK;
    }

    synchronized void renameFolder(String id, String name) {
        Folder f = folders.get(id);
        if (f == null) return;
        f.name = name == null ? "" : name;
        save();
    }

    /** Saca una app de la carpeta y la deja en la Home, en el lugar libre más cercano. FULL si la página no tiene sitio. */
    synchronized int removeMember(String folderId, String memberId) {
        Folder f = folders.get(folderId);
        if (f == null) return OK;
        Member found = null;
        for (Member m : f.members) {
            if (m.id.equals(memberId)) found = m;
        }
        if (found == null) return OK;
        int[] free = nearestFree(false, f.page, f.row, f.col);
        if (free == null) return FULL;
        f.members.remove(found);
        map.put(found.id, new Instance(found.id, found.key, false, f.page, free[0], free[1]));
        if (f.members.isEmpty()) folders.remove(folderId);
        save();
        return OK;
    }

    /** Reordenar (4E-3): dos apps de la carpeta intercambian lugares. Se guarda al instante. */
    synchronized int swapMembers(String folderId, String idA, String idB) {
        Folder f = folders.get(folderId);
        if (f == null) return FULL;
        int a = -1;
        int b = -1;
        for (int i = 0; i < f.members.size(); i++) {
            String id = f.members.get(i).id;
            if (id.equals(idA)) a = i;
            if (id.equals(idB)) b = i;
        }
        if (a < 0 || b < 0) return FULL;
        if (a == b) return OK;
        Member t = f.members.get(a);
        f.members.set(a, f.members.get(b));
        f.members.set(b, t);
        save();
        return OK;
    }

    /**
     * Sacar de la carpeta arrastrando (4E-3): la app se suelta en una celda de la Home o del dock.
     *  - sobre OTRA carpeta: pasa a ella;
     *  - celda libre: se queda ahí; ocupada por una app: el lugar libre más cercano a ese punto;
     *  - sin sitio: FULL y no cambia nada.
     * Si la carpeta de origen queda vacía, se elimina (igual que con removeMember).
     */
    synchronized int moveMemberOut(String folderId, String memberId, boolean dock, int page, int r, int c) {
        Folder f = folders.get(folderId);
        if (f == null) return FULL;
        Member found = null;
        for (Member m : f.members) {
            if (m.id.equals(memberId)) found = m;
        }
        if (found == null) return FULL;
        int pg = dock ? 0 : page;
        if (!valid(dock, pg, r, c)) return FULL;
        if (!dock) {
            Folder dst = folderAtRaw(pg, r, c);
            if (dst != null) {
                if (dst.id.equals(folderId)) return OK; // soltada sobre su propia carpeta: no pasa nada
                f.members.remove(found);
                dst.members.add(found);
                if (f.members.isEmpty()) folders.remove(folderId);
                save();
                return OK;
            }
        }
        int rr = r;
        int cc = c;
        if (occupied(dock, pg, r, c)) {
            int[] free = nearestFree(dock, pg, r, c);
            if (free == null) return FULL;
            rr = free[0];
            cc = free[1];
        }
        f.members.remove(found);
        map.put(found.id, new Instance(found.id, found.key, dock, pg, rr, cc));
        if (f.members.isEmpty()) folders.remove(folderId);
        save();
        return OK;
    }

    /**
     * Elimina la carpeta y devuelve sus apps a la Home (la primera ocupa el lugar de la carpeta).
     * @return cuántas apps no cupieron en la página (siguen instaladas y en el cajón, solo dejan de estar en la Home).
     */
    synchronized int deleteFolder(String id) {
        Folder f = folders.remove(id);
        if (f == null) return 0;
        int lost = 0;
        for (Member m : f.members) {
            int[] free = nearestFree(false, f.page, f.row, f.col);
            if (free == null) {
                lost++;
                continue;
            }
            map.put(m.id, new Instance(m.id, m.key, false, f.page, free[0], free[1]));
        }
        save();
        return lost;
    }

    // ---- Ícono y fondo personalizados de la carpeta -------------------------------
    // Cada método devuelve el archivo que dejó de usarse (para borrarlo) o "" si no había.

    synchronized String setFolderIcon(String id, String file) {
        Folder f = folders.get(id);
        if (f == null) return "";
        String old = f.icon;
        f.icon = file == null ? "" : file;
        save();
        return old.equals(f.icon) ? "" : old;
    }

    /** Fondo con imagen: reemplaza cualquier color o imagen anterior. */
    synchronized String setFolderBgImage(String id, String file) {
        Folder f = folders.get(id);
        if (f == null) return "";
        String old = f.bgImage;
        f.bgImage = file == null ? "" : file;
        f.hasBgColor = false;
        save();
        return old.equals(f.bgImage) ? "" : old;
    }

    /** Fondo de color sólido: reemplaza cualquier imagen o color anterior. */
    synchronized String setFolderBgColor(String id, int color) {
        Folder f = folders.get(id);
        if (f == null) return "";
        String old = f.bgImage;
        f.bgImage = "";
        f.hasBgColor = true;
        f.bgColor = color;
        save();
        return old;
    }

    synchronized String clearFolderBg(String id) {
        Folder f = folders.get(id);
        if (f == null) return "";
        String old = f.bgImage;
        f.bgImage = "";
        f.hasBgColor = false;
        save();
        return old;
    }

    /** Nombres de todos los archivos de imagen que alguna carpeta usa (para limpiar los huérfanos). */
    synchronized Set<String> folderFiles() {
        Set<String> out = new java.util.HashSet<>();
        for (Folder f : folders.values()) {
            if (!f.icon.isEmpty()) out.add(f.icon);
            if (!f.bgImage.isEmpty()) out.add(f.bgImage);
        }
        return out;
    }
}
