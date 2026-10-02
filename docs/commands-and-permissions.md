# Comandos y permisos

Todo lo que hace la UI se puede hacer con comandos, así que los jugadores sin el mod (vanilla o Bedrock con Geyser)
tienen la misma funcionalidad. Los jugadores se buscan por nombre (deben haber entrado antes en el servidor) o por UUID.

## Mensajes

| Comando | Descripción |
|---|---|
| `/pm <jugador> <mensaje>` (alias `/dm`) | Mensaje privado. Si está desconectado queda en su buzón. |
| `/r <mensaje>` | Responder al último privado. |
| `/socialmod inbox` | Ver los mensajes sin leer (para jugadores sin el mod). |

En el texto: `**negrita**`, `*cursiva*`, `` `código` ``, `@jugador`, `@TAG` (menciona a todo el grupo; requiere el
permiso de rol `pin`), `[coords]` (tu posición) e `[item]` (el ítem de tu mano).

> SocialMod no sustituye `/msg` ni `/tell` de vanilla: no toca el chat firmado ni el sistema de reportes de Mojang.

## Grupos (`/g`)

Las acciones se aplican a tu **grupo principal** (cámbialo con `/g main <TAG>`).

| Comando | Descripción |
|---|---|
| `/g <mensaje>` | Escribir en el primer canal del grupo principal. |
| `/g ch <canal> <mensaje>` | Escribir en un canal del grupo principal. |
| `/g to <TAG> <canal> <mensaje>` | Escribir en un canal de cualquiera de tus grupos. |
| `/g create <TAG> <nombre>` | Crear un grupo (TAG de 2–5 letras o números). |
| `/g invite <jugador>` · `/g accept <TAG>` · `/g decline <TAG>` | Invitaciones. |
| `/g leave [TAG]` · `/g kick <jugador>` | Salir y expulsar. |
| `/g promote <jugador>` · `/g demote <jugador>` · `/g transfer <jugador>` | Roles (Recluta → Miembro → Oficial; el líder se transfiere). |
| `/g motd <texto>` · `/g description <texto>` · `/g pin <texto>` | Mensaje del día, descripción y mensaje fijado. |
| `/g color <#RRGGBB o nombre>` · `/g tag <TAG>` | Identidad del grupo. |
| `/g channel create <nombre> [rol mínimo]` · `/g channel delete <nombre>` | Canales con permisos por rol. |
| `/g event <minutos> <título>` | Evento: avisa a los miembros conectados 5 min antes y al empezar. |
| `/g info [TAG]` · `/g list` · `/g main <TAG>` | Información. |
| `/g disband confirm` | Disolver (solo el líder). Borra el historial. |

## Parties

| Comando | Descripción |
|---|---|
| `/party create` · `/party invite <jugador>` | Crear e invitar (invitar crea la party si no existe). |
| `/party accept` · `/party decline` · `/party leave` · `/party kick <jugador>` · `/party list` | Gestión. |
| `/p <mensaje>` | Chat de la party. |

## Amigos y bloqueos

| Comando | Descripción |
|---|---|
| `/friend add\|accept\|deny\|remove <jugador>` | Amistad (`deny` también cancela una solicitud tuya). |
| `/friend favorite <jugador>` · `/friend note <jugador> <texto>` | Favoritos y notas privadas. |
| `/friend list` · `/friend requests` · `/friend blocked` | Listas. |
| `/block <jugador>` · `/unblock <jugador>` (o `/friend block\|unblock`) | Bloqueos (se aplican en el servidor). |

## Estado y privacidad

| Comando | Descripción |
|---|---|
| `/status online\|away\|dnd\|invisible` | Estado. |
| `/status text <texto>` · `/status clear` | Estado personalizado. |
| `/socialmod privacy messages everyone\|friends\|nobody` | Quién puede escribirme. |
| `/socialmod privacy status everyone\|friends\|nobody` | Quién ve mi estado. |
| `/socialmod data export` · `/socialmod data delete confirm` | Exportar o borrar mis datos (si el servidor lo permite). |
| `/socialmod report "<conversación>" <id>` | Reportar un mensaje (en la UI: seleccionar el mensaje → Reportar). |

## Staff

| Comando | Nodo | Por defecto |
|---|---|---|
| `/socialmod mod mute <jugador> <segundos> [motivo]` (0 = indefinido) · `unmute <jugador>` | `socialmod:mod.mute` | OP 2 |
| `/socialmod mod history dm <a> <b>` · `history group <TAG> <canal>` | `socialmod:mod.history` | OP 2 |
| `/socialmod mod disband <grupo>` | `socialmod:mod.disband` | OP 2 |
| `/socialmod mod inspect <jugador>` · `/socialmod data export <jugador>` | `socialmod:mod.inspect` | OP 2 |
| `/socialmod mod reports` | `socialmod:mod.reports` | OP 2 |
| `/socialmod mod spy` (solo si `moderation.spyEnabled`) | `socialmod:mod.spy` | OP 2 |
| `/socialmod reload` | `socialmod:admin.reload` | OP 2 |

Con LuckPerms (o cualquier proveedor de la Fabric Permission API) los nodos se escriben como `socialmod.mod.mute`, etc.

## Nodos de permiso

| Nodo | Uso | Por defecto |
|---|---|---|
| `socialmod.chat.private` | Enviar privados | todos |
| `socialmod.group.create` | Crear grupos | todos |
| `socialmod.party.create` | Crear parties | todos |
| `socialmod.data.export` · `socialmod.data.delete` | Derechos sobre los datos | todos |
| `socialmod.mod.bypass` | Saltarse privacidad y anti-spam | OP 2 |
| `socialmod.limit.groups` (entero) | Grupos por jugador; sustituye a `limits.maxGroupsPerPlayer` | config |
| `socialmod.limit.friends` (entero) | Amigos por jugador; sustituye a `limits.maxFriends` | config |

Todos los cambios de moderación y de gestión de grupos quedan en `<mundo>/socialmod/audit.log`.
