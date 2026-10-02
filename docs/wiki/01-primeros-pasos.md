# 1. Primeros pasos

## ¿Qué es SocialMod?

SocialMod añade una **capa social** a Minecraft sin sustituir el chat de siempre. El chat normal sigue funcionando; SocialMod
suma, por encima, lo que echabas de menos en un servidor con amigos o con mucha gente:

- Hablar en **privado** con cualquiera, incluso si no está conectado (el mensaje le espera).
- Tener **amigos** y saber quién está en línea, ausente o no quiere ser molestado.
- Crear un **clan** (grupo) con roles, canales de chat, mensaje del día y eventos programados.
- Formar un **equipo temporal** (party) para una expedición.
- Recibir **notificaciones** flotantes y contestarlas con una tecla, sin abrir ningún menú.
- Compartir **dónde estás** o **qué llevas en la mano** con un clic.
- **Bloquear** y **reportar** a quien moleste.

## ¿Mi servidor lo tiene?

SocialMod necesita estar instalado **en el servidor**. En tu cliente es **opcional**:

| Tienes el mod en tu cliente | Lo que obtienes |
|---|---|
| **Sí** | La interfaz completa: panel, toasts, tecla de respuesta rápida, perfiles, etiquetas de grupo sobre los jugadores... |
| **No** (vanilla o Bedrock con Geyser) | Todo lo importante funciona con **comandos** y los mensajes llegan al chat normal. Ver [Jugar sin el mod](11-sin-el-mod.md). |

Para comprobarlo con el mod instalado: pulsa **`K`**. Si se abre el panel social, el servidor lo tiene. Si ves el aviso
*"Este servidor no tiene SocialMod (usa el chat normal)"*, el servidor no lo tiene o usa una versión incompatible; en ese caso
no se rompe nada: simplemente juegas con el chat de siempre.

## Teclas por defecto

| Tecla | Acción |
|---|---|
| **`K`** | Abrir el panel social. |
| **`Y`** | **Respuesta Rápida**: contesta a la última notificación sin salir del juego. |
| **`Mayús + Y`** | Abrir el panel directamente en la conversación de la última notificación. |

Puedes cambiarlas en *Opciones → Controles → SocialMod*. Si alguna choca con la de otro mod, SocialMod te avisa al entrar
al mundo en lugar de quitarle la tecla al otro (`R` y `U` se evitaron a propósito porque las usan JEI, REI y EMI).

## Tu primer mensaje (30 segundos)

1. Pulsa **`K`** para abrir el panel.
2. En la columna de la derecha verás los jugadores conectados. Pulsa el nombre de un amigo.
3. En su perfil, pulsa **Mensaje**.
4. Escribe algo en la barra de abajo y pulsa **Enter**.

Sin el mod, lo mismo es: `/pm NombreDelAmigo hola, ¿quedamos?`

## Tu primer grupo (1 minuto)

1. Abre el panel con **`K`** y pulsa **+ Nuevo grupo** (abajo a la izquierda).
2. Escribe un nombre (mínimo 3 caracteres) y una etiqueta de 2 a 5 letras o números, por ejemplo `TF`.
3. Pulsa **Crear grupo**.
4. Desde ese momento eres el **Líder**, y `[TF]` aparece junto a tu nombre sobre tu cabeza para quienes tengan el mod.
5. Invita a tus amigos con el botón **+ Invitar** o con `/g invite NombreDelAmigo`.

Sin el mod: `/g create TF Team Forest`.

## Dos cosas que conviene saber desde el principio

- **Los mensajes los guarda el servidor.** Eso permite entregarlos cuando vuelves a conectarte, ver el historial en cualquier
  ordenador y que el staff pueda moderar. El panel te lo recuerda con una línea fija. No hay cifrado de extremo a extremo:
  ver [Privacidad y seguridad](12-privacidad-y-seguridad.md).
- **Solo puedes hablar con jugadores que hayan entrado alguna vez en ese servidor.** Si un nombre sale como "desconocido",
  esa persona todavía no ha jugado allí.
