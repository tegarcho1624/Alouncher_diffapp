# Alouncher (Fase 7B-1)

Fase 1: grid de 4 columnas, toque = abrir, pulsación larga = menú.
Fase 2: personalización por entrada del launcher (ícono, nombre, ocultar, restaurar).

## Permisos
Ninguno. El selector de imágenes es ACTION_OPEN_DOCUMENT (no pide permisos);
solo se conserva el acceso de lectura a la imagen que tú eliges.

## Datos
SharedPreferences "customizations": clave = "paquete/actividad", valor = JSON
{"n": nombre, "i": uri de la imagen, "h": true si está oculta}.

## Compilar
GitHub › Actions › Build APK. No cambies `applicationId` ni el keystore.

## Fase 3A
- Home (fondo del sistema + cuadrícula 6×4 + dock de 7 espacios) y cajón de apps como capa que sube.
- Una sola Activity: el cajón es una capa de la misma ventana para poder arrastrar a la Home.
- Estado de Home/dock en SharedPreferences "home_layout" (JSON con paquete/actividad y posición).
- Constantes ajustables: HomeStore.HOME_COLS / HOME_ROWS / DOCK_SLOTS y MainActivity.DRAWER_COLUMNS.

## Fase 4D (carpetas: ícono y fondo)
- Menú de la carpeta en la Home: "Cambiar ícono" (selector de imágenes) y "Quitar ícono" (solo si hay uno).
- Botón circular dentro de la carpeta abierta: imagen del dispositivo, color sólido (paleta de 12) o quitar fondo.
- Las imágenes se copian a filesDir/folders (ícono 256×256 PNG, fondo ≤ 1280 px JPEG).
- Persistencia en "home_layout" (por carpeta: "ic", "bi", "bc"). Si el archivo falta: miniaturas / sin fondo.
- Las imágenes de carpetas eliminadas se limpian al iniciar y al eliminar una carpeta.

## Fase 4E-1 (carpetas redimensionables: base)
- Cada carpeta guarda su tamaño en celdas ("sz": "2x3"); la esquina superior izquierda es su fila/columna. Rango 1x1 a 3x3.
- HomeStore trata todas las celdas del rectángulo como ocupadas (apps, arrastre y "lugar libre más cercano" las respetan).
- Las carpetas > 1x1 se dibujan en una capa (FolderLayer) encima de la cuadrícula de la página, con un grid interno
  que se adapta (FolderGridView), el nombre debajo y el fondo personalizado detrás.
- Estrategia de espacio (opción B): solo crece sobre celdas libres; si no cabe no cambia nada y avisa.
- "Redimensionar" abre un selector provisional de ancho/alto; el marco con puntos llega en la 4E-2.

## Ícono personalizado en carpetas redimensionadas
- Con ícono personalizado, la carpeta (a cualquier tamaño) muestra solo la imagen, recortada tipo "cover" a todo su rectángulo;
  las apps solo se ven al abrirla. Sin ícono: grid interno con el fondo personalizado detrás.
- Los íconos nuevos se guardan con su proporción original (lado largo ≤ 512 px) para no verse borrosos al ampliarse.

## Fase 4E-2 (modo Redimensionar interactivo)
- "Redimensionar" en el menú de la carpeta pone una capa (FolderResizeOverlay) encima de la Home: fondo atenuado,
  marco dorado alrededor de las celdas de la carpeta y 4 puntos (centro de cada borde). Botón "Listo" arriba.
- Arrastrar un punto cambia el tamaño en tiempo real (el borde sigue al dedo, ajustado a la cuadrícula), entre 1x1 y 3x3,
  sin salir del grid y solo sobre celdas libres (se detiene en la primera celda ocupada). Cada cambio se guarda.
- Salir: tocar fuera de la carpeta, "Listo" o el botón atrás.

## Fase 5A (buscador de apps en el cajón)
- Campo "Buscar apps" en el encabezado del cajón (entre el título y "Cerrar"), con el estilo de los demás campos (input_bg).
- Filtra en tiempo real con "contiene", sin distinguir mayúsculas, en el nombre original y en el personalizado.
- Las apps ocultas nunca aparecen en los resultados; la celda "Apps ocultas" se esconde mientras hay búsqueda.
- Sin coincidencias: mensaje "No se encontraron apps". Al borrar el texto vuelve la lista completa.
- Al cerrar el cajón el campo se vacía y el teclado se baja (al abrirlo siempre está vacío).
- MainActivity usa windowSoftInputMode="stateHidden|adjustResize" para que el teclado no tape las apps.
- El campo es un NoDragEditText: rechaza arrastres. Un EditText normal acepta cualquier arrastre y bloqueaba soltar una app del cajón en la Home.

## Fase 6A (panel de Widgets, solo visual)
- "Widgets" del modo edición abre WidgetsActivity (antes ComingSoonActivity, que sigue en el proyecto sin usarse).
- Izquierda (~30 %): buscador "Buscar" (contiene, sin distinguir mayúsculas, solo por nombre de app) y lista de apps con widgets
  (ícono, nombre, "N widgets"). Derecha (~70 %): vista previa del widget elegido, "Nombre - A × B" y, si la app tiene varios,
  carrusel deslizable con el mismo indicador de segmentos de la Home (sin casita).
- Datos: AppWidgetManager.getInstalledProviders(), agrupados por paquete; solo widgets de pantalla de inicio. Sin AppWidgetHost ni binding.
- Tamaño en celdas: (dp + 30) / 70 con minWidth/minHeight (llegan en px, se pasan a dp); en Android 12+ se usa targetCellWidth/Height si existe.
- Vista previa: previewImage del widget; si no tiene, el ícono de la app y "Sin vista previa". Se dibuja a tamaño real (sin ampliar; solo se reduce si no cabe).
- Sin permisos nuevos y sin cambios en la Home, el cajón ni las carpetas. Colocar el widget en la Home llega en la 6B.

## Fase 6B-1 (colocar un widget en la Home)
- En el panel de Widgets, mantener presionada la vista previa cierra el panel y coloca el widget en la Home:
  primer espacio libre empezando por la página actual (luego las siguientes y después las anteriores).
- El tamaño en celdas sale de minWidth/minHeight del widget, medido contra las celdas reales de la Home.
- Vinculación: `AppWidgetHost` (id 1024) en MainActivity. `BIND_APPWIDGET` está declarado, pero Android no lo
  concede a launchers normales: la primera vez sale el diálogo del sistema (ACTION_APPWIDGET_BIND).
  Los widgets con pantalla de configuración la abren antes de colocarse.
- Las celdas del widget cuentan como ocupadas en HomeStore (apps y carpetas no se colocan encima).
- Redimensionar: ver 6B-4. Quitar: ver 6B-5. (Guardar: ver 6B-3; mover: ver 6B-2.)

## Fase 6B-3 (persistencia de widgets)
- Los widgets colocados se guardan en "home_layout" (clave "layout3", lista "widgets"): {"i": appWidgetId, "pk": proveedor "paquete/clase",
  "p": página, "r": fila, "c": columna, "w": ancho, "h": alto en celdas}. Se guardan al colocarlos y al eliminar su página.
- Ya no se llama a AppWidgetHost.deleteHost(): el sistema conserva los ids del host 1024 y se reutilizan al reabrir (y tras reiniciar).
- MainActivity.restoreWidgets() (en onCreate, antes de dibujar la Home): por cada widget guardado comprueba que el id siga vinculado
  (getAppWidgetInfo) y que su proveedor siga instalado (getInstalledProviders) y recrea la vista con createView(). Cualquier fallo
  (app desinstalada, vínculo perdido, excepción) quita su registro y libera su id, sin avisos; sus celdas quedan libres.
- HomeStore lee los widgets después de apps y carpetas; los que ya no caben o están en una página inexistente se descartan.
- startListening() en onStart y stopListening() en onStop (ya estaban de la 6B-1).

## Fase 6B-2 (mover widgets)
- Mantener presionado un widget de la Home abre su menú (mismo estilo que los de apps y carpetas): "Mover", y "Redimensionar" y
  "Eliminar" atenuados que avisan "Próximamente" (llegan en 6B-4 y 6B-5).
- Para detectar la pulsación larga antes de que el widget se quede el toque, cada widget es una WidgetHostView (AppWidgetHostView
  propia, creada por el AppWidgetHost de MainActivity en onCreateView). Un toque corto o un arrastre normal siguen llegando al widget.
- "Mover" pone una capa (WidgetMoveOverlay, como la de Redimensionar carpetas): fondo atenuado y marco dorado sobre el widget.
  Al arrastrarlo, una copia con sombra sigue al dedo y la celda de destino se resalta (dorado = cabe, rojizo = no cabe).
- Solo sobre celdas libres (opción B): HomeStore.canMoveWidget(id, página, fila, columna) ignora las celdas del propio widget y rechaza
  apps, carpetas, otros widgets y todo lo que se salga del grid. Mismo tamaño en celdas.
- Soltar en lugar válido: la copia se asienta en la celda y HomeStore.moveWidget() guarda la posición (misma lista "widgets" de la 6B-3).
  Soltar en lugar inválido: la copia vuelve a su sitio con una animación corta y se sale del modo. Tocar fuera o "atrás" cancelan.
- El movimiento es dentro de la página que se está viendo (no cambia de página al llegar al borde).

## Fase 6B-4 (redimensionar widgets)
- El menú del widget ya activa "Redimensionar" (queda "Eliminar" atenuado, 6B-5). Reutiliza la capa de las carpetas
  (FolderResizeOverlay): fondo atenuado, marco dorado, 4 puntos en el centro de cada borde, botón "Listo". Salir: tocar fuera
  del widget, "Listo" o atrás. Solo se añadió setHandles() a la capa para ocultar los puntos de un eje que no se puede cambiar.
- Arrastrar un punto cambia el tamaño en tiempo real, celda por celda y ajustado a la cuadrícula (MainActivity.dragWidgetResize,
  misma mecánica que dragResize de carpetas). Opción B: solo crece sobre celdas libres y se detiene en la primera ocupada
  (HomeStore.canPlaceWidget). Cada cambio se guarda al momento con HomeStore.resizeWidget() en "home_layout" ("widgets": w, h, r, c).
- Límites (MainActivity.computeWidgetLimits, al entrar en el modo), en celdas, con AppWidgetProviderInfo:
  mínimo = minResizeWidth/Height (si no hay o superan a minWidth/Height, estos); máximo = maxResizeWidth/Height (Android 12+; en versiones
  anteriores y en widgets que no lo declaran, el grid 6x4); mínimo absoluto 1x1. Los límites nunca excluyen el tamaño actual.
  AppWidgetProviderInfo NO tiene "maxWidth/maxHeight": existen solo maxResizeWidth/Height (API 31).
- resizeMode del proveedor: sin RESIZE_HORIZONTAL el ancho queda fijo; sin RESIZE_VERTICAL, el alto. Si no se puede cambiar ningún eje
  no se entra en el modo y avisa "Este widget no se puede redimensionar".
- El widget recibe su nuevo tamaño por trackWidgetSize (ya existía): al cambiar su layout llama a updateAppWidgetSize(min=max=tamaño en dp),
  así el proveedor puede redibujar su contenido para ese tamaño.
- Sin cambios en colocar (6B-1), mover (6B-2) ni persistencia (6B-3): el formato guardado es el mismo.

## Fase 6B-5 (eliminar widgets)
- "Eliminar" del menú del widget abre la misma hoja de confirmación que al eliminar una carpeta: "¿Eliminar widget?" /
  "El widget se quitará de la pantalla de inicio." con "Eliminar" (rojo) y "Cancelar" (MainActivity.confirmDeleteWidget).
- Cancelar no cambia nada. Eliminar (MainActivity.deleteWidget): HomeStore.removeWidget() borra el registro de "home_layout"
  (sus celdas quedan libres y no se restaura al reiniciar) y renderHome() llama a pruneWidgetViews(), que saca la vista de la Home
  y libera el id con AppWidgetHost.deleteAppWidgetId(). No se añadió código de host nuevo: es el mismo camino que ya usaba
  la eliminación de páginas con widgets.

## Fase 7A (gesto: deslizar hacia arriba abre el cajón)
- En la Home, deslizar el dedo hacia arriba abre el cajón de apps con la misma animación que el botón del dock
  (el gesto llama a MainActivity.openDrawer(), igual que el botón, que sigue funcionando igual).
- Se detecta en MainActivity.dispatchTouchEvent (llega antes que las páginas, los widgets y las apps) y solo OBSERVA:
  nunca consume el toque, así que no interfiere con el desplazamiento de páginas, pulsaciones largas ni arrastres.
  Se calcula la diferencia entre ACTION_DOWN y ACTION_UP (trackSwipeUp).
- Condiciones: subir al menos el 20 % del alto de la Home y que el movimiento sea predominantemente vertical
  (subir >= 1.5 × lo desplazado de lado). Si las páginas se están desplazando de lado, no se abre.
- No se activa: si el dedo bajó sobre un widget, con dos dedos, si el toque se cancela (empieza un arrastre de app o carpeta),
  con el cajón abierto, en modo edición, en los modos Mover/Redimensionar o con un menú abierto.
- Constantes ajustables (MainActivity): SWIPE_UP_MIN_FRACTION (0.20), SWIPE_UP_VERTICAL_RATIO (1.5) y SWIPE_UP_ZONE_TOP
  (0 = el gesto puede empezar en cualquier parte de la Home; 0.5 = solo desde la mitad inferior).
- Funciona en vertical y horizontal (el alto se lee de la Home en cada gesto; la actividad no bloquea la orientación).
- Sin permisos nuevos y sin cambios en cajón, dock, páginas, carpetas, widgets ni fondos.

## Fase 7B-1 (color de acento: selector y Home)
- "Configuración de pantalla principal" tiene una fila nueva, "Color de acento", debajo de la disposición: a la derecha un círculo con el
  color actual (por defecto el dorado). Tocar la fila abre una hoja inferior (BottomSheet, mismo patrón que el color de fondo de carpetas)
  con 12 círculos (Dorado, Amarillo, Naranja, Rojo, Rosa, Morado, Azul, Cian, Turquesa, Verde, Lima, Plata). Al elegir uno se guarda,
  la hoja se cierra y el círculo de la fila se actualiza.
- ThemeUtils (nueva): accent() / accentHi() / setAccent() / version() / cellBg() y la paleta ACCENT_COLORS. Se guarda como entero ARGB en
  SharedPreferences "settings", clave "accent" (junto a la fuente). Sin valor guardado: dorado (#C9A24B; accentHi = #E2BD67, igual que
  gold / gold_hi). Para otros colores, accentHi es el acento mezclado un 22 % con blanco.
- Se aplica al volver a la Home (MainActivity.onResume compara ThemeUtils.version(), igual que la fuente) con MainActivity.applyAccent():
  segmento activo de los indicadores de páginas (Home y modo edición, PageIndicator.setAccent), ícono de cuadrícula del botón del dock
  (tinte de la 2ª capa de ic_drawer_btn), resaltado de celdas y dock al arrastrar (cellBg() en lugar de cell_bg.xml; PageVH.cellAccent
  repinta cada página al volver a enlazarla) y casita de las tarjetas del modo edición.
- Sin cambios en menús, hojas, cajón, carpetas, widgets, fondos ni fuentes: siguen leyendo gold / gold_hi de colors.xml (parte 2).
- cell_bg.xml sigue existiendo: solo lo usa ya WidgetsActivity.
