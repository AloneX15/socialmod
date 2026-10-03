# 2. La interfaz

Abre el panel con **`K`**. Es una vista de tres columnas con el aspecto de los menús de Minecraft.

```text
+-------------------------+-------------------------------+---------------------+
| CONVERSACIONES          | GRUPO: Team Forest  #general  | EN LÍNEA (24)       |
+-------------------------+-------------------------------+---------------------+
| [Buscar...]             | [Fijado] Reunión sábado 18:00 | ● Alex   ★          |
|                         | Alex  12:41                   | ◐ Luna   [TF]       |
| DIRECTOS                |  alguien tiene diamantes?     |                     |
| ● Alex  "Mira la b.."   | Steve 12:42                   | MIEMBROS (3)        |
| ○ Steve "Nos vemos.."   |  tengo un stack en la base    | [L] Alex            |
|                         |                               | [O] Steve           |
| GRUPOS                  |                               | [M] Luna            |
| [TF] Team Forest   (3)  | Steve está escribiendo...     |                     |
|   #general              | > Escribe un mensaje...  ⌖ ✦ ➤| [+ Invitar] [Ajustes]|
| [+ Nuevo grupo][Ajustes]|                               |                     |
+-------------------------+-------------------------------+---------------------+
```

## Columna izquierda: conversaciones

- **Buscar**: filtra por nombre de jugador, de grupo o etiqueta.
- **Solicitudes**: aparecen arriba cuando alguien quiere ser tu amigo o te invita a un grupo. Pulsa **✔** para aceptar o
  **✖** para rechazar, sin abrir nada más.
- **Directos**: tus conversaciones privadas recientes, con la cabeza del jugador, su estado (●, ◐, ⊘, ○), el último mensaje
  y un contador en naranja de mensajes sin leer. Debajo aparecen tus **amigos** (los favoritos ★ primero, luego los conectados).
- **Grupos**: cada grupo con su etiqueta de color y sus canales (`#general`, `#comercio`...). El número entre paréntesis son los
  mensajes sin leer.
- **Clic derecho** sobre un directo abre el perfil de esa persona.
- **+ Nuevo grupo** y **Ajustes** (abajo).

## Columna central: el chat

- La **cabecera** muestra con quién hablas (y su estado personalizado), o el emblema, el grupo y el canal. Si el grupo tiene
  un **mensaje fijado**, aparece justo debajo. A la derecha, en grupos y parties:
  - **♪ / ⊘**: silenciar ese canal solo para ti (sin toasts, sonido ni contador; las menciones sí avisan).
  - **☏**: entrar o salir del chat de voz del grupo (solo si el servidor tiene Simple Voice Chat; verde = dentro).
- Los mensajes se agrupan por autor con su cabeza y la hora. Las **menciones** a ti salen en dorado, los **enlaces** en azul y
  subrayado, las **coordenadas** en verde y los **ítems** en azul claro (con tooltip al pasar el ratón).
- **Rueda del ratón** para subir. Al llegar arriba se cargan **mensajes más antiguos** automáticamente (historial paginado).
- Bajo los mensajes ves **"X está escribiendo..."** y, en privados, **✔ Leído** cuando la otra persona ha visto tu mensaje.
- Abajo está la **barra de escritura** con tres botones:
  - **⌖** inserta `[coords]` (tu posición actual).
  - **✦** inserta `[item]` (el ítem que llevas en la mano).
  - **➤** envía (o pulsa **Enter**).

### Trucos de la barra de escritura

| Tecla | Qué hace |
|---|---|
| **Enter** | Enviar. |
| **Tab** | Autocompletar un `@nombre` (miembros del grupo y jugadores conectados). |
| **↑ / ↓** | Recorrer los mensajes que ya enviaste. |
| **Esc** | Cancelar la edición de un mensaje, o cerrar el panel. |

### Acciones sobre un mensaje

**Pulsa un mensaje** para seleccionarlo; aparece una fila de botones encima de la barra:

| Botón | Cuándo aparece |
|---|---|
| **Editar** | Es tuyo y han pasado menos de 2 minutos (configurable). |
| **Borrar** | Es tuyo y han pasado menos de 2 minutos. Queda como "mensaje borrado". |
| **Reportar** | Es de otra persona. Se envía al staff con los mensajes de alrededor como contexto. |
| **Copiar** | Siempre: copia el texto sin formato. |
| **Abrir enlace** | El mensaje contiene un enlace (te pide confirmación, igual que en vanilla). |
| **Waypoint** | El mensaje tiene coordenadas y tienes Xaero's Minimap o JourneyMap. |

Si el mensaje contiene coordenadas, **vuelve a pulsarlo** para crear el waypoint (con mapa) o copiarlas al portapapeles.

### Estilo de los textos

Todo el panel sigue la misma guía: títulos en blanco centrados, **CABECERAS DE SECCIÓN** en mayúsculas y gris con una línea,
botones y etiquetas en frase normal ("En línea", "Conversaciones"), y los colores de los grupos se aclaran solos si son
demasiado oscuros para leerse sobre el fondo.

## Columna derecha: jugadores

- Si estás en un grupo: la lista de **miembros** ordenada por rol (`[L]` líder, `[O]` oficial, `[M]` miembro, `[R]` recluta) y
  los **eventos** próximos.
- **En línea**: todos los jugadores conectados, con su estado, ★ si son amigos y su `[TAG]` de grupo.
- Pulsa cualquier nombre para abrir su **perfil**.
- Abajo: **+ Invitar** y **Ajustes del grupo** (si estás en un grupo), o **+ Nueva party**.

## El perfil rápido

Muestra la **skin en 3D** (si la persona está cerca de ti; si no, su cara), su grupo y rango, su estado y su estado
personalizado. Los botones cambian según tu relación con ella:

| Botón | Para qué |
|---|---|
| **Mensaje** | Abrir su conversación privada. |
| **Añadir amigo / Aceptar amistad / Cancelar solicitud / Quitar amigo** | Gestionar la amistad. |
| **★ Favorito** | Marcar o desmarcar un amigo (sale primero en tu lista). |
| **Bloquear / Desbloquear** | Ver [Amigos y bloqueos](04-amigos-y-bloqueos.md). |
| **Invitar a party** | Crea tu party si no la tienes e invita. |
| **Invitar a [TAG]** | Invitarle a tu grupo (si tu rol puede invitar). |
| **Ascender / Degradar / Expulsar / Hacer líder** | Gestión del grupo, solo si tu rol lo permite. |
| **Nota privada** | Un apunte solo para ti sobre ese amigo ("me debe 3 diamantes"). |

## Se adapta a tu pantalla

El diseño cambia según el **ancho de la GUI** (no los píxeles reales, así funciona con cualquier *GUI Scale*):

| Ancho disponible | Diseño |
|---|---|
| 480 o más | Tres columnas a la vez. |
| 320 – 479 | Dos columnas; la lista de jugadores pasa a una pestaña. |
| Menos de 320 | Pestañas a pantalla completa: **Conversaciones / Chat / Jugadores**. |

Si usas un minimapa u otro HUD en un lado, reserva ese espacio en *Ajustes → Margen para minimapas* y el panel no lo tapará.
