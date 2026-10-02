# 5. Grupos y clanes

Un **grupo** es un clan persistente: sigue existiendo aunque nadie esté conectado, con su propio chat, sus roles y su identidad.

## Crear un grupo

- **Panel**: **+ Nuevo grupo** → nombre y etiqueta → **Crear grupo**.
- **Comando**: `/g create <TAG> <nombre>`, por ejemplo `/g create TF Team Forest`.

Requisitos: nombre de **3 a 24 caracteres**, etiqueta de **2 a 5 letras o números**, y que ni el nombre ni la etiqueta estén
en uso. Por defecto puedes estar en **3 grupos** (el servidor, o LuckPerms por rango, puede cambiarlo).

Quien lo crea es el **Líder**. Se crea con los canales por defecto del servidor (normalmente `#general`).

## Identidad del grupo

| Elemento | Cómo se cambia | Quién puede |
|---|---|---|
| **Etiqueta** (`[TF]`) | Ajustes del grupo o `/g tag <TAG>` | Roles con "editar información" |
| **Color** | Ajustes o `/g color <#RRGGBB o nombre>` (ej. `#55FF55`, `gold`) | Idem |
| **Descripción** | Ajustes o `/g description <texto>` | Idem |
| **Mensaje del día** | Ajustes o `/g motd <texto>` | Idem |
| **Mensaje fijado** | Ajustes o `/g pin <texto>` | Roles con permiso de fijar |

- El **mensaje del día** se muestra a cada miembro **cada vez que se conecta**.
- El **mensaje fijado** aparece siempre arriba del chat del grupo.

### La etiqueta junto a tu nombre

Los jugadores que tienen el mod ven `[TF]` (con el color del grupo) delante de tu nombre sobre tu cabeza. Se muestra la del
**grupo principal**. Respeta las reglas normales de Minecraft: no se ve a mucha distancia, ni si te agachas, y **nunca revela a
un jugador invisible**. Cada jugador puede ocultar las etiquetas en sus Ajustes.

Si estás en varios grupos, elige cuál se muestra con **Hacer principal** (Ajustes del grupo) o `/g main <TAG>`.

## Roles

| Rol | Letra | Por defecto puede... |
|---|---|---|
| **Líder** | `[L]` | Todo, incluida la gestión de roles, transferir y disolver el grupo. |
| **Oficial** | `[O]` | Invitar, expulsar, gestionar canales, fijar mensajes, editar información y gestionar eventos. |
| **Miembro** | `[M]` | Invitar y escribir en los canales abiertos. |
| **Recluta** | `[R]` | Escribir en los canales abiertos. Es el rol de quien acaba de entrar. |

> Los permisos de cada rol los decide el **servidor** (ver [Guía de administradores](15-guia-de-administradores.md)), así que en
> tu servidor pueden ser distintos.

### Reglas de jerarquía

- Nadie puede expulsar ni modificar a alguien de **rango igual o superior**.
- **Ascender / Degradar** (`/g promote`, `/g demote`) mueve a alguien un escalón: Recluta ↔ Miembro ↔ Oficial.
- A **Líder** solo se llega mediante **transferencia** (`/g transfer <jugador>`); el líder pasa a ser Oficial.
- Si el **líder se va**, el liderazgo pasa automáticamente al miembro de mayor rango. Si sale el último miembro, el grupo se disuelve.

## Unirse a un grupo

1. Un miembro con permiso te **invita** (perfil → *Invitar a [TAG]*, o `/g invite <jugador>`).
2. Te llega una notificación y la invitación aparece en **SOLICITUDES**.
3. **Acepta** con ✔ o `/g accept <TAG>`; para rechazar, ✖ o `/g decline <TAG>`.

No hay unión libre: **siempre hace falta invitación**. Tampoco puedes entrar si el grupo está lleno (por defecto, 50 miembros).

## Canales

Un grupo puede tener varios **canales** (`#general`, `#comercio`, `#eventos`...), como en un chat de equipo:

- **Crear**: Ajustes del grupo → *Nuevo canal* + **rol mínimo**, o `/g channel create <nombre> [rol]`.
  El **rol mínimo** decide quién puede leer y escribir; un canal `#oficiales` con rol *Oficial* es invisible para los demás.
- **Borrar**: ✖ junto al canal o `/g channel delete <nombre>` (borra también su historial). Siempre debe quedar al menos uno.
- **Escribir**: eliges el canal en la columna izquierda. Con comandos: `/g <mensaje>` (primer canal del grupo principal),
  `/g ch <canal> <mensaje>` o `/g to <TAG> <canal> <mensaje>` (cualquier grupo tuyo).
- Nombres de canal: letras minúsculas, números, `_` y `-`, hasta 16 caracteres. Por defecto, hasta **8 canales** por grupo.

## Eventos programados

Para quedar en algo concreto ("ir al End", "raid a la fortaleza"):

- **Crear**: Ajustes del grupo → minutos + título → **Añadir**, o `/g event <minutos> <título>`.
- Los miembros conectados reciben un aviso **5 minutos antes** y otro **cuando empieza**.
- Los eventos próximos aparecen en la columna derecha del panel y en `/g info`.
- Se borran solos una hora después de empezar. Por defecto, hasta 10 por grupo.

## Información y listados

- `/g info` (o `/g info <TAG>`): descripción, mensaje del día, fijado, miembros (en blanco los conectados), canales y eventos.
- `/g list`: tus grupos, con ★ en el principal.

## Salir, expulsar y disolver

| Acción | Cómo |
|---|---|
| **Salir** | Ajustes del grupo → *Salir*, o `/g leave [TAG]`. |
| **Expulsar** | Perfil del miembro → *Expulsar*, o `/g kick <jugador>`. |
| **Disolver** | Solo el líder: Ajustes → *Disolver* (pide confirmar), o `/g disband confirm`. **Borra todo el historial.** |

## Mensajes de sistema del grupo

Verás avisos como *"Alex se ha unido al grupo"*, *"Luna ahora es Oficial"* o *"Steve es el nuevo líder"* en tu chat, con la
etiqueta del grupo en su color.
