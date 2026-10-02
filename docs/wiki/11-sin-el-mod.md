# 11. Jugar sin el mod

SocialMod está pensado para que **nadie se quede fuera**. Si juegas con un cliente **vanilla**, desde **Bedrock (Geyser)** o con
cualquier otro cliente sin SocialMod, puedes hacer **todo lo importante con comandos**, y los mensajes llegan al chat normal.

## Cómo llegan los mensajes

Como mensajes de sistema en tu chat, con un formato configurable por el servidor. Por defecto:

```text
[Alex → yo] hola, ¿quedamos?            (privado recibido, en rosa)
[yo → Alex] vale, ahora voy             (privado enviado)
[TF#general] Steve: ¿quién tiene hierro? (canal de un grupo, en cian)
[Party] Luna: voy al portal             (party, en azul)
```

**Pulsa sobre el prefijo** (`[Alex → yo]`, `[TF#general]`...) y se rellena el comando para **responder** en tu barra de chat.
Los enlaces se pueden pulsar (te piden confirmación) y las coordenadas e ítems compartidos tienen su tooltip y clic de copiar.

## Las notificaciones

Las invitaciones, solicitudes de amistad, menciones y eventos te llegan por la **action bar** (sobre la barra de objetos) o
como línea de chat, según el servidor. Las invitaciones incluyen la pista del comando para aceptar.

## Chuleta de comandos

### Privados
| Comando | Qué hace |
|---|---|
| `/pm <jugador> <mensaje>` (o `/dm`) | Mensaje privado |
| `/r <mensaje>` | Responder al último |
| `/socialmod inbox` | Leer tus mensajes sin leer (buzón) |

### Amigos
| Comando | Qué hace |
|---|---|
| `/friend add <jugador>` | Solicitud de amistad |
| `/friend accept <jugador>` / `deny` / `remove` | Aceptar, rechazar o cancelar, quitar |
| `/friend list` · `/friend requests` · `/friend blocked` | Ver listas (las solicitudes traen botones **[Aceptar] [Rechazar]**) |
| `/friend favorite <jugador>` · `/friend note <jugador> <texto>` | Favorito y nota privada |
| `/block <jugador>` · `/unblock <jugador>` | Bloquear y desbloquear |

### Grupos
| Comando | Qué hace |
|---|---|
| `/g <mensaje>` | Hablar en tu grupo principal |
| `/g ch <canal> <mensaje>` · `/g to <TAG> <canal> <mensaje>` | Hablar en un canal concreto |
| `/g create <TAG> <nombre>` | Crear grupo |
| `/g invite <jugador>` · `/g accept <TAG>` · `/g decline <TAG>` | Invitaciones |
| `/g leave [TAG]` · `/g kick <jugador>` | Salir, expulsar |
| `/g promote\|demote\|transfer <jugador>` | Roles |
| `/g motd\|description\|pin <texto>` | Mensaje del día, descripción, fijado |
| `/g color <color>` · `/g tag <TAG>` | Identidad |
| `/g channel create <nombre> [rol]` · `/g channel delete <nombre>` | Canales |
| `/g event <minutos> <título>` | Evento programado |
| `/g info [TAG]` · `/g list` · `/g main <TAG>` | Información y grupo principal |
| `/g disband confirm` | Disolver (solo líder) |

### Party
| Comando | Qué hace |
|---|---|
| `/party create` · `/party invite <jugador>` | Crear e invitar |
| `/party accept` · `/party decline` · `/party leave` · `/party kick <jugador>` · `/party list` | Gestión |
| `/p <mensaje>` | Hablar en la party |

### Estado y privacidad
| Comando | Qué hace |
|---|---|
| `/status online\|away\|dnd\|invisible` | Cambiar estado |
| `/status text <texto>` · `/status clear` | Estado personalizado |
| `/socialmod privacy messages everyone\|friends\|nobody` | Quién puede escribirte |
| `/socialmod privacy status everyone\|friends\|nobody` | Quién ve tu estado |
| `/socialmod data export` · `/socialmod data delete confirm` | Tus datos |
| `/socialmod report "<conversación>" <id>` | Reportar un mensaje |

En el texto de cualquier mensaje funcionan `**negrita**`, `*cursiva*`, `@menciones`, `[coords]` e `[item]`.

## Autocompletado

Los comandos sugieren nombres de jugadores conectados, tus grupos y los canales de tu grupo principal al pulsar **Tab**.

## Diferencias respecto a tener el mod

| Con el mod | Sin el mod |
|---|---|
| Panel gráfico, perfiles con skin | Comandos y chat |
| Toasts flotantes con sonido | Action bar o chat |
| Respuesta Rápida con `Y` | Pulsar el prefijo del mensaje para responder |
| Etiqueta `[TAG]` sobre los jugadores | Opcional: el servidor puede mostrarla como prefijo de team (desactivado por defecto) |
| "Escribiendo..." y "Leído" | No disponible |
| Historial paginado con scroll | `/socialmod inbox` para lo pendiente |
