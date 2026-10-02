# 16. Moderación

Toda acción se **valida en el servidor** y las de moderación quedan en `<mundo>/socialmod/audit.log`. Los comandos de staff exigen
permisos de LuckPerms (o OP nivel 2 sin proveedor).

## Qué se protege solo

- **Permisos y pertenencia**: nadie puede escribir en un canal que no es suyo ni actuar fuera de su rol.
- **Longitud** de mensajes (256 por defecto) y saneado de texto (controles, `§`, caracteres invisibles o de dirección).
- **Rate limit** de paquetes por jugador; los malformados se ignoran y se anotan.
- **Anti-spam**: límite de mensajes por ventana, detección de repeticiones y **silencio automático** temporal.
- **Filtro de palabras**: lista, expresiones regulares y datapacks; modo `censor` (sustituye por `*****`) o `block` (rechaza).
- **Bloqueos y privacidad** de cada jugador.
- **Filtros externos** que otros mods registran con la API.

## Comandos de staff

| Comando | Para qué |
|---|---|
| `/socialmod mod mute <jugador> <segundos> [motivo]` | Silenciar (0 = indefinido). El jugador recibe aviso con el motivo. |
| `/socialmod mod unmute <jugador>` | Quitar el silencio. |
| `/socialmod mod history dm <a> <b>` | Últimos 30 mensajes entre dos jugadores. |
| `/socialmod mod history group <TAG> <canal>` | Últimos 30 mensajes de un canal. |
| `/socialmod mod inspect <jugador>` | Perfil: estado, amigos, bloqueados, grupos con rol, silencio, no leídos, privacidad. |
| `/socialmod mod disband <grupo>` | Disolver un grupo (borra su historial). |
| `/socialmod mod reports` | Últimos reportes con su contexto. |
| `/socialmod mod spy` | Activar/desactivar la lectura de privados (solo si el servidor lo permite). |
| `/socialmod data export <jugador>` | Exportar los datos de un jugador. |
| `/socialmod reload` | Recargar la configuración. |

Consultar un historial **queda registrado** con quién lo hizo.

## Reportes

1. Un jugador pulsa un mensaje y elige **Reportar** (o `/socialmod report "<conversación>" <id>`).
2. Se guarda en `reports/` con el mensaje reportado y **5 mensajes antes y después** (configurable con `moderation.reportContext`).
3. El staff conectado con el permiso `socialmod.mod.reports` recibe una **notificación**.
4. Se consulta con `/socialmod mod reports`.

No se puede reportar un mensaje propio.

## Silencios

- Un silenciado **no puede enviar** privados ni mensajes de grupo (sí recibe).
- Es **temporal** (en segundos) o indefinido (`0`).
- Su panel y su HUD muestran que está silenciado, y su campo de texto avisa.
- El **anti-spam** puede silenciar automáticamente (por defecto 60 s tras 3 infracciones en un minuto); queda en auditoría como `auto`.

## Modo spy (opcional y visible)

Por defecto está **apagado**. Si lo activas con `moderation.spyEnabled: true`:

- El staff con `socialmod.mod.spy` puede activarlo con `/socialmod mod spy` y recibe en su chat los privados de otros como
  `[Spy] A → B: texto` (nunca los suyos propios).
- **Todos los jugadores ven un aviso naranja permanente** en el panel: *"El modo spy está activo en este servidor"*.
- Activarlo y desactivarlo queda en auditoría.

Úsalo solo si tu servidor lo necesita y avisa a tu comunidad en las normas.

## Registro de auditoría

`audit.log`, una línea por acción con fecha ISO: amistades y bloqueos, creación y disolución de grupos, uniones y salidas, cambios
de rol, edición de grupos, canales, silencios, reportes, consultas de historial, inspecciones, spy, exportaciones y borrados.

```text
2026-10-02T22:01:06Z MUTE Staff Alex 600s spam
2026-10-02T22:03:40Z GROUP_ROLE Alex Luna recruit->member k3j2h1ab
2026-10-02T22:10:12Z HISTORY Staff dm:...:...
```

Se puede desactivar con `moderation.auditLog: false`, aunque no se recomienda.

## Datos personales

- **Exportar**: el jugador usa `/socialmod data export` o el botón de Ajustes; tú le entregas el archivo `exports/<uuid>.json.gz`.
- **Borrar**: `/socialmod data delete confirm` elimina su perfil, relaciones y el **texto** de sus mensajes (quedan como
  "borrado" para no romper el hilo de los demás).
- Puedes desactivar ambas con `moderation.allowDataExport` y `moderation.allowDataDelete` si la normativa de tu servidor lo exige.

## Buenas prácticas

- Publica las **normas** y qué se registra (historial, spy si lo usas).
- Empieza con el filtro en modo `censor` y ajusta la lista con el uso.
- Usa permisos **por rangos**: ayudantes con `mod.mute` y `mod.reports`; moderadores con `history` e `inspect`; solo administradores con `spy`.
- Revisa `audit.log` periódicamente.
