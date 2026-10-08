# Changelog

## 0.8.0

- Add FancyMenu catalog elements for live TEAM banners, contextual text, native modules and independent form controls on SocialMod screens.
- Share native controls, permission checks, confirmations and drafts across modules; isolate named forms and discard drafts when their destination changes.
- Add configurable FancyMenu actions and contextual placeholders. Save contexts, destinations and form bindings in FancyMenu layouts.
- Let FancyMenu control its toolbar; original-interface mode disables added elements without deleting layouts. Preserve protocol 5 and public API signatures.
- Add real layout save/reload and input-routing client regression tests. Document the catalog and banner placement in `docs/personalizacion-0.8.0.md`.

## 0.7.0

- TEAM banners with Minecraft dye colors and up to six ordered patterns, independent of tag icons. Leaders and administrators can edit them; old teams receive a white banner.
- Universal paginated player search and direct chat opening, including known offline identities without revealing hidden presence.
- Personal original-interface preference per server and administrator original mode for everyone, preserving saved styles. Basic settings work with or without optional editors.
- FancyMenu supports compact icon buttons, a search button, and decoration around dynamic banners. Remove the built-in advanced design installer, series profile screens and previous-design restore entry point; use FancyMenu profiles instead.
- Network protocol 5; public API signatures unchanged. Atomic asynchronous client configuration writes.


## 0.6.0

- Navidad gráfica con los seis botones PNG y ocho iconos originales proporcionados: sustituye al ejemplo Social limpio, conserva Dedsafío y mantiene visible el mundo. Imágenes editables por variante o control, estados de botón, accesibilidad, respaldos y exportación completa.

- Replace the former Christmas guide with illustrated Christmas and Dedsafio profiles using FancyMenu and SpiffyHUD, translucent panels and no full-screen background or blur.
- Add bilingual guides and per-Minecraft design packages; retain legacy assets for existing user customizations.
- Preserve row editor drafts, reject invalid navigation, cap undo history and restore render state on failure.
- Keep failed conversation reads unavailable rather than overwriting old history with empty data; prepare large storage indexes in bounded batches and drain profile writes at shutdown.
- Protocol 4: send only changed snapshot sections, chunk large updates, apply them atomically and request a fresh baseline on synchronization errors. Update clients and server together.
- Add dependency version predicates to profile manifests and review; legacy manifests remain readable with an unspecified-version warning.

- Query permission providers at action time, deny protected actions on provider failure, and recheck authorization after asynchronous chat/history loads.
- Add real LuckPerms tests for grants, denials, revocation, contexts and integer metadata; document boolean nodes and colon-separated limit metadata.
- Batch snapshots to 20 recipients per tick, share TEAM views within a batch, skip identical payloads and bound outgoing snapshot size.
- Coalesce pending writes, retain failed snapshots for bounded retries and close storage only after queued I/O drains.
- Reuse custom HUD rows and measurements, index SpiffyHUD elements per tick, isolate SpiffyHUD failures from FancyMenu and respect HUD preferences.
- Reject ZIP symlinks, Windows filename aliases and file/directory collisions; validate restored profile state before touching design files.
- Hide activity timestamps of invisible friends, bound notification queues and rate-limit expensive export/report requests.

## 0.5.0

- Named local series profiles: save, duplicate and activate complete panel, row and HUD designs.
- Selective ZIP export with author, version, Minecraft and dependency metadata; unrelated FancyMenu settings stay out.
- Guided ZIP review and installation, with persistent backup, automatic rollback on file-write errors and restoration of the previous design.
- Profile switching restores pre-existing assets, disables retired pre-existing layouts and removes files created exclusively by the retired profile.
- Spanish and English instructions and real screenshots for profiles, selective export and import review.

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
