# Estado de la implementación (versión 0.1.0)

Comparación con `SOCIALMOD_PLAN.md`. Las fases 0–4 de la hoja de ruta (hasta la **v1.0**) están implementadas salvo los
puntos marcados; las fases 5 y 6 (v1.x y v2.0) quedan pendientes salvo la API pública.

Leyenda: ✅ hecho · 🟡 parcial · ⏳ pendiente

## Fase 0 — Base

| Punto | Estado | Notas |
|---|---|---|
| Multiversión con Stonecutter (26.1.2, 26.2, 26.3) | ✅ | Un jar por versión. |
| CI en GitHub Actions | ✅ | `.github/workflows/build.yml` y `release.yml` (skill `fabric-mod-github-ci`). |
| `StorageBackend` `file` | ✅ | JSON comprimido por documento, escritura atómica, hilo de E/S propio, cuarentena de archivos corruptos. |
| Handshake con versión de protocolo | ✅ | Protocolo distinto → modo "solo chat", sin crash. |

## Fase 1 — MVP

| Punto | Estado | Notas |
|---|---|---|
| Mensajes privados con historial | ✅ | Paginación (lazy loading) al hacer scroll. |
| Buzón | ✅ | Contador de no leídos persistente, resumen al conectarse, `/socialmod inbox` para vanilla. |
| Editar y borrar (2 min) | ✅ | El staff con `mod.history` puede borrar cualquiera (queda en auditoría). |
| "Escribiendo..." y "leído" | ✅ | Desactivables por el servidor y por cada jugador (recíproco). |
| Amigos, favoritos, notas, bloqueos | ✅ | Bloqueo en el servidor: privados, invitaciones, menciones, estado y mensajes de grupo. |
| Privacidad (quién me escribe / quién ve mi estado) | ✅ | |
| Presencia: en línea, AFK (servidor), DND, invisible, estado y dimensión opt-in | ✅ | Interés: amigos, compañeros y panel abierto; deltas cada 250 ms. |
| Toasts | ✅ | Posición, márgenes, duración, animaciones, apilado, agrupación por remitente, prioridades, DND inteligente (combate y pantallas), sonidos por tipo, narrador. |
| Comandos para vanilla/Bedrock | ✅ | `/pm`, `/r`, `/g`, `/p`, `/party`, `/friend`, `/block`, `/status`, `/socialmod`. |
| Anti-spam | ✅ | Ventana, repeticiones, silencio automático. |

## Fase 2

| Punto | Estado | Notas |
|---|---|---|
| Grupos: nombre, etiqueta, color, descripción, MOTD, fijado | ✅ | |
| Emblema (icono o banner) | ⏳ | El campo `icon` existe en los datos; falta el selector y las texturas. |
| Roles con permisos configurables | ✅ | `roles` en `server.json`. |
| Canales con rol mínimo | ✅ | Silenciar por canal en el cliente: ⏳. |
| Parties temporales | ✅ | Vida de los miembros en el HUD: ⏳. |
| Eventos del grupo | ✅ | Aviso 5 min antes y al empezar; se borran 1 h después. |
| Grupo principal | ✅ | |
| Límites por config y por permiso | ✅ | `socialmod.limit.groups`, `socialmod.limit.friends`. |
| Nametags | 🟡 | Con el mod: `[TAG]` delante del nombre en la misma línea (no encima/debajo), sin mixins; respeta invisibilidad, agacharse y distancia porque solo modifica el nametag que vanilla ya decidió mostrar. Fallback de teams opcional y desactivado. |
| Menciones `@jugador` y `@grupo` | ✅ | `@TAG`/`@everyone` exige el permiso de rol `pin`. |

## Fase 3

| Punto | Estado | Notas |
|---|---|---|
| Quick-Reply (`Y`) | ✅ | Tab entre las 5 últimas, historial con flechas, autocompletado de `@`, no pausa. Aviso de conflicto de teclas. |
| HUD social | ✅ | No leídos, estado y silencio. Canal de voz: depende de la integración (fase 5). |
| Compartir coordenadas | 🟡 | Distancia y dirección y copiar al portapapeles. Waypoints en Xaero/JourneyMap: ⏳ (fase 5). |
| Compartir ítems | ✅ | Generado por el servidor, tooltip completo. |
| Ping en el mundo | ⏳ | |
| Temas JSON | ✅ | Colores, columnas y toasts con Codecs y recarga con F3+T. Texturas de fondo: ⏳. |
| Accesibilidad | ✅ | Narrador, símbolos con forma propia (● ◐ ⊘ ○), alto contraste. Escala de texto propia: ⏳ (se usa la GUI Scale). |
| Adaptación de pantalla 3/2/1 columnas | ✅ | Breakpoints en píxeles escalados y margen para minimapas. |

## Fase 4

| Punto | Estado | Notas |
|---|---|---|
| Validación total en el servidor | ✅ | Permisos, bloqueos, límites, pertenencia, longitudes; rate limit de todos los paquetes. |
| Filtro de palabras | ✅ | Lista, regex, datapacks y filtros externos por API. |
| Reportes con contexto | ✅ | Guardados en `reports/`, aviso al staff conectado, `/socialmod mod reports`. |
| Comandos de staff | ✅ | `mute`, `unmute`, `history`, `disband`, `inspect`, `reports`, `spy`. |
| Spy opcional y visible | ✅ | Aviso en el panel de todos los jugadores cuando el servidor lo activa. |
| Restricciones de chat del cliente | ✅ | Chat oculto o cuenta restringida: la UI no se abre y el servidor no entrega mensajes (quedan en el buzón). |
| Exportar y borrar datos | ✅ | |
| Auditoría | ✅ | `<mundo>/socialmod/audit.log`. |
| LuckPerms | ✅ | Fabric Permission API. |
| Text Placeholder API | ✅ | 5 placeholders. |

## Fase 5 (v1.x) y 6 (v2.0)

| Punto | Estado |
|---|---|
| API pública (`SocialModServerAPI`, `SocialModClientAPI`, eventos) | ✅ (dentro del jar; artefacto Maven aparte ⏳) |
| Simple Voice Chat / Plasmo Voice | ⏳ |
| Claims (OPAC, FTB Chunks, Cadmus) | ⏳ |
| Mapas (Xaero, JourneyMap) | ⏳ |
| Backends H2 y MySQL | ⏳ (la interfaz `StorageBackend` ya está; el servidor avisa y usa `file`) |
| Caché local en el cliente por servidor | ⏳ |
| Redes con proxy (Velocity), Polymer, addon de Discord | ⏳ |

## Pruebas

| Punto | Estado |
|---|---|
| Unitarias (JUnit) | ✅ 27 pruebas: markdown seguro, saneado, ids, roles, rate limit, anti-spam, filtro, almacenamiento, retención, paginación. |
| Gametests de servidor | ✅ 15 pruebas con jugadores falsos (ver `docs/testing.md`). |
| Test de cliente con capturas y reinicio del mundo | ✅ |
| Compat pack en CI | 🟡 Lithium, FerriteCore y Text Placeholder API. Falta ampliar a la matriz completa de la sección 14. |
| Carga con 200 bots | ⏳ |
