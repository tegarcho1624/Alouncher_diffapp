# Alouncher (Fase 3A)

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
