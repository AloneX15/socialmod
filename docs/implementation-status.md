# Estado de la implementación (versión 0.2.0)

Comparación con `SOCIALMOD_PLAN.md`. La 0.2.0 corrige los 4 errores de `SOCIALMOD_ERRORES.md`, completa todos los
puntos pendientes de las fases 0–4 y las fases 5 y 6 en lo que depende de mods disponibles para 26.1.2, 26.2 y 26.3.
Lo que no se puede hacer todavía (mod inexistente para 26.x, solo beta sin API, o proyecto aparte) está en la wiki:
[Integraciones](wiki/18-integraciones.md). El detalle de cada error encontrado y su solución está en
[registro-de-errores.md](registro-de-errores.md).

Leyenda: ✅ hecho · 🟡 parcial · ⏳ pendiente (con motivo)

> **Protocolo de red 2.** Cliente y servidor deben tener la 0.2.0. Con versiones distintas el cliente pasa a modo
> "solo chat" sin errores. La API pública (`api.*`) no cambia.

## Errores de `SOCIALMOD_ERRORES.md`

| # | Error | Estado | Solución |
|---|---|---|---|
| 1 | El estado no rota al pulsarlo | ✅ | El servidor reenvía el snapshot tras `SET_STATUS`; el cliente aplica estado, privacidad, dimensión, leído y "escribiendo" al instante. |
| 2 | Formato de textos inconsistente | ✅ | Guía de estilo en `Ui` (títulos, cabeceras en mayúsculas por código, colores legibles); 4 idiomas en frase normal. |
| 3 | Tag del grupo: posición y personalización | ✅ | Etiqueta **debajo** del nombre (línea `scoreText`, sin mixins), emblemas, rol, selector visual de color con vista previa. |
| 4 | Placeholders sin procesar en toasts | ✅ | `MessageFormatter.preview` resuelve `[coords]` → `x: 120, z: -450` e `[item]` → nombre; acepta `[CORDS]`/`[ITEM]`. |

## Fase 0 — Base

| Punto | Estado | Notas |
|---|---|---|
| Multiversión con Stonecutter (26.1.2, 26.2, 26.3) | ✅ | Un jar por versión. |
| CI en GitHub Actions | ✅ | `build.yml` y `release.yml`; la release publica también la API en Maven si hay `vars.MAVEN_URL`. |
| `StorageBackend` `file` | ✅ | JSON comprimido por documento, escritura atómica, cuarentena de corruptos. |
| Handshake con versión de protocolo | ✅ | Protocolo 2 en la 0.2.0. |

## Fase 1 — MVP

| Punto | Estado | Notas |
|---|---|---|
| Mensajes privados, buzón, editar/borrar, "escribiendo" y "leído" | ✅ | |
| Amigos, favoritos, notas, bloqueos, privacidad | ✅ | |
| Presencia (en línea, AFK, DND, invisible, estado, dimensión) | ✅ | Cambio instantáneo en el cliente (error 1). |
| Toasts | ✅ | Marcadores resueltos (error 4). |
| Comandos para vanilla/Bedrock | ✅ | Más `/social` (menú de cofre) en la 0.2.0. |
| Anti-spam | ✅ | |

## Fase 2

| Punto | Estado | Notas |
|---|---|---|
| Grupos: nombre, etiqueta, color, descripción, MOTD, fijado | ✅ | `/g color` acepta `#RRGGBB`. |
| Emblema | ✅ | 20 emblemas Unicode (`GroupIcon`, lista blanca en el servidor), en la lista de grupos, el chat, el perfil y el nametag; `/g icon`. Sin texturas: se ven también en el fallback de teams para vanilla. |
| Roles con permisos configurables | ✅ | |
| Canales con rol mínimo | ✅ | **Silenciar por canal** en el cliente (icono ♪/⊘ de la cabecera): sin toasts, sonidos ni contador. |
| Parties temporales | ✅ | **Vida de los compañeros en el HUD** (borde izquierdo, a media altura). |
| Eventos del grupo, grupo principal, límites | ✅ | |
| Nametags | ✅ | Etiqueta debajo del nombre con emblema y rol; opción para ponerla delante. Fallback de teams con emblema. |
| Menciones | ✅ | |

## Fase 3

| Punto | Estado | Notas |
|---|---|---|
| Quick-Reply (`Y`) | ✅ | Si otro mod usa la tecla (p. ej. Xaero's Minimap), se recoloca sola mientras esté en su valor por defecto. |
| HUD social | ✅ | |
| Compartir coordenadas | ✅ | Waypoint en **Xaero's Minimap/World Map** o **JourneyMap** (doble clic o botón Waypoint); sin mapa, copia al portapapeles. |
| Compartir ítems | ✅ | |
| Ping en el mundo | ✅ | Tecla `J`: haz de partículas 10 s y línea en el HUD con distancia y flecha; vanilla recibe las coordenadas en el chat. |
| Temas JSON | ✅ | **Texturas** opcionales (`textures.background/panel/toast`, sprites nine-slice del resource pack). |
| Accesibilidad | ✅ | **Escala de texto propia** del chat (75–150 %), además de narrador, símbolos y alto contraste. |
| Adaptación de pantalla y margen para minimapas | ✅ | Márgenes automáticos con Xaero's Minimap; márgenes editables en Ajustes → Mapas. |

## Fase 4

| Punto | Estado |
|---|---|
| Validación total en el servidor, filtro, reportes, staff, spy visible, restricciones de chat, exportar/borrar, auditoría, LuckPerms, Text Placeholder API | ✅ |

## Fase 5 (v1.x)

| Punto | Estado | Notas |
|---|---|---|
| API pública | ✅ | Artefacto Maven `socialmod-api` (`publishToMavenLocal` / `publish`). |
| Simple Voice Chat | ✅ | Grupo de voz por grupo/party (`☏` en el panel, `/g voice`, `/party voice`). Plugin por entrypoint `voicechat`. |
| Plasmo Voice | ⏳ | Para 26.x solo hay beta (2.2.0-beta.1) y los grupos están en `pv-addon-groups`, sin API pública. Ver la wiki. |
| Claims: Open Parties and Claims | ✅ | Sincronización opcional (`integrations.claimsSync`: `to_claims` o `both`) por grupo con `/g claims link`. |
| Claims: FTB Chunks, Cadmus | ⏳ | No existen para 26.1.2–26.3. |
| Mapas: Xaero's Minimap / World Map | ✅ | Waypoints y márgenes automáticos (reflexión, sin dependencia). |
| Mapas: JourneyMap | ✅ | Waypoints (plugin de la API v2, entrypoint `journeymap`). |
| Backends H2, MySQL, MariaDB | ✅ | `JdbcStorageBackend`; driver en `config/socialmod/drivers/`; importa los datos de `file`. |
| Caché local en el cliente por servidor | ✅ | `config/socialmod/cache/<hash>/snapshot.json`; se puede desactivar. |

## Fase 6 (v2.0)

| Punto | Estado | Notas |
|---|---|---|
| GUI para vanilla y Bedrock | ✅ | `/social`: menú de cofre vanilla del servidor (amigos, estado, buzón, grupos). No necesita Polymer: un menú de cofre ya funciona en Java vanilla y en Bedrock con Geyser. |
| Polymer | ⏳ | No hace falta para el menú; queda como opción si se quieren ítems/bloques propios. Ver la wiki. |
| Redes con proxy (Velocity) | ⏳ | No es un mod por versión: requiere un plugin de proxy y Redis/puente. Proyecto aparte. |
| Addon de Discord Rich Presence | ⏳ | Jar aparte por diseño (PLAN 12), desactivado por defecto. Proyecto aparte. |

## Pruebas

| Punto | Estado |
|---|---|
| Unitarias (JUnit) | ✅ 35 pruebas (nuevas: vista previa de marcadores, emblemas, backend H2). |
| Gametests de servidor | ✅ 22 pruebas (nuevas: errores 1, 3 y 4; carga con 200 jugadores; OPAC; Simple Voice Chat; menú de cofre). |
| Test de cliente con capturas y reinicio del mundo | ✅ Incluye selector de estilo, ping y waypoint real en Xaero (`-Pxaero`). |
| Compat pack en CI | ✅ Lithium, FerriteCore, Text Placeholder API, Open Parties and Claims (+ Forge Config API Port) y Simple Voice Chat. Xaero, en el cliente con `-Pxaero`. |
| Carga con 200 jugadores | ✅ `loadTwoHundredPlayers`: 1200 mensajes en ~270–380 ms. |
