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
    "mailbox": true, "sharing": true, "mentions": true, "placeholders": true,
    "partyHud": true,    // vida de los compañeros de party en el HUD (v0.2.0)
    "ping": true,        // ping en el mundo para la party (v0.2.0)
    "voice": true        // grupos de voz con Simple Voice Chat, si está instalado (v0.2.0)
  },
  "storage": {
    "backend": "file",          // file | h2 | mysql | mariadb (v0.2.0)
    "jdbcUrl": "",              // vacío con h2 = <mundo>/socialmod/socialmod-h2
    "user": "", "password": "",
    "tablePrefix": "socialmod_",
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
  "nametags": { "scoreboardFallback": false },  // teams del scoreboard para clientes vanilla
  "integrations": {
    "claimsSync": "off"         // Open Parties and Claims: off | to_claims | both (cada líder enlaza con /g claims link)
  }
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
| `hud` | `enabled`, `position`, `offsetX`, `offsetY`, `scale` (50–200), `partyHealth` |
| `nametags` | `showGroupTags`, `belowName` (etiqueta debajo del nombre), `showIcon`, `showRole` |
| `accessibility` | `narrateToasts`, `highContrast` |
| `panel` | `reservedRight`, `reservedTop` (margen para minimapas), `textScale` (75–150, texto del chat), `mutedChannels` (canales silenciados) |
| `ping` | `enabled`, `sound` |
| `maps` | `waypoints` (waypoints en Xaero), `autoMargins`, `autoMarginsApplied` |
| `cache` | `enabled` (caché local por servidor en `config/socialmod/cache/`) |

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
  "toast": { "background": "#E0101010", "border": "#555555", "width": 160 },
  "textures": {                       // opcional (v0.2.0): sprites de la GUI, admiten nine-slice
    "background": "mipack:socialmod/fondo",
    "panel": "mipack:socialmod/panel",
    "toast": "mipack:socialmod/toast"
  }
}
```

Los sonidos (`socialmod:notify.private`, `notify.mention`, `notify.invite`, `notify.event`, `notify.group`,
`notify.system`, `ping`) se redefinen en `assets/socialmod/sounds.json`.

## Datos guardados

`<mundo>/socialmod/`: `players/`, `groups/`, `conversations/` (un JSON comprimido por conversación), `reports/`,
`exports/` y `audit.log`. La escritura es atómica; un archivo corrupto se aparta como `.corrupt` y el servidor sigue.

### Texturas del tema

Los valores de `textures` son sprites del atlas de la GUI: el resource pack los pone en
`assets/<ns>/textures/gui/sprites/<ruta>.png` y, para que escalen sin deformarse, un `<ruta>.png.mcmeta` con
`"gui": { "scaling": { "type": "nine_slice", "width": 32, "height": 32, "border": 4 } }`. Cada textura sustituye
al color correspondiente (`background`, `panel`, `toast.background`). Con **Alto contraste** se ignoran.

## Bases de datos (h2, mysql, mariadb)

1. Copia el `.jar` del driver JDBC en `config/socialmod/drivers/` (no va dentro de SocialMod para no engordar el jar):
   H2 (`com.h2database:h2`), MySQL Connector/J o MariaDB Connector/J.
2. En `server.json`: `"backend": "h2"` (sin más) o `"mysql"`/`"mariadb"` con `jdbcUrl`, `user` y `password`.
3. Al arrancar, si la tabla `<tablePrefix>documents` está vacía, se importan los datos del backend `file`
   (los archivos se conservan como copia).

`audit.log` y `exports/` siguen siendo archivos. Si el driver falta o no conecta, el log lo explica y se usa `file`.
La contraseña se guarda en texto plano en `server.json`: protege el archivo.

## Datos del cliente

- `config/socialmod/client.json`: preferencias.
- `config/socialmod/cache/<hash>/snapshot.json`: último estado social por servidor (amigos, grupos, lista de
  conversaciones con la vista previa de la última línea). Se borra al desactivar **Caché local**.


## TEAM y preset visual (0.3.0)

`server.json` admite `maxTeams` (8 por defecto, entre 1 y 1000). El aspecto obligatorio se guarda en
`config/socialmod/visual.json`. Ver [TEAM y editor visual](teams-y-editor-visual.md) para permisos, modos,
componentes, exportacion/importacion y resource packs obligatorios.
