# 14. Preguntas frecuentes

### ¿Necesito instalar el mod en mi cliente?
No es obligatorio. El **servidor** necesita SocialMod; tu cliente solo lo necesita si quieres la interfaz (panel, toasts,
Respuesta Rápida). Sin él puedes hacer todo con [comandos](11-sin-el-mod.md).

### Pulso `K` y no pasa nada.
Puede ser que (1) el servidor no tenga SocialMod, (2) tenga una **versión de protocolo distinta** (actualiza cliente y servidor a
la misma versión del mod), o (3) tengas la tecla cambiada en Controles. Si ves el aviso *"Este servidor no tiene SocialMod"*, es el caso 1 o 2.

### Dice "Jugador desconocido".
Solo puedes hablar con quien haya entrado alguna vez en este servidor. Comprueba la ortografía del nombre.

### Le escribí a alguien desconectado, ¿lo recibirá?
Sí, si el servidor tiene el **buzón** activado (lo normal). Le llegará al conectarse, con un resumen.

### ¿Por qué no puedo escribirle a esa persona?
Puede ser que solo admita mensajes de **amigos** o de **nadie**, que te haya **bloqueado** (no se te dice), que la hayas bloqueado
tú, o que estés **silenciado**. Ver la tabla de [Mensajes privados](03-mensajes-privados.md).

### ¿Si me bloquean, me entero?
No. Verás que no puedes escribirle o que tus solicitudes no llegan, pero nunca hay un aviso.

### ¿Puedo ver quién ha leído mi mensaje en un grupo?
No. El "Leído" solo existe en **privados**, y solo si ambas personas lo tienen activado.

### Edité un mensaje y ya no me deja.
Solo se puede editar o borrar durante los **2 minutos** siguientes (el servidor puede cambiar el tiempo).

### ¿Qué diferencia hay entre grupo y party?
La party es temporal (se pierde al terminar la sesión); el grupo es permanente, con roles, canales y etiqueta. Ver la
[comparativa](06-parties.md).

### No puedo unirme a un grupo.
Hace falta **invitación**. Además puede estar lleno, o puede que ya estés en el máximo de grupos permitido (por defecto 3).

### ¿Cómo cambio qué grupo sale como etiqueta sobre mi nombre?
**Ajustes del grupo → Hacer principal**, o `/g main <TAG>`.

### Mi etiqueta `[TAG]` no aparece sobre mi cabeza.
La ven los jugadores que **tienen el mod**, **debajo de tu nombre**, y no se muestra a gran distancia, si te agachas ni si
eres invisible. Tampoco la ves tú en tu propia pantalla en primera persona. Comprueba *Ajustes → Interfaz → Etiquetas*.

### Pulso el estado en Ajustes y no cambia.
Era un error de la 0.1.0 (el servidor no devolvía el estado nuevo). Desde la 0.2.0 cambia al instante. Si sigue igual,
comprueba que el servidor también tenga la 0.2.0: con versiones distintas el cliente entra en modo "solo chat".

### Las notificaciones muestran `[coords]` o `[ITEM]` en lugar de las coordenadas.
Corregido en la 0.2.0: ahora se ve `x: 120, z: -450` o el nombre del ítem, y `[CORDS]`/`[ITEM]` en mayúsculas también
funcionan. Hace falta la 0.2.0 en el servidor (la vista previa la prepara él).

### Una tecla de SocialMod choca con otro mod.
Si la tecla sigue en su valor por defecto, SocialMod la mueve sola a una libre y te avisa. Si la cambiaste tú, cámbiala en
*Opciones → Controles → SocialMod*.

### Los toasts no aparecen mientras peleo.
Es el **No molestar inteligente**: esperan durante el combate y mientras tienes una pantalla abierta, y luego te enseñan un resumen.
Desactívalo en Ajustes si prefieres verlos al instante.

### ¿Puedo usarlo con minimapas (Xaero, JourneyMap)?
Sí. Con **Xaero's Minimap** (y World Map) o **JourneyMap**, un doble clic en unas coordenadas compartidas (o el botón
**Waypoint**) crea un waypoint. Con Xaero's Minimap, la primera vez SocialMod aparta los toasts y el HUD si comparten
esquina con el minimapa; puedes ajustarlo en **Ajustes → Mapas**. Más detalles en [Compartir](10-compartir.md) e
[Integraciones](18-integraciones.md).

### ¿Es compatible con Sodium, Iris, Lithium, JEI, REI...?
Sí. SocialMod **no usa mixins** y dibuja con las herramientas normales de la interfaz, así que no interfiere con mods de render.
Se prueba en la CI junto con Lithium y FerriteCore.

### ¿Y con otros mods de chat (Chat Heads, Styled Chat...)?
SocialMod **no toca el chat vanilla**. Sus mensajes para jugadores sin el mod son mensajes de sistema normales, así que esos mods
los trataran como cualquier otro.

### ¿Hay voz en los grupos?
Sí, con **Simple Voice Chat** en el servidor y en tu cliente: **☏** en la cabecera del chat del grupo o de la party, o
`/g voice` y `/party voice`. Plasmo Voice está pendiente (ver [Integraciones](18-integraciones.md)).

### No tengo el mod, ¿hay algo más cómodo que los comandos?
Sí: `/social` abre un **menú de cofre** con tus amigos (color según su estado), tu estado (clic para cambiarlo), el buzón y
tus grupos. Funciona en Java vanilla y en Bedrock (Geyser).

### ¿Cómo reporto a alguien?
Abre la conversación, **pulsa el mensaje** y elige **Reportar**. El staff lo recibe con los mensajes de alrededor.

### ¿Pueden leer mis mensajes privados?
El staff puede consultar historiales para moderar (queda en el registro de auditoría), y si el servidor activa el modo **spy**
verás un **aviso naranja** en el panel. No hay cifrado de extremo a extremo. Ver [Privacidad y seguridad](12-privacidad-y-seguridad.md).

### Quiero borrar todo lo mío.
`/socialmod data delete` y luego `/socialmod data delete confirm`, si el servidor lo permite.

### ¿Cuántos amigos / grupos / miembros puedo tener?
Por defecto 200 amigos, 3 grupos por jugador, 50 miembros por grupo, 8 canales por grupo y 8 jugadores por party. El servidor puede
cambiarlo, también por rango con LuckPerms.

### Me dice "Estás enviando mensajes demasiado rápido".
Es el anti-spam. Espera unos segundos. Si lo repites mucho, te silencia automáticamente un minuto.
