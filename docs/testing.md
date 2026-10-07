# Pruebas y CI

```bash

./gradlew :26.3:test                         # unitarias (JUnit)

./gradlew :26.3:runGameTest                  # gametests de servidor

./gradlew :26.3:runGameTest -PcompatPack     # con el modpack de compatibilidad (Lithium, FerriteCore, Placeholder API)

./gradlew :26.3:runClientGameTest            # cliente: capturas en versions/26.3/build/run/clientGameTest/screenshots/

./gradlew :26.3:runClientGameTest -Pxaero    # cliente con Xaero's Minimap + World Map

./gradlew :26.3:publishToMavenLocal          # artefacto socialmod-api

```

Cambia `26.3` por `26.1.2` o `26.2` para las otras versiones. Las pruebas de cliente abren una ventana real de

Minecraft; mientras carga con renderizado por software Windows puede mostrarla como "No responde" unos segundos.

## Unitarias (`src/test`) — 35 pruebas

- `SafeMarkdownTest`: negrita, cursiva, código, escapes, menciones, enlaces y que el JSON de componentes nunca se interpreta.

- `TextSanitizerTest`: controles, códigos `§`, caracteres de dirección e invisibles, espacios y textos de 1 MB.

- `ConversationIdTest`: claves simétricas, ida y vuelta, rechazo de basura y rutas, orden de roles.

- `SecurityTest`: rate limit por jugador, anti-spam (velocidad, repetición, silencio automático), filtro (palabras, regex inválidas, bloqueo).

- `StorageTest`: ida y vuelta comprimida, cuarentena de archivos corruptos, path traversal, retención y paginación.

- `PreviewAndIconTest` (v0.2.0): `[coords]`/`[item]` resueltos en la vista previa de toasts, `[CORDS]`/`[ITEM]` en

  mayúsculas, marcadores sin adjunto, recorte, lista blanca y unicidad de emblemas.

- `JdbcStorageTest` (v0.2.0): backend H2 (upsert, borrado, claves), importación desde `file`, auditoría y exportaciones

  en archivos, prefijo de tabla malicioso y backend desconocido.

## Gametests de servidor (`src/gametest`) — 19 pruebas

Mensajes guardados y no leídos; bloqueos; privacidad "solo amigos"; escribir en un privado ajeno o en un grupo

inexistente; textos enormes y basura; silencio automático por spam; filtro en modo bloqueo; flujo de amistad;

solicitudes de bloqueados descartadas; roles, permisos y canales por rol; herencia del liderazgo y disolución de grupos

vacíos; parties temporales; adjuntos `[item]`/`[coords]` generados por el servidor; borrado de datos.

Nuevas en la v0.2.0:

- `statusChangeIsVisibleInSnapshot` (error 1), `groupStyleIconAndNametagEntry` (error 3),

  `toastPreviewResolvesPlaceholders` (error 4).

- `loadTwoHundredPlayers`: carga con 200 jugadores en un grupo, 1000 mensajes de grupo y 200 privados. Resultado de

  referencia: ~270–380 ms en las tres versiones (presupuesto del test: 20 s).

## Cliente

Crea un mundo, comprueba el handshake real, crea un grupo con canales y mensajes por comandos, captura el panel, el

perfil, los ajustes del grupo y del jugador, los toasts y Quick-Reply. Desde la v0.2.0 además:

- Rota el estado (Ausente → No molestar → En línea) y espera a verlo en el snapshot (error 1).

- Comprueba que la vista previa de la conversación muestra `x: …` y no `[coords]` (error 4).

- Cambia emblema y color con `/g icon` y `/g color #3366FF` y espera la etiqueta nueva (error 3); captura el selector

  de estilo y la pantalla de crear grupo.

- Crea una party, lanza un ping y lo captura en el HUD.

- Con `-Pxaero`: crea un waypoint real en Xaero's Minimap y lee la posición del minimapa.

Después reinicia el mundo y verifica que el grupo, el mensaje fijado y el historial persistieron.

## CI

Ver la skill `skills/fabric-mod-github-ci`. Cada push compila y prueba las 3 versiones (con y sin compat pack) y ejecuta

el test de cliente con renderizado por software. Un push a `main` con todo en verde publica la pre-release `dev`; un tag

`vX.Y.Z` crea la release (y publica en Modrinth/CurseForge si están configurados `vars.MODRINTH_ID`/`vars.CURSEFORGE_ID`,

y la API en Maven si está `vars.MAVEN_URL`).

Xaero no va en el compat pack del servidor: su librería (`xaerolib`) falla al arrancar el servidor de gametests

(`Registry is not frozen yet!`), un problema de Xaero en ese entorno. Se prueba en el cliente con `-Pxaero`.

El test de cliente con `-Pxaero` es **diagnóstico manual**: con Xaero, el arnés de pruebas de Fabric a veces se

interbloquea al salir del mundo (ver el error 10 del [registro de errores](registro-de-errores.md)); el vigilante del test

vuelca las pilas (`HANGDUMP` en el log) si salir tarda más de 15 s. En el juego real (`runClient -Pxaero`) entrar, crear un

waypoint, salir y cerrar funciona sin bloqueos.

## Diseños de serie y protocolo 4

`-PfancyMenu` comprueba ambos diseños, sus fondos transparentes, sus HUD, perfiles y exportaciones. `-PfancyMenu -PfancyOnly` verifica el fallback sin SpiffyHUD. `-PfancyMenu -PseriesPack=christmas` y `-PfancyMenu -PseriesPack=dedsafio` arrancan con el ZIP publicado de cada diseño. Ejecutar cada perfil para 26.1.2, 26.2 y 26.3.

Las capturas `series_<estilo>_<idioma>_panel` y `_hud` proceden del juego real. Los ZIP reproducibles se escriben en `versions/<mc>/build/run/clientGameTest/advanced-export/`. Copiarlos a `docs/examples/series/` después de pasar las pruebas.

`SnapshotSyncTest` verifica deltas, fragmentos UTF-8, aplicación atómica, recuperación y límites. `PersistenceRecoveryTest` verifica lectura fallida, reintento y drenaje de índices grandes. El benchmark de snapshots mide 50 y 200 destinatarios; sus tiempos son diagnósticos de CI y no equivalen a conexiones de red reales.

La validación de 0.6.0 está en [Implementación del plan](implementacion-0.6.0.md). El cliente también deja pendientes un guardado y una activación y comprueba que el cierre los termina.
