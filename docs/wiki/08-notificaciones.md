# 8. Notificaciones

SocialMod avisa de lo importante con **toasts**: pequeños recuadros flotantes con el aspecto de los avisos de Minecraft.

```text
+------------------------------------------------------+
| [cabeza] Alex                          hace 10 s     |
| ¿Nos vemos en x:120, z:-450?                         |
| [Y] Responder                                        |
+------------------------------------------------------+
```

## Qué te notifica

| Tipo | Cuándo | Borde |
|---|---|---|
| **Mención** | Alguien te menciona con `@tu_nombre` (o `@TAG` en tu grupo) | Dorado |
| **Invitación** | Te invitan a un grupo o party | Cian |
| **Amistad** | Solicitud recibida o aceptada | Cian |
| **Mensaje privado** | Te escriben por privado | Verde |
| **Evento** | Un evento de tu grupo empieza pronto o ahora | Magenta |
| **Mensaje de grupo** | Hay mensajes nuevos en un canal (desactivable) | Gris |
| **Sistema** | Avisos del servidor u otros mods (reportes para el staff...) | Gris |

Si ya tienes abierta esa conversación en el panel, **no se muestra** el toast (ya lo estás viendo).

## Prioridades

Cuando llegan varios a la vez, se ordenan por importancia:

1. **Menciones e invitaciones** (arriba del todo)
2. Mensajes privados, eventos y amistades
3. Avisos de sistema
4. Mensajes de grupo

A igual prioridad, el más reciente va primero. Se muestran como máximo **3 a la vez** (configurable de 1 a 8).

## Agrupación

Si la misma persona te escribe varias veces seguidas, **no se apilan 5 toasts**: se agrupan en uno solo, *"Alex (3)"*, que se
actualiza con el último mensaje.

## Modo "No molestar" inteligente

Para que no te interrumpan en mal momento, los toasts **esperan** cuando:

- Has **recibido daño** en los últimos 5 segundos (estás en combate), o
- Tienes **cualquier pantalla abierta** (inventario, cofre, menú...).

Cuando termina, si llegaron varios verás un único resumen: *"4 notificaciones mientras estabas ocupado"*. Se puede desactivar
en Ajustes (**No molestar intel.**).

Aparte, si tu **estado** es **No molestar** (⊘), no aparecen toasts ni suenan sonidos salvo **menciones e invitaciones**.

## Sonidos

Cada tipo tiene su sonido (privado, mención, invitación, evento, grupo, sistema), todos con el estilo del juego. En Ajustes
puedes:

- Cambiar el **volumen** (0 %, 25 %, 50 %, 70 %, 100 %).
- Silenciar tipos concretos editando `client.json` (`privateMessages`, `mentions`, `invites`, `events`, `groupMessages`).

Los packs de recursos pueden **sustituir los sonidos** (`socialmod:notify.private`, `notify.mention`...).

## Posición, duración y animaciones

En **Ajustes** (columna "Este cliente"):

| Ajuste | Opciones |
|---|---|
| **Toasts** | Activar/desactivar todos. |
| **Posición** | Arriba/abajo, izquierda/derecha. |
| **Duración** | 3, 4, 6, 8, 10, 12 o 15 segundos. |
| **Máx. toasts** | 1 a 5 a la vez. |
| **Animaciones** | Deslizamiento y fundido, activables. |
| **Toasts de grupo** | Mostrar o no los mensajes normales de grupo. |

Usan un elemento de HUD de Fabric, así que se ordenan bien con otros HUD y se **ocultan con F1**.

## El widget del HUD

Un recuadro pequeño siempre visible (si lo activas) que muestra:

- **Tu estado** (●, ◐, ⊘ u ○),
- **✉ N**: los mensajes sin leer en total,
- **⊘** en rojo si estás silenciado.

Puedes cambiar su **posición** (4 esquinas) y su **escala** (75 %, 100 %, 125 %, 150 %), o desactivarlo.
Se oculta con F1 y cuando tienes una pantalla abierta.

## Narrador

Si tienes activado el **Narrador** de Minecraft, los toasts se leen en voz alta (se puede apagar en *Ajustes → Narrador*).

## Notificaciones sin el mod

Si no tienes el mod, las notificaciones importantes te llegan por la **action bar** (la línea sobre la barra de objetos) o por el
chat, según decida el servidor.
