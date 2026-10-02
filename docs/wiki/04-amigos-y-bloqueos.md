# 4. Amigos y bloqueos

## Hacer amigos

1. Abre el perfil de la persona (pulsa su nombre en el panel) y pulsa **Añadir amigo**.
   Sin el mod: `/friend add <jugador>`.
2. Ella recibe una notificación y la solicitud aparece en su panel, arriba, en **SOLICITUDES**.
3. Al **aceptar** (✔ en el panel o `/friend accept <jugador>`), os hacéis amigos **mutuamente**.

Cosas útiles:

- Si esa persona ya te había enviado una solicitud, **añadirla la acepta directamente**.
- Para rechazar una solicitud o **cancelar una que tú enviaste** usa `/friend deny <jugador>` (o el botón del perfil).
- Para ver lo pendiente: `/friend requests` (con botones **[Aceptar] [Rechazar]** pulsables en el chat).
- Límite por defecto: **200 amigos** y **50 solicitudes pendientes** (el servidor puede cambiarlo).

## Lo que te dan los amigos

- Aparecen **siempre** en tu columna de Directos, con su estado y, si lo comparten, su estado personalizado.
- Puedes limitar a "solo amigos" quién te escribe y quién ve tu estado.
- Reciben tu **presencia** en tiempo real (cuando te conectas, te pones ausente, etc.).

## Favoritos ★

En el perfil de un amigo pulsa **★ Favorito** (o `/friend favorite <jugador>`). Los favoritos salen **primero** en tu lista.

## Notas privadas

En el perfil de un amigo hay un campo **Nota privada**: un apunte solo para ti ("le debo una armadura de netherita").
Nadie más puede verlo. Por comando: `/friend note <jugador> <texto>`.

## Quitar a un amigo

Botón **Quitar amigo** o `/friend remove <jugador>`. La amistad se rompe en ambos sentidos.

## Ver tu lista

`/friend list` muestra tus amigos con su símbolo de estado, ★ si son favoritos, su estado personalizado y tu nota.

## Bloquear

Si alguien te molesta, **bloquéalo**: botón **Bloquear** en su perfil, `/block <jugador>` o `/friend block <jugador>`.

Al bloquear a alguien, **el servidor** se encarga de que esa persona:

| No pueda... | Detalle |
|---|---|
| Enviarte mensajes privados | Y tú tampoco podrás escribirle hasta que lo desbloquees. |
| Invitarte a grupos o parties | La invitación se descarta **en silencio**: ella no sabe que la has bloqueado. |
| Mencionarte | Sus `@menciones` no te notifican. |
| Ver tu estado | Para ella apareces siempre como desconectado. |
| Enviarte solicitudes de amistad | Se descartan también en silencio. |

Además, **se rompe la amistad** entre ambos y los mensajes que esa persona escriba en canales de grupo **no se te muestran**.

Para ver a quién has bloqueado: `/friend blocked`. Para deshacerlo: **Desbloquear** o `/unblock <jugador>`.

> El bloqueo es privado. La otra persona nunca recibe un aviso de que la has bloqueado; solo ve que sus mensajes no
> llegan o que no puede escribirte.

## ¿Y si alguien me acosa?

Además de bloquear, puedes **reportar** el mensaje concreto (pulsa el mensaje → **Reportar**). El staff recibe el reporte con
los mensajes de alrededor para entender el contexto. Ver [Moderación](16-moderacion.md).
