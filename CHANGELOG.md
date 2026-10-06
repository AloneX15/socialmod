# Changelog

## 0.4.0

- Optional FancyMenu and SpiffyHUD client integrations using registered elements, editor wrappers and live placeholders.
- Stable screen and control identifiers; movable, resizable social blocks retain their native actions and scrolling.
- Detailed local row templates for messages, conversations, players, party members and notifications, with fonts, textures, wrapping and undo/redo.
- Independent custom social, party, ping and notification HUD components with native fallback.
- Editable Christmas modpack example, atomic local saves, template reset and bounded ZIP export of instance-relative assets.
- Spanish and English guides with actual editor and in-game captures.

## 0.3.2

- Close the social panel using its assigned key, including remapped bindings.
- Christmas Winter Lodge pixel-art template: illustrated nine-slice frames, snowy scenery, beveled controls and a bundled bitmap font with Spanish glyphs.
- Configurable input textures, selected button sprites and frame insets; exported presets include bundled assets.
- Restoring a preset closes the draft editor so the restored server design is immediately visible.
- Explicit template chooser and bilingual illustrated setup documentation.

## 0.3.1

- Visual inspector categories, texture thumbnails, layout templates and grouped panel movement with grid snapping.
- Previous preset recovery and bounded server revision backups; missing-texture and clipped-control checks.
- Dedicated TEAM administration screen, current TEAM in profiles and explicit action confirmations.

## 0.3.0 - TEAM y editor visual

- TEAM independiente de los grupos, primera eleccion bloqueada y chat vinculado automatico.
- Gestion administrativa, limite de teams activos y archivo/restauracion reversible con historial.
- Nametags con el nombre completo del TEAM; los grupos existentes no se convierten automaticamente.
- Ventana compacta, panel lateral y pantalla completa; editor de controles, textos, fondos, colores y recursos.
- Presets visuales versionados, exportacion/importacion de packs y aspecto obligatorio del servidor.
- Protocolo 3: actualizar cliente y servidor juntos. Guia en `docs/teams-y-editor-visual.md`.


## 0.2.0 — integraciones y correcciones

Protocolo de red 2: cliente y servidor deben tener la 0.2.0 (con versiones distintas el cliente pasa a "solo chat").
Detalle en `docs/implementation-status.md` y `docs/registro-de-errores.md`.

- Corregidos los 4 errores de `SOCIALMOD_ERRORES.md`: el estado cambia al instante, estilo de textos uniforme, etiqueta
  del grupo debajo del nombre con selector de estilo, y `[coords]`/`[item]` resueltos en los toasts.
- Emblemas de grupo, silenciar por canal, vida de los compañeros de party en el HUD y ping en el mundo (`J`).
- Integraciones opcionales: Simple Voice Chat, Open Parties and Claims, Xaero's Minimap/World Map y JourneyMap.
- Backends H2, MySQL y MariaDB; caché local por servidor en el cliente; menú de cofre `/social` para vanilla y Bedrock.
- Temas con texturas, escala de texto propia y recolocación automática de teclas en conflicto.
- API pública publicada como artefacto Maven `socialmod-api`.
- CI: job de normas TakumiStudios, notas de release desde el changelog y comprobación del tag frente a `mod.version`.

## 0.1.0 — primera versión

Fases 0–4 del plan (ver `docs/implementation-status.md`):

- Base multiversión (MC 26.1.2, 26.2, 26.3) con Stonecutter, CI y almacenamiento `file` asíncrono.
- Handshake con versión de protocolo y modo "solo chat" para clientes incompatibles o sin el mod.
- Mensajes privados con historial paginado, buzón, editar/borrar, "escribiendo..." y "leído".
- Amigos, favoritos, notas, bloqueos aplicados en el servidor y privacidad.
- Presencia (en línea, AFK en el servidor, no molestar, invisible, estado personalizado, dimensión opcional) por deltas
  y solo a los interesados.
- Grupos con roles configurables, canales por rol, eventos, MOTD y mensaje fijado; parties temporales.
- Panel adaptable (3/2/1 columnas), perfil con skin 3D, ajustes, toasts con prioridades y no molestar inteligente,
  HUD social, Respuesta Rápida (`Y`), compartir `[coords]` e `[item]`, etiquetas de grupo en el nametag.
- Moderación: rate limit, anti-spam, filtro (config, regex, datapacks, API), reportes con contexto, silencios,
  historial, inspección, disolver grupos, spy opcional y visible, auditoría, exportar y borrar datos.
- Integraciones opcionales: LuckPerms (Fabric Permission API), Text Placeholder API, ModMenu.
- API pública de servidor y cliente con eventos.
- Sin mixins.
