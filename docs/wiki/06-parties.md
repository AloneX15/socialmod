# 6. Parties

Una **party** es un grupo **temporal y rápido** para una sesión: ir al End, una mazmorra, una expedición al Nether... No tiene
nombre que elegir, etiqueta ni canales: es un chat común para los que participan, y **desaparece cuando acabáis**.

## Crear una party e invitar

- **Panel**: **+ Nueva party**, o en el perfil de alguien **Invitar a party** (crea la party si aún no tienes una).
- **Comandos**: `/party create` y `/party invite <jugador>` (invitar crea la party si no existe).

Solo puedes invitar a jugadores **conectados**. Por defecto, una party admite hasta **8 jugadores**.

## Unirse

La persona invitada recibe una notificación prioritaria. Para aceptar: ✔ en el panel o `/party accept`; para rechazar,
✖ o `/party decline`. Si ya estabas en otra party, **sales de ella** al aceptar.

## Hablar

- En el panel, la party aparece en **GRUPOS** como *Party*.
- Con comandos: `/p <mensaje>`.

## Vida de los compañeros

Con el mod, mientras estás en una party ves en el **borde izquierdo de la pantalla, a media altura**, el nombre y una barra
de vida de cada compañero conectado (hasta 8). La barra es gris si está en otra dimensión o muerto. Se actualiza dos veces
por segundo y solo cuando cambia. Se desactiva en **Ajustes → Interfaz → Vida de la party** (y el servidor puede apagarla con
el módulo `partyHud`). No tapa los minimapas (van en las esquinas) ni el chat.

## Ping: marcar un punto

Pulsa **J** mirando a un bloque (hasta 256 bloques): tus compañeros de party en la misma dimensión ven durante **10 s**:

- Un **haz de partículas** sobre el bloque marcado.
- Una línea en el HUD: **◆ Alex ↗ 42 m** (la flecha indica hacia dónde está respecto a donde miras).
- Un sonido corto (se quita en **Ajustes → Avisos → Sonido de ping**).

Los compañeros **sin el mod** reciben en el chat "Alex ha marcado un punto: [x y z]" (clic para copiar). Hay un ping cada
1,5 s como mucho. Se desactiva en **Ajustes → Mapas → Ping**; el servidor lo controla con el módulo `ping`.

## Chat de voz de la party

Con **Simple Voice Chat** en el servidor y en tu cliente: **☏** en la cabecera del chat de la party, o `/party voice`
(`/party voice leave` para salir). Solo pueden entrar los miembros de la party.

## Gestionar

| Acción | Cómo |
|---|---|
| Ver miembros | `/party list` |
| Expulsar | `/party kick <jugador>` (solo el líder) |
| Salir | `/party leave` |

Quien crea la party es su líder. Si se va, el liderazgo pasa a otro miembro.

## Cuándo desaparece

- Cuando **todos salen**.
- Cuando **ningún miembro queda conectado**.
- Cuando se reinicia el servidor: las parties **no se guardan** (son temporales a propósito).

## Party vs. grupo: ¿cuál uso?

| | Party | Grupo (clan) |
|---|---|---|
| Duración | Una sesión | Permanente |
| Historial | Solo mientras existe | Guardado según la retención del servidor |
| Roles y canales | No | Sí |
| Etiqueta sobre tu nombre | No | Sí |
| Invitar a desconectados | No | Sí (la invitación espera) |
| Ideal para | Salir de expedición con amigos | Organizar una comunidad o un clan |
