# Registro de errores y soluciones (v0.2.0)

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
