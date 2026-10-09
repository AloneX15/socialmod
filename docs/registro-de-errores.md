# Registro de errores y soluciones (v0.2.0)

## Catálogo FancyMenu 0.8.0: foco e interacción de elementos

- **Síntoma:** un campo añadido al layout no recibía escritura desde el panel principal.
- **Causa:** `AbstractElement` no es enfocable por defecto y su `isMouseOver` devuelve falso. Además, los controles
  registrados al inicializar la capa pueden desaparecer cuando la pantalla rehace sus widgets.
- **Solución:** un adaptador de entrada nativo mantiene foco, narración y límites del elemento; se comprueba su
  registro mediante la API de pantallas de Fabric y se utiliza el hitbox del elemento. Los formularios reutilizan
  los controles nativos y transforman sus coordenadas sin modificar permanentemente su distribución.
- **Prueba:** guardar y recargar un layout real, hacer clic y escribir mediante la pantalla principal, verificar
  el borrador compartido y restaurar/reactivar estilos.


## Personalización 0.7.0: escala del estandarte

- **Síntoma:** la vista previa permanecía pequeña al ampliar el control.
- **Causa:** el renderizador GUI de estandartes de Minecraft utiliza una escala interna fija.
- **Solución:** dibujar las caras de las texturas nativas y sus capas teñidas dentro del tamaño del widget,
  conservando la proporción 1:2. Los identificadores y colores se almacenan en caché.
- **Comprobación:** capturas del editor y del selector del tag en las pruebas de cliente; tamaños configurables
  sin cambiar el estandarte guardado.


Errores encontrados al corregir `SOCIALMOD_ERRORES.md`, completar las fases pendientes y añadir la compatibilidad con
Xaero's World Map / Minimap. Cada entrada indica el síntoma, la causa y cómo se solucionó.

## 1. El estado no "rota" al pulsarlo

- **Síntoma:** en Ajustes, el botón de estado (En línea → Ausente → No molestar → Invisible) se quedaba igual.
- **Causa:** el servidor guardaba el estado pero no reenviaba el snapshot (`ServerNet` → `SET_STATUS`), así que el
  botón leía el valor viejo y volvía a pedir el mismo "siguiente" estado.
- **Solución:** `SET_STATUS` ahora envía el snapshot; además el cliente aplica el cambio al instante
  (`ClientState.updateSelf`) en el estado y en todas las opciones del perfil (privacidad, dimensión, leído,
  "escribiendo"). Prueba: `statusChangeIsVisibleInSnapshot` y el test de cliente.

## 2. Textos con formato inconsistente

- **Síntoma:** cabeceras en MAYÚSCULAS en el JSON, pestañas en frase normal, "TAG", títulos a distintas alturas,
  colores de grupo oscuros ilegibles.
- **Solución:** guía de estilo en `Ui` (títulos `Ui.title`, cabeceras en mayúsculas por código con `Ui.upper`,
  `Ui.readable` para colores de jugadores, constantes de color). Los 4 idiomas en minúscula normal y alineados.

## 3. Etiqueta del grupo encima/delante del nombre y sin personalización

- **Causa:** el nametag solo se podía modificar como prefijo sin mixins.
- **Solución:** se usa `scoreText` del estado de render (la línea "belowName" que vanilla dibuja **debajo** del nombre,
  comprobado en 26.1.2, 26.2 y 26.3): sin mixins y compatible con otros mods. Emblemas (`GroupIcon`), selector visual
  de color con vista previa (`TagStyleScreen`) y rol en la etiqueta.

## 4. `[CORDS]` / `[ITEM]` sin procesar en las notificaciones

- **Causa:** la vista previa de toasts, lista de conversaciones y reportes usaba el texto crudo.
- **Solución:** `MessageFormatter.preview(texto, adjuntos, max)` resuelve los marcadores (`x: 120, z: -450`, nombre
  del ítem) y `normalizeTokens` acepta mayúsculas y la errata `[cords]`.

## 5. `/g color #RRGGBB` no funcionaba

- **Síntoma:** el comando rechazaba `#3366FF` aunque el mensaje de error pedía ese formato.
- **Causa:** argumento `StringArgumentType.word()`, que no admite `#`.
- **Solución:** `greedyString()`.

## 6. La tecla de ping (G) chocaba con teclas de vanilla 26.3

- **Síntoma:** aviso de conflicto con "Acciones rápidas" (G).
- **Solución:** la tecla por defecto pasa a **J**. Las combinaciones F3+ ya no cuentan como conflicto.

## 7. Quick-Reply (Y) chocaba con Xaero's Minimap ("Ajustes del minimapa", Y)

- **Solución:** `SocialKeys.checkConflicts` recoloca la tecla de SocialMod a una libre **solo si sigue en su valor por
  defecto** y avisa en el chat; nunca toca la tecla del otro mod. Si el jugador la cambió a mano, solo avisa.

## 8. Xaero's Minimap/World Map hacían caer el servidor de gametests

- **Síntoma:** `IllegalStateException: Registry is not frozen yet!` en `xaerolib` al arrancar el servidor de pruebas.
- **Causa:** la librería de Xaero no está pensada para el servidor de gametests (fallo de Xaero, no de SocialMod).
- **Solución:** Xaero se prueba solo en el cliente con `-Pxaero` (`runClientGameTest -Pxaero`), fuera del compat pack
  del servidor.

## 9. Waypoint de Xaero: método equivocado

- **Síntoma:** `NoSuchMethodException: WaypointSession.saveWorld(MinimapWorld)`; la integración se desactivaba sola.
- **Causa:** `saveWorld` está en `MinimapWorldManagerIO` (`MinimapSession.getWorldManagerIO()`), no en `WaypointSession`.
- **Solución:** corregido en `MapCompat` y comprobado con javap en las 3 versiones de Xaero.

## 10. Minecraft "no responde" y se cierra al salir del mundo (pruebas con Xaero)

- **Síntoma:** en `runClientGameTest -Pxaero` la ventana de Minecraft se queda en "No responde" al salir del mundo y el
  proceso termina con `0xCFFFFFFF` (el código que pone Windows al cerrar una ventana colgada). Parecía causado por el
  waypoint.
- **Experimentos** (cada fila es una tanda de ejecuciones del test de cliente con Xaero):

  | Variante | Resultado |
  |---|---|
  | Waypoint creado por SocialMod (`MapCompat`) | 2 bien, 3 colgadas |
  | Waypoint creado con la función propia de Xaero (`TemporaryWaypointHandler`) | 1 bien, 3 colgadas |
  | **Sin ningún waypoint** | 2 bien, 1 colgada |
  | Sin Xaero | siempre bien |

  Conclusión: el waypoint no influye; el cuelgue aparece con Xaero instalado en el entorno de pruebas.
- **Causa (volcado de hilos automático en el test, `HANGDUMP` en el log):** interbloqueo dentro del arnés de pruebas de
  Fabric (`fabric-client-gametest-api`). El hilo de render está en `IntegratedServer.halt()` esperando al servidor; el hilo
  del servidor y el "Test thread" están parados en `ThreadingImpl.enterPhase` (un `Phaser` del arnés) esperando una fase
  que nunca llega. Los hilos de SocialMod (`SocialMod-IO`, `SocialMod client cache`) estaban libres. Ese código de
  desconexión del arnés (`deferDisconnect`) **solo existe en las pruebas automáticas**.
- **Comprobación en el juego real:** cliente normal (`runClient -Pxaero`, sin arnés), entrar al mundo, crear un waypoint
  con SocialMod, "Guardar y salir" y cerrar el juego, todo automático: **5 de 5 sin bloqueo** (sale del mundo en ~1 s).
- **Solución:** no hay nada que corregir en SocialMod. El test de cliente lleva un vigilante (`startHangWatchdog`) que
  vuelca las pilas si salir del mundo tarda más de 15 s, y el test con `-Pxaero` queda como diagnóstico manual (no va en
  la CI). Las ventanas "No responde" que aparecían durante el trabajo eran esas pruebas automáticas.

## 11. Open Parties and Claims no cargaba en las pruebas

- **Síntoma:** `requires version 26.2.0 or later of forgeconfigapiport, which is missing!` aunque se añadió la dependencia.
- **Causa:** en Modrinth, Forge Config API Port usa el mismo número de versión para Fabric y NeoForge, y el Maven de
  Modrinth resolvía el jar equivocado.
- **Solución:** se usa el **id de la versión Fabric** (`jUe0ucoE`, `rSd3GiG8`, `JpKvrr9J`) en `stonecutter.properties.toml`.

## 12. Diferencias de API entre 26.1.2 y 26.3

- `InputConstants.Type.KEYSYM` (26.1.2) pasó a `KEYBOARD` (26.3): las teclas libres se buscan con
  `InputConstants.getKey("key.keyboard.j")`, igual en todas.
- Los ítems de colores (`Items.WHITE_WOOL`, `LIME_DYE`...) pasaron a `ColorCollection` en 26.3: el menú `/social` los busca
  por id en el registro (`BuiltInRegistries.ITEM`).
- `RenderPipeline` cambió de paquete en 26.3: se usa `RenderPipelines.GUI_TEXTURED` sin importar el tipo.

## 13. La API de JourneyMap solo está publicada como SNAPSHOT

- **Solución:** Gradle extrae el jar estable de la API que va dentro del mod (`META-INF/jars/journeymap-api-*.jar`) y
  compila contra él (`extractJourneyMapApi`). En ejecución la aporta JourneyMap.

## 14. Teclas que chocaban con el propio Minecraft 26.3

- Vanilla 26.3 usa más letras que antes (Acciones rápidas en G, guardar/cargar barras en C/X...). Con Xaero, las únicas
  letras libres eran J, K y R; por eso el ping va en J y las combinaciones F3+ no cuentan como conflicto.

## 15. `/socialmod data delete` y la exportación bloqueaban los chats en servidores grandes

- **Síntoma:** en la build completa, tests de mensajes fallaban con "no guardado on tick 62" de forma intermitente.
- **Causa:** `SocialStorage.editAllConversations` leía y reescribía **todas** las conversaciones del disco en una sola
  tarea del único hilo de E/S. Con 2.973 conversaciones (el mundo de pruebas local), cualquier carga de un chat esperaba
  varios segundos detrás. En un servidor real pasaría lo mismo con cada borrado o exportación de datos.
- **Solución:** el recorrido va por **lotes de 16 archivos** que se vuelven a encolar, así las cargas de los jugadores
  pasan entre lote y lote. La exportación usa un aviso de fin (`onDone`) en vez de depender del orden de la cola.

## 16. Tests que se pisaban entre sí

- `uniqueTag()` usaba los primeros dígitos de `nanoTime` en base 36, que solo cambian cada ~2 s: dos tests creando grupos
  a la vez chocaban con "etiqueta en uso". Ahora es aleatoria y única por ejecución.
- El test de carga cambiaba la config global (sin anti-spam) durante varios ticks mientras otros tests del lote corrían a
  la vez: ahora la restaura en el mismo tick.
- Los tests miraban solo la caché de conversaciones; las 200 conversaciones del test de carga podían expulsar las suyas.
  Ahora, si no está en caché, se vuelve a cargar del disco. El test de carga además comprueba que sus 200 privados se
  guardan y los borra al acabar.
- Resultado: 3 builds completas seguidas en verde (3 versiones × 22 gametests + 35 unitarias) y el compat pack y el test
  de cliente en verde en las 3 versiones.

## Nametag con varios grupos (0.3.0)

- Sintoma: la identidad visible de un jugador perteneciente a varios grupos dependia del grupo principal y su fallback.
- Solucion: TEAM persistente elegido una vez, independiente de los grupos; sin fallback a grupos sociales.
- Regresion: `teamIdentitySurvivesMultipleGroupsAndArchiveRestore`, permisos C2S, persistencia file/JDBC y reinicio real del cliente.
- UI: corregidas las pestanas fuera del marco compacto y la inclusion accidental de campos estaticos en el inspector.

### Controles TEAM superpuestos y estilo expuesto

El selector colocaba Crear, Elegir y Estilo en la misma fila, y mostraba Estilo a jugadores sin permisos. Se eliminaron los controles administrativos del modo jugador y se repartieron Crear/Elegir en dos mitades. La captura del selector y las acciones manipuladas del gametest verifican interfaz y permisos.

### La tecla del panel no lo cerraba

Minecraft entrega las pulsaciones a la pantalla abierta, por lo que el manejador del tick no recibía la tecla de apertura. SocialScreen ahora compara KeyEvent con la asignación actual y ejecuta onClose; la ruta del tick también alterna abrir/cerrar. El gametest de cliente abre y cierra el panel con K y con H reasignada, conservando la configuración original.

### Restaurar mantenía el borrador antiguo a la vista

Tras restaurar, el servidor actualizaba el diseño, pero el editor seguía mostrando su preview local anterior. La confirmación ahora cierra el editor y vuelve al panel con el diseño del servidor. El gametest publica una plantilla distinta y restaura Navidad mediante los botones reales, comprobando la fuente y la pantalla activa.

### Listas movidas con FancyMenu no abrían perfiles (0.4.0)

La prueba mostraba coordenadas y filas correctas, pero el clic se consumía en la ruta general de controles antes de llegar a la lista. SocialScreen ahora conserva la prioridad de los controles interactivos y dirige los clics al bloque visible superior usando sus límites finales. La prueba mueve y redimensiona Jugadores sobre Conversaciones, abre un perfil y comprueba que una lista oculta no consume la rueda.

### Texto de filas recortado dos veces

El recorte usaba coordenadas absolutas después de trasladar la matriz de dibujo, desplazando el área visible por segunda vez. El recorte ahora se configura antes de trasladar la matriz y se cierra después de restaurarla. Las capturas muestran nombres y horas en el panel real y el editor de filas.

### Controles tapados por la barra de FancyMenu

La barra superior cubría controles de SocialMod. La integración usa `CustomizationOverlay.registerOverlayVisibilityController` para ocultarla exclusivamente en esas pantallas; el editor completo mantiene su barra. La prueba también comprueba que los controles del editor de filas caben y no se superponen en una GUI de 427 × 240.

### Apariencia local y caché de tema

Al sustituir un diseño local se podía conservar la paleta anterior en caché. VisualManager invalida el tema al cambiar su fuente de diseño. Restablecer desactiva los layouts del ejemplo, guarda filas básicas y retira la apariencia local del siguiente arranque. Las escrituras y el ZIP se realizan fuera del hilo de render.

### HUD visible en el editor pero ausente en partida

SpiffyHUD sustituye temporalmente la pantalla activa por su overlay durante el render. Comprobar si había una pantalla abierta dentro de ese render ocultaba nuestros componentes y a la vez retiraba el HUD nativo. Ahora se conserva el estado real de la pantalla al final del tick del cliente y se aplica la misma condición de visibilidad al reemplazo y al render. La prueba verifica que el componente social haya dibujado datos durante la partida, además de capturar el resultado.

### FancyMenu sin SpiffyHUD desactivaba la integración

El verificador de clases JVM resolvía referencias a SpiffyOverlayScreen al cargar el backend de FancyMenu. Las llamadas que utilizan tipos de SpiffyHUD se movieron a un backend separado, cargado exclusivamente cuando el mod está instalado. Los perfiles de cliente verifican FancyMenu solo, ambos editores y la ausencia de ambos.

### Botones del ratón en Minecraft 26.3

Minecraft 26.3 utiliza valores SDL para el ratón: izquierda es 1 y derecha es 3; las versiones anteriores usaban 0 y 1. Comparar con 1 confundía selección y clic derecho, y las pruebas con clic 0 no reproducían un clic físico. El panel y el editor usan ahora `InputConstants.MOUSE_BUTTON_LEFT/RIGHT`. El gametest usa la misma constante oficial y verifica tanto seleccionar una fila como pulsar Volver en un perfil.

### Recursos de filas durante el arranque de un modpack

Las filas importadas pueden cargarse antes de terminar la primera recarga de recursos de Minecraft. Validarlas en ese momento podría descartar fuentes que todavía no estaban disponibles. La validación de recursos espera a la primera recarga; posteriores cargas se validan inmediatamente. Los perfiles actuales `-PfancyMenu -PseriesPack=clean` y `-PfancyMenu -PseriesPack=dedsafio` arrancan con el ZIP documentado instalado y exigen que sus filas y apariencia se carguen.

### Exportación de configuraciones ajenas a la serie (0.5.0)

El exportador anterior copiaba toda la configuración de FancyMenu, incluidas preferencias y layouts de otras pantallas. Ahora usa selección explícita de layouts, incorpora únicamente sus recursos locales referenciados y las filas/apariencia, y añade un manifiesto validado. Los tests comprueban que no salgan preferencias ni layouts no seleccionados, y que se rechacen rutas externas y dependencias omitidas.

### Cambio de perfil y recuperación del diseño previo (0.5.0)

Sobrescribir archivos sin conservar su origen puede perder el diseño anterior; restaurar un layout retirado como activo puede además superponerlo al nuevo. Cada activación respalda los archivos afectados y el estado del perfil, conserva el contenido original de archivos existentes y retira o desactiva los layouts que dejan de usarse. La restauración recupera los bytes anteriores y elimina archivos nuevos. Los tests cubren colisiones, perfiles sin filas locales, layouts retirados y restauración tras reiniciar el servicio; el gametest recarga los editores reales, activa/importa y restaura perfiles.

## Revisión de rendimiento, seguridad y compatibilidad (2026-10-07)

### Permisos obsoletos y autorización antes de una carga asíncrona

La caché propia retenía permisos cinco segundos y algunas operaciones reutilizaban autorizaciones anteriores a cargar el historial. Se consulta al proveedor en cada acción y se revalidan privacidad, pertenencia, silencio y permisos al completar la carga. Ante una excepción del proveedor se deniega la acción. Las regresiones incluyen revocación, fallo del proveedor, mensaje pendiente al cerrar la privacidad y un perfil con LuckPerms real, contextos y metadatos.

### Escrituras perdidas tras un fallo y cierre de backend durante E/S

El estado dejaba de estar marcado como pendiente antes de saber si el backend había escrito; el cierre por timeout podía cerrar una conexión JDBC todavía en uso. Se conserva la última copia JSON por documento, se agrupan escrituras, se reintenta y se cierra el backend desde su executor después de las operaciones pendientes. Los tests cubren 10.000 revisiones de un documento, copia inmutable, fallo temporal, recuperación sin otra modificación y cierre interrumpido. Un fallo permanente durante el apagado se registra como datos sin guardar; no se promete persistencia cuando el almacenamiento continúa inaccesible.

### Coste del HUD y aislamiento de los editores

Se creaban filas, mapas y textos en cada frame y se recorrían todos los elementos para cada consulta de reemplazo. Las filas se calculan como máximo una vez por tick, con reutilización cuando no cambian los datos; las alturas se invalidan por anchura, template y revisión de recursos. El índice se actualiza por tick y la visibilidad se consulta durante el render. Una avería de SpiffyHUD ya no desactiva FancyMenu. Los perfiles de cliente verifican render real, reutilización, F1 y recuperación del HUD nativo.

### ZIP y respaldo con nombres ambiguos

Dos rutas distintas podían designar el mismo archivo en Windows; el estado embebido en un respaldo solo se comprobaba por tamaño. Se validan nombres, colisiones de directorios, entradas simbólicas del directorio central ZIP y el estado restaurado antes de escribir. Los modelos y metadatos JSON rechazan una profundidad superior a 64 niveles antes de la deserialización recursiva. Las regresiones comprueban que un rechazo conserva los archivos instalados y que los templates distribuidos siguen siendo válidos.

### Privacidad y límites de trabajo

La última actividad de un amigo invisible podía aparecer en el snapshot aunque su estado figurase como desconectado. Se oculta ese timestamp y se cubre con gametest. Los snapshots se procesan en lotes de veinte destinatarios, se descartan duplicados y se liberan sus cachés al salir. Una conexión de 200 jugadores puede necesitar diez ticks para recibir una actualización global. Se limitan exportaciones y reportes en el servicio común, además del límite global de paquetes.

## 0.6.0: editores, perfiles y sincronización

- Síntoma: cambiar de fila o página perdía campos y ocultaba errores. Causa: reconstrucción de widgets sin validar. Solución: aplicar antes de navegar, conservar entradas inválidas y borradores al redimensionar, limitar todo el historial a 40. Regresión: pruebas reales del editor con campos inválidos, navegación y resize.
- Síntoma: una lectura fallida podía crear una conversación vacía escribible. Solución: no ejecutar acciones ni cachear datos vacíos cuando la lectura falla; permitir reintento. Regresión: `failedReadDoesNotCreateWritableEmptyHistoryAndCanBeRetried`.
- Síntoma: preparación de índices grandes concentrada en un tick. Solución: lotes de 16 registros con drenaje completo al apagar. Regresión: `largeIndexesPrepareOverSeveralTicksAndShutdownDrainsThem`.
- Síntoma: estado grande descartado y panel desactualizado. Solución: protocolo 4, secciones modificadas, fragmentos y aplicación atómica hasta 8 MiB; recuperación con nueva base. Regresión: `SnapshotSyncTest`.
- Síntoma: ZIP aprobado sin comprobar versiones de mods. Solución: predicados de versión en manifiesto y validación previa; aviso para manifiestos antiguos. Regresión: `versionRequirementsAreValidatedAndLegacyManifestsRemainReadable`.
- Síntoma: el ejemplo navideño ocultaba el juego. Solución: dos perfiles sin fondos globales y con paneles semitransparentes. Regresión: `SeriesTemplatesTest` y capturas de FancyMenu/SpiffyHUD en las tres versiones.

## Navidad gráfica: imágenes y perfiles

- **Síntoma:** la guía anterior ofrecía un diseño neutro en lugar de los botones ilustrados solicitados. **Causa:** interpretación incompleta del objetivo visual. **Solución:** Navidad gráfica reutiliza exactamente los seis PNG y ocho iconos adjuntos, con estados, adaptación del centro y transparencia del mundo. **Prueba:** galería en juego, controles pequeños, alto contraste y ZIP con recursos.
- **Síntoma:** FancyMenu se desactivaba mientras una imagen todavía se estaba cargando. **Causa:** una textura asíncrona puede tener un identificador nulo hasta estar preparada. **Solución:** utilizar el PNG incluido como respaldo mientras la copia editable carga. **Prueba:** primera apertura con FancyMenu, instalación y recarga reales.
- **Síntoma:** guardar un perfil tras cambiar de Navidad a Dedsafío podía fallar por imágenes ausentes. **Causa:** se conservaba el layout desactivado, pero se retiraban sus recursos. **Solución:** conservar imágenes asociadas a los ejemplos desactivados, byte por byte, dentro de la operación respaldada. **Prueba:** cambio, exportación, restauración y regresión con bytes binarios.
- **Síntoma:** aparecían cajas vacías en el HUD navideño. **Causa:** se dibujaba el borde antes de comprobar si había filas. **Solución:** omitir componentes vacíos en juego; mantener su vista previa en el editor. **Prueba:** capturas de panel y HUD y comprobación de render de SpiffyHUD.

## Historial y elementos de FancyMenu (2026-10-09)

- El historial respondía a la rueda pero carecía de barra. Ahora permite clic y arrastre; al llegar al inicio
  conserva la paginación y al recibir mensajes conserva la posición de lectura. `HistoryInteractionTests`
  comprueba arrastre al inicio y llegada de mensajes en una conversación larga.
- Los módulos incrustados agrupaban controles y no permitían ocultar sus originales al separarlos.
  El selector muestra nombres traducidos, comparte formulario y oculta únicamente las copias originales;
  `SocialCatalogTests` verifica los controles de todos los módulos, persistencia, campos y acciones.
- El estandarte sin TEAM mostraba un mensaje de contexto. Ahora utiliza un estandarte blanco y no permite edición
  sin equipo/permisos. Se añade clic de edición y acción configurable. El TAG con icono conserva el color del TEAM.
- Los estilos retirados tenían recursos y perfiles de prueba propios. Se eliminan y se restablece el diseño completo
  de sus configuraciones. Tests de migración verifican filas, apariencia, layouts desactivados y archivos conservados.
- En 26.3 el identificador del botón izquierdo cambió. Los nuevos manejadores y sus tests utilizan
  `InputConstants.MOUSE_BUTTON_LEFT` para conservar el clic de estandartes, botones separados y barra en todas
  las versiones. La prueba de Nuevo grupo entra por la pantalla anfitriona y detectó este caso.

## Gestión de TEAM sin destino y errores genéricos (2026-10-09)

- Síntoma: Renombrar y guardar un color respondían «TEAM, nombre, jugador o acción no válidos».
- Causa reproducida: Gestionar abría con `selected` vacío incluso teniendo TEAM, y la navegación desde el catálogo descartaba la selección. Todos los rechazos de la transacción se resumían en una sola clave.
- Solución: conservar la selección o cargar el TEAM local y sus valores; impedir acciones sin destino; devolver una clave específica por rechazo tanto en paquetes como en comandos. El estandarte de FancyMenu explica la falta de equipo o permiso con un aviso.
- Pruebas: `TeamEditingTests` reproduce el destino vacío y comprueba renombrado y color a través de la UI y del servidor; gametest de servidor comprueba rechazos específicos sin mutación. `SocialCatalogTests` pulsa a través de la pantalla, tras cerrar el editor, con anclaje central y sin conversación seleccionada. La regresión adicional retiene deliberadamente la referencia al editor cerrado: reproducía el clic bloqueado y pasa al comprobar la pantalla activa.

## Clic bloqueado por una referencia al editor cerrado (2026-10-09)

- Síntoma reproducido: el estandarte visible en la pantalla normal no responde, aunque el TEAM y los permisos son válidos.
- Causa: `AbstractElement.isEditor()` consulta la instancia estática guardada por FancyMenu; una referencia retenida al editor anterior también se interpretaba como edición activa e impedía registrar o procesar la entrada.
- Solución: comprobar la pantalla activa y reservar la referencia almacenada para las pantallas auxiliares del editor.
- Prueba: `SocialCatalogTests` cierra el editor real, retiene su referencia y pulsa el estandarte desde `SocialScreen`; fallaba con el código anterior y abre `BannerEditorScreen` con la corrección.

## Cierre del mundo de pruebas en CI 26.3 (2026-10-09)

Los perfiles FancyMenu agotaron el tiempo después de las comprobaciones de UI. El volcado mostraba el hilo de render en `IntegratedServer.halt()` y los hilos de servidor y test en la barrera de `ThreadingImpl`, el interbloqueo conocido del arnés de Fabric. El test deja ahora la pantalla y sus hooks de entrada antes de sincronizar el cierre del mundo, manteniendo las comprobaciones de persistencia tras reiniciar.
