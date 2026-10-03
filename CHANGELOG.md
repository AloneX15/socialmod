# Changelog

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
