# 15. Guía de administradores

## Instalación

1. Instala **Fabric Loader** y **Fabric API** en el servidor.
2. Añade el jar de SocialMod que corresponda a tu versión de Minecraft (`socialmod-<versión>+mc26.x.jar`).
3. Arranca el servidor: se crea `config/socialmod/server.json` y la carpeta de datos `<mundo>/socialmod/`.
4. (Opcional) Instala **LuckPerms**, **Text Placeholder API**, etc. Todas las integraciones son opcionales.
5. Los jugadores **no están obligados** a instalar nada: sin el mod usan comandos. Quien quiera la interfaz instala el mismo jar.

Si cliente y servidor tienen **versiones de protocolo distintas**, el cliente queda en "solo chat" sin errores.

## Qué puedes decidir tú

| Quieres... | Ajuste (`server.json`) |
|---|---|
| Apagar una función entera | `modules.*` (privados, grupos, parties, amigos, presencia, buzón, compartir, menciones, placeholders) |
| Cambiar el largo de los mensajes | `chat.maxMessageLength` (256 por defecto) |
| Cambiar el tiempo para editar/borrar | `chat.editWindowSeconds` (120) |
| Quitar enlaces pulsables | `chat.allowLinks: false` |
| Quitar "escribiendo" y "leído" para todos | `chat.typingIndicator`, `chat.readReceipts` |
| Cuánto historial guardar | `storage.retentionDays` (30) y `storage.maxMessagesPerConversation` (500) |
| Límites de grupos, miembros, canales, amigos, party | `limits.*` |
| Antes de marcar AFK | `presence.afkMinutes` (5; 0 = nunca) |
| Anti-spam | `antiSpam.*` |
| Filtro de palabras | `filter.*` y datapacks |
| Permisos de cada rol de grupo | `roles` |
| Canales que tiene un grupo nuevo | `defaultChannels` |
| Formato del chat para jugadores sin el mod | `formats.*` |
| Dónde reciben avisos los jugadores sin el mod | `formats.vanillaNotifications` (`actionbar` o `chat`) |
| Mostrar `[TAG]` a jugadores sin el mod | `nametags.scoreboardFallback: true` |
| Vida de la party en el HUD / ping | `modules.partyHud`, `modules.ping` |
| Chat de voz por grupo (Simple Voice Chat) | `modules.voice` (y tener Simple Voice Chat en el servidor) |
| Sincronizar grupos con Open Parties and Claims | `integrations.claimsSync`: `off`, `to_claims` o `both`; cada líder enlaza con `/g claims link` |
| Guardar en una base de datos | `storage.backend`: `h2`, `mysql` o `mariadb` (ver abajo) |

Recarga sin reiniciar con **`/socialmod reload`**. Referencia completa en [configuración](../configuration.md).

## Ejemplos

### Un servidor survival entre amigos
Valores por defecto. Opcionalmente sube `limits.maxGroupsPerPlayer`.

### Un servidor público con muchos jugadores
```json
{
  "limits": { "maxGroupsPerPlayer": 2, "maxMembersPerGroup": 30, "packetsPerSecond": 15 },
  "antiSpam": { "maxMessages": 4, "windowSeconds": 5, "strikesToMute": 2, "autoMuteSeconds": 120 },
  "filter": { "enabled": true, "mode": "censor", "words": ["palabra1", "palabra2"] },
  "chat": { "allowLinks": false },
  "storage": { "retentionDays": 14 }
}
```

### Solo mensajes y amigos (sin clanes)
```json
{ "modules": { "groups": false, "parties": false } }
```

### Permitir que los miembros también expulsen
```json
{ "roles": { "member": ["invite", "kick"] } }
```

## Permisos (LuckPerms)

Con LuckPerms (o cualquier proveedor de la **Fabric Permission API**) los nodos son `socialmod.<ruta>`. Sin proveedor, los
nodos de jugador se conceden a todos y los de staff exigen OP nivel 2.

| Nodo | Para qué |
|---|---|
| `socialmod.chat.private` | Enviar privados (todos por defecto) |
| `socialmod.group.create` · `socialmod.party.create` | Crear grupos y parties |
| `socialmod.limit.groups` · `socialmod.limit.friends` | **Valor entero** que sustituye el límite de la config (por rango, p. ej. VIP = 6 grupos) |
| `socialmod.mod.mute` · `history` · `disband` · `inspect` · `reports` · `spy` · `bypass` | Staff |
| `socialmod.admin.reload` | `/socialmod reload` |
| `socialmod.data.export` · `socialmod.data.delete` | Derechos del jugador |

Los nodos enteros se definen según el proveedor de permisos (con LuckPerms, como valor de meta del grupo o usuario); si no hay valor, se usa el de `server.json`.

## Dónde se guardan los datos

`<mundo>/socialmod/`:

| Carpeta/archivo | Contenido |
|---|---|
| `players/` | Perfiles sociales |
| `groups/` | Grupos (las parties no se guardan) |
| `conversations/` | Un archivo comprimido por conversación |
| `reports/` | Reportes con contexto |
| `exports/` | Exportaciones de datos de jugadores |
| `audit.log` | Registro de auditoría |

- La escritura es **asíncrona** y **atómica**: el hilo del servidor nunca espera al disco y un corte de luz no deja archivos a medias.
- Si un archivo se corrompe, se aparta como `.corrupt` y el servidor sigue funcionando.
- **Copias de seguridad**: incluye la carpeta `socialmod/` en tus backups del mundo.
### Base de datos (H2, MySQL, MariaDB)

1. Descarga el **driver JDBC** y copia el `.jar` en `config/socialmod/drivers/` (H2: `h2-2.x.jar`; MySQL: Connector/J;
   MariaDB: Connector/J de MariaDB). No va dentro de SocialMod para no engordar el jar.
2. En `server.json`:

   ```jsonc
   "storage": {
     "backend": "mysql",                                   // o "h2" (sin más datos) o "mariadb"
     "jdbcUrl": "jdbc:mysql://localhost:3306/minecraft",   // vacío con h2: archivo en <mundo>/socialmod/socialmod-h2
     "user": "minecraft", "password": "...",
     "tablePrefix": "socialmod_"
   }
   ```
3. Reinicia. La primera vez, si la tabla está vacía, se **importan** los datos de `file` (los archivos se quedan como copia).

`audit.log` y `exports/` siguen en archivos. Si el driver falta o la conexión falla, el log lo explica y se usa `file`:
el servidor nunca se queda sin guardar. Protege `server.json`: la contraseña va en texto plano.

## Integraciones

Todas son opcionales y se activan solas si el mod está instalado. Detalle, versiones probadas y lo pendiente en
[Integraciones](18-integraciones.md).

## Placeholders

Con **Text Placeholder API**: `%socialmod:main_group%`, `%socialmod:tag%`, `%socialmod:unread%`, `%socialmod:status%`,
`%socialmod:friends_online%`. Útiles en tablist, scoreboards o chat de otros mods.

## Rendimiento

Diseñado para no pesar: el trabajo por tick es mínimo, la presencia no crece con el cuadrado de jugadores (solo se envía a
interesados, en lotes), los historiales se cargan bajo demanda con una caché acotada (`storage.cachedConversations`) y las listas
se paginan.

## Solución de problemas

| Síntoma | Qué revisar |
|---|---|
| Un jugador no ve la interfaz | ¿Tiene el mod en su cliente y la **misma versión de protocolo**? `/socialmod version` muestra la tuya. |
| "No se pudo leer server.json" en el log | JSON mal escrito: se usan los valores por defecto hasta que lo corrijas y hagas `/socialmod reload`. |
| El filtro no hace nada | Recuerda `filter.enabled: true` (o palabras en un datapack). Las expresiones regulares inválidas se ignoran con un aviso en el log. |
| Un permiso no se aplica | Los permisos se cachean 5 segundos; reconsulta tras cambiarlos o haz `/socialmod reload`. |
| Etiquetas de grupo chocan con TAB | Deja `nametags.scoreboardFallback` en `false` (por defecto). |
| "El backend 'mysql' no está disponible" | Falta el driver en `config/socialmod/drivers/` o la URL/usuario/contraseña son incorrectos; el mensaje dice cuál. |
| `/g voice` dice que no está disponible | Simple Voice Chat no está en el servidor o `modules.voice` es `false`. |
| `/g claims link` dice que no está activado | Open Parties and Claims no está instalado o `integrations.claimsSync` es `off`. |
| Clientes 0.1.0 entran en "solo chat" | La 0.2.0 usa el protocolo 2: actualiza cliente y servidor a la vez. |
