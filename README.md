# Alouncher (Fase 6B-2b)

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
- Todavía SIN redimensionar (6B-4) ni quitar (6B-5). (Guardar: ver 6B-3; mover: ver 6B-2.)

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
- Cambio de página al mover: si el dedo se queda ~0,5 s a menos de 44 dp del borde izquierdo o derecho, la Home pasa a la página
  vecina (y sigue cada 0,8 s mientras siga en el borde); una franja dorada tenue avisa. El widget se puede soltar en cualquier página
  existente (no crea páginas nuevas). Si se suelta en un lugar inválido estando en otra página, la Home vuelve a la página de origen.
- La cuadrícula del modo Mover se calcula con el carrusel y el relleno de página (no con la página visible), para no moverse mientras se desliza.
