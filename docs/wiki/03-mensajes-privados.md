# 3. Mensajes privados

## Enviar un mensaje

- **Con el mod**: abre el perfil de alguien (o pulsa su conversación) y escribe en la barra inferior.
- **Sin el mod, o desde el chat**: `/pm <jugador> <mensaje>` (también vale `/dm`).
- **Responder**: `/r <mensaje>` contesta a la última persona con la que hablaste. En la UI basta con seguir en la conversación.

## El buzón: escribir a quien no está conectado

Puedes escribir a **cualquier jugador que haya entrado alguna vez** en el servidor, aunque esté desconectado. El mensaje se
guarda y se le entrega cuando vuelve:

> *Tienes 3 mensajes de Alex*

- Con el mod: verás un contador naranja junto a la conversación y en el widget del HUD (✉ 3), y el historial completo al abrirla.
- Sin el mod: aparece el resumen al conectarte con un enlace **[Leerlos]**, o escribe `/socialmod inbox`.
- Los mensajes sin leer **no caducan** hasta que los leas o salte la retención del servidor (por defecto 30 días o los últimos
  500 mensajes de cada conversación).

> Si tienes el chat desactivado (en las opciones de chat o por restricciones de tu cuenta Microsoft), no recibes mensajes
> en directo: quedan guardados en tu buzón para cuando lo actives.

## Formato del texto

Puedes usar un conjunto **pequeño y seguro** de formato:

| Escribes | Se ve |
|---|---|
| `**negrita**` | **negrita** |
| `*cursiva*` | *cursiva* |
| `` `código` `` | texto gris tipo código |
| `https://ejemplo.com` | enlace pulsable (pide confirmación; el servidor puede desactivarlos) |
| `@Alex` | mención resaltada en dorado, con notificación prioritaria |
| `\*` o `\@` | muestra el símbolo tal cual, sin formato |

Por seguridad, **nunca se interpreta JSON de texto** enviado por un jugador. Nadie puede inyectar botones de clic ni
comandos camuflados en un mensaje.

Los mensajes tienen un máximo de **256 caracteres** (el servidor puede cambiarlo). Se eliminan caracteres de control, códigos
`§` y caracteres invisibles o de dirección que se usan para suplantar nombres.

## Menciones

Escribe `@` seguido de un nombre. Con **Tab** se autocompleta. La persona mencionada recibe una notificación **prioritaria**
(borde dorado y se muestra por encima de los mensajes normales). Además, las menciones **sí atraviesan tu estado
"No molestar"**, que silencia el resto de notificaciones.

En un grupo también puedes escribir `@TAG` (por ejemplo `@TF`) o `@everyone` para mencionar a **todos los miembros** del canal.
Esto solo lo pueden hacer los roles con permiso de fijar mensajes (por defecto, oficiales y líder), para evitar abusos.

Un jugador que te ha bloqueado **no recibe** tus menciones.

## Editar y borrar

Durante los **2 minutos** siguientes a enviar un mensaje (configurable por el servidor) puedes:

- **Editarlo**: se marca como *(editado)*.
- **Borrarlo**: desaparece para todos y queda como *mensaje borrado*.

Pasado ese tiempo ya no puedes (salvo que seas staff con permiso de moderación).

## "Escribiendo..." y "Leído"

- Cuando alguien te escribe, ves **"Alex está escribiendo..."**.
- En privados, cuando la otra persona abre la conversación, ves **✔ Leído** bajo tu último mensaje.

Ambas funciones se pueden **desactivar en Ajustes** (indicador de escribiendo y confirmación de lectura). Funcionan de forma
**recíproca**: si tú no envías confirmaciones, tampoco las recibes.

## Quién puede escribirte

En *Ajustes → Quién puede escribirme* eliges entre **Todos**, **Amigos** o **Nadie**. Y recuerda que un jugador **bloqueado**
no puede escribirte nunca. Ver [Privacidad y seguridad](12-privacidad-y-seguridad.md).

## Límites contra el spam

Para proteger a todos, el servidor limita cuántos mensajes puedes mandar seguidos (por defecto 5 cada 4 segundos), impide repetir
el mismo mensaje una y otra vez y, si insistes, te **silencia automáticamente** durante un minuto. Verás un aviso explicando por
qué no se envió.

## Si no puedes escribir a alguien

| Mensaje que ves | Qué significa |
|---|---|
| *Jugador desconocido* | Esa persona nunca ha entrado en este servidor. |
| *No puedes enviar mensajes a X* | X solo admite mensajes de amigos o de nadie. |
| *Has bloqueado a X* | Desbloquéalo primero. |
| *Estás silenciado* | Un moderador (o el anti-spam) te ha silenciado temporalmente. |
| *El filtro del servidor ha bloqueado tu mensaje* | Contiene una palabra o patrón prohibido. |
| *Tu chat está desactivado* | Actívalo en las opciones de chat de Minecraft. |
