# 12. Privacidad y seguridad

## Qué guarda el servidor

SocialMod guarda los datos **en el servidor** (en la carpeta `socialmod/` del mundo), no en tu ordenador. Eso permite entregar
mensajes a quien está desconectado, conservar tu historial si cambias de PC y que el staff pueda moderar.

| Se guarda | Detalle |
|---|---|
| Tu perfil social | Estado, privacidad, amigos, bloqueados, favoritos, notas, solicitudes, grupo principal |
| Tus grupos | Pertenencia, roles, canales |
| Mensajes | Privados y de grupo, **con retención limitada** (por defecto 30 días o los últimos 500 por conversación) |
| Reportes | Con unos mensajes de contexto, para el staff |
| Registro de auditoría | Acciones de moderación y de gestión de grupos |

> **Transparencia:** el panel te lo recuerda siempre con una línea fija: *"Los mensajes los gestiona el servidor y el staff puede
> consultarlos para moderar."* **No hay cifrado de extremo a extremo.** No uses SocialMod para secretos.

Los **mensajes de un jugador que se va** no se conservan para siempre: caducan según la retención del servidor.

## Lo que tú controlas

| Ajuste | Dónde | Opciones |
|---|---|---|
| Quién puede escribirme | Ajustes / `/socialmod privacy messages` | Todos · Amigos · Nadie |
| Quién ve mi estado | Ajustes / `/socialmod privacy status` | Todos · Amigos · Nadie |
| Mostrar mi dimensión | Ajustes | Desactivado por defecto |
| Confirmación de lectura ("Leído") | Ajustes | Activada |
| "Escribiendo..." | Ajustes | Activado |
| Estado | Ajustes / `/status` | Incluye **Invisible** |
| Bloquear jugadores | Perfil / `/block` | Ver [Amigos y bloqueos](04-amigos-y-bloqueos.md) |
| Etiquetas de grupo sobre los jugadores | Ajustes | Se pueden ocultar en tu cliente |

La **dimensión** y el **estado personalizado** solo se ven si tu privacidad lo permite. **Las coordenadas nunca** se publican por
sí solas.

## Qué NO puede hacer nadie con SocialMod

- **Suplantar** a otro jugador: el servidor comprueba quién eres en cada paquete; no se acepta ningún dato de identidad del cliente.
- **Escribir en una conversación ajena** o en un grupo del que no eres miembro.
- **Falsificar** un ítem o unas coordenadas compartidas: los genera el servidor.
- **Inyectar comandos o botones** en un mensaje: el JSON de texto de un cliente nunca se interpreta.
- **Colapsar el servidor** con paquetes: cada jugador tiene un límite de paquetes por segundo y los malformados se ignoran.
- **Colar caracteres de dirección o invisibles** para disfrazar un nombre: se eliminan.

## Moderación visible

Todo lo que el staff puede hacer está pensado para que lo sepas:

- Si un moderador te **silencia**, recibes un aviso con el motivo y el tiempo.
- Si el servidor activa el modo **spy** (lectura de privados por el staff), **el panel de todos los jugadores muestra un aviso
  naranja** *"El modo spy está activo en este servidor"*.
- Las acciones de moderación quedan en un **registro de auditoría**.

## Tus datos: exportar y borrar

Si el servidor lo permite:

| Quieres... | Haz |
|---|---|
| **Una copia** de tus datos (perfil, grupos y todos tus mensajes) | **Ajustes → Exportar mis datos** o `/socialmod data export`. Se guarda un archivo en el servidor; pide el archivo al staff. |
| **Borrar** tus datos sociales | `/socialmod data delete` y, para confirmar, `/socialmod data delete confirm`. |

Al borrar: sales de tus grupos, desaparecen tus amistades y bloqueos, y **el texto de tus mensajes se elimina** del historial
(queda "mensaje borrado"). Es irreversible.

## Consejos de seguridad básicos

- Desconfía de los **enlaces** de desconocidos: Minecraft te pide confirmación antes de abrirlos, léela.
- Si alguien te acosa: **bloquea** y **reporta** el mensaje concreto.
- Usa **Invisible** o **Nadie** en "quién ve mi estado" si no quieres que te localicen.
