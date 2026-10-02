# Changelog

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
