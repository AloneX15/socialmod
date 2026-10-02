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
La ven los jugadores que **tienen el mod**, y no se muestra a gran distancia, si te agachas ni si eres invisible. Tampoco la ves
tú en tu propia pantalla en primera persona. Comprueba que *Ajustes → Etiquetas* esté activado.

### Los toasts no aparecen mientras peleo.
Es el **No molestar inteligente**: esperan durante el combate y mientras tienes una pantalla abierta, y luego te enseñan un resumen.
Desactívalo en Ajustes si prefieres verlos al instante.

### ¿Puedo usarlo con minimapas (Xaero, JourneyMap)?
Sí, conviven. Si tu minimapa tapa los toasts, cambia su **posición** en Ajustes o sube el **margen para minimapas**. El
waypoint automático desde `[coords]` llegará en una versión futura; de momento se **copian** las coordenadas.

### ¿Es compatible con Sodium, Iris, Lithium, JEI, REI...?
Sí. SocialMod **no usa mixins** y dibuja con las herramientas normales de la interfaz, así que no interfiere con mods de render.
Se prueba en la CI junto con Lithium y FerriteCore.

### ¿Y con otros mods de chat (Chat Heads, Styled Chat...)?
SocialMod **no toca el chat vanilla**. Sus mensajes para jugadores sin el mod son mensajes de sistema normales, así que esos mods
los trataran como cualquier otro.

### ¿Hay voz en los grupos?
Todavía no. La integración con Simple Voice Chat y Plasmo Voice está prevista en la fase 5 del plan.

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
