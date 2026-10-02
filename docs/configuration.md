# Configuración

Separación por capas (PLAN 15):

| Capa | Archivo | Contenido | Recarga |
|---|---|---|---|
| Servidor (reglas) | `config/socialmod/server.json` + datapacks | Módulos, límites, retención, anti-spam, filtro, roles, formatos | `/socialmod reload` (datapacks: `/reload`) |
| Resource pack (aspecto) | `assets/socialmod/themes/default.json`, `sounds.json`, `lang/` | Colores, columnas, toasts, sonidos, textos | F3+T |
| Cliente (preferencias) | `config/socialmod/client.json` | Toasts, sonidos, HUD, etiquetas, accesibilidad | Al instante desde Ajustes |

Si un archivo tiene errores se registra en el log y se usan los valores por defecto. Nunca crashea.

## `server.json`

```jsonc
{
  "modules": {           // cada módulo se puede desactivar por separado
    "privateMessages": true, "groups": true, "parties": true, "friends": true, "presence": true,
    "mailbox": true, "sharing": true, "mentions": true, "placeholders": true
  },
  "storage": {
    "backend": "file",          // h2 y mysql: previstos para la v1.x
    "retentionDays": 30,        // 0 = sin límite de días
    "maxMessagesPerConversation": 500,
    "flushIntervalSeconds": 5,  // escrituras agrupadas en un hilo propio
    "cachedConversations": 256
  },
  "chat": {
    "maxMessageLength": 256, "editWindowSeconds": 120, "allowLinks": true,
    "typingIndicator": true, "readReceipts": true, "historyPageSize": 50,
    "maxStatusLength": 48, "maxNoteLength": 128
  },
  "limits": {
    "maxGroupsPerPlayer": 3, "maxMembersPerGroup": 50, "maxChannelsPerGroup": 8, "maxFriends": 200,
    "maxPartySize": 8, "maxPendingRequests": 50, "maxEventsPerGroup": 10,
    "packetsPerSecond": 20, "packetBurst": 40       // rate limit de todos los paquetes C→S
  },
  "presence": { "afkMinutes": 5, "batchTicks": 5 }, // 5 ticks = 250 ms
  "antiSpam": {
    "enabled": true, "maxMessages": 5, "windowSeconds": 4, "maxRepeats": 3,
    "repeatWindowSeconds": 30, "strikesToMute": 3, "autoMuteSeconds": 60
  },
  "filter": { "enabled": false, "mode": "censor", "words": [], "regex": [] },  // mode: censor | block
  "moderation": {
    "spyEnabled": false,        // si es true, los jugadores ven un aviso en el panel
    "reportsEnabled": true, "reportContext": 5,
    "allowDataExport": true, "allowDataDelete": true, "auditLog": true
  },
  "formats": {                  // para jugadores sin el mod
    "directIn": "[{sender} → me] {message}", "directOut": "[me → {receiver}] {message}",
    "group": "[{tag}#{channel}] {sender}: {message}", "party": "[Party] {sender}: {message}",
    "directColor": "light_purple", "groupColor": "aqua", "partyColor": "blue",
    "vanillaNotifications": "actionbar"   // o chat
  },
  "roles": {                    // el líder siempre tiene todos los permisos
    "officer": ["invite", "kick", "manage_channels", "pin", "edit_info", "manage_events"],
    "member": ["invite"],
    "recruit": []
  },
  "defaultChannels": ["general"],
  "nametags": { "scoreboardFallback": false }   // teams del scoreboard para clientes vanilla
}
```

Permisos de rol disponibles: `invite`, `kick`, `manage_channels`, `pin` (fijar y mencionar a todo el grupo),
`edit_info`, `manage_roles`, `manage_events`.

### Filtro desde datapacks

`data/<namespace>/socialmod/filters/<nombre>.json`:

```json
{ "words": ["palabra1", "palabra2"] }
```

Las palabras de los datapacks se suman a las de `server.json` y activan el filtro aunque `filter.enabled` sea `false`.

## `client.json`

Se edita desde **Ajustes** en el panel (o desde ModMenu). Un modpack puede incluir `config/socialmod/client-defaults.json`
con los mismos campos: se aplica como valores por defecto sin pisar lo que el jugador haya cambiado.

| Sección | Campos |
|---|---|
| `toasts` | `enabled`, `position` (`TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT`), `marginX`, `marginY`, `durationSeconds` (3–15), `animations`, `maxVisible`, `groupBySender`, `showGroupMessages`, `smartDnd` |
| `sounds` | `enabled`, `volume` (0–100), `privateMessages`, `mentions`, `invites`, `events`, `groupMessages` |
| `hud` | `enabled`, `position`, `offsetX`, `offsetY`, `scale` (50–200) |
| `nametags` | `showGroupTags` |
| `accessibility` | `narrateToasts`, `highContrast` |
| `panel` | `reservedRight`, `reservedTop` (margen para minimapas) |

## Tema (resource pack)

`assets/socialmod/themes/default.json` usa anclas y pesos, no coordenadas absolutas:

```json
{
  "layout": "three_column",
  "columns": [
    { "id": "conversations", "weight": 1, "min_width": 110 },
    { "id": "chat",          "weight": 2, "min_width": 160 },
    { "id": "players",       "weight": 1, "min_width": 90, "collapsible": true }
  ],
  "colors": { "text": "#E0E0E0", "muted": "#909090", "accent": "#55FF55", "unread": "#FFAA00",
              "background": "#C0000000", "panel": "#80101010", "border": "#404040", "highlight": "#40FFFFFF" },
  "toast": { "background": "#E0101010", "border": "#555555", "width": 160 }
}
```

Los sonidos (`socialmod:notify.private`, `notify.mention`, `notify.invite`, `notify.event`, `notify.group`,
`notify.system`) se redefinen en `assets/socialmod/sounds.json`.

## Datos guardados

`<mundo>/socialmod/`: `players/`, `groups/`, `conversations/` (un JSON comprimido por conversación), `reports/`,
`exports/` y `audit.log`. La escritura es atómica; un archivo corrupto se aparta como `.corrupt` y el servidor sigue.
