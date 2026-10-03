# 7. Estado y presencia

La **presencia** es lo que los demás ven de ti: si estás disponible, ocupado o fuera. Cada estado tiene un **color y una forma
distinta**, así que se distinguen aunque no veas bien los colores.

## Los estados

| Símbolo | Estado | Significado |
|---|---|---|
| **●** (verde) | **En línea** | Disponible. |
| **◐** (naranja) | **Ausente** | Llevas un rato sin hacer nada. **Lo detecta el servidor automáticamente** (por defecto tras 5 minutos). |
| **⊘** (rojo) | **No molestar** | No quieres notificaciones (las menciones e invitaciones aún te llegan). |
| **○** (gris) | **Desconectado / Invisible** | Los demás te ven como desconectado. |

### Invisible

Eliges **Invisible** y para los demás eres como si estuvieras desconectado, pero sigues pudiendo usar todo. El staff con permiso
de inspección sí puede ver que estás invisible.

## Cambiar tu estado

- **Panel → Ajustes → Estado**: cada pulsación rota entre *En línea → Ausente → No molestar → Invisible*. El botón cambia al
  instante y el servidor lo confirma (en la 0.1.0 el botón se quedaba igual; corregido en la 0.2.0). Lo mismo con el resto
  de opciones de esa columna (privacidad, mostrar dimensión, confirmación de lectura, "escribiendo").
- **Sin el mod**: `/social` → clic en el tinte de estado (arriba a la izquierda).
- **Comando**: `/status online`, `/status away`, `/status dnd` o `/status invisible`.

Ponerte **Ausente** a mano es útil si te vas un rato aunque no pase el tiempo de inactividad.

## Estado personalizado

Una frase corta bajo tu nombre ("Construyendo la base", "Minando — vuelvo en 10"):

- **Ajustes** → campo *Estado personalizado* → **Guardar**.
- **Comando**: `/status text <texto>`; para quitarlo, `/status clear`.

Máximo **48 caracteres** (el servidor puede cambiarlo) y se filtran los caracteres peligrosos igual que en los mensajes.

Algunos mods del servidor pueden aportar una **actividad automática** ("En una mazmorra") que se muestra cuando no tienes un
estado personalizado.

## Mostrar tu dimensión (opcional)

Por defecto **nadie** sabe en qué dimensión estás. Si quieres que tus amigos vean si estás en el Overworld, el Nether o el End,
activa **Ajustes → Mostrar mi dimensión**. Nunca se muestran coordenadas por esta vía.

## Quién ve tu estado

En **Ajustes → Quién ve mi estado** eliges **Todos**, **Amigos** o **Nadie**. Un jugador al que has bloqueado siempre te ve como
desconectado.

## Cómo se envía (para curiosos)

La presencia **solo se manda a quien le interesa**: tus amigos, los miembros de tus grupos y quien tenga el panel abierto
en ese momento. Los cambios se agrupan y se envían como máximo cada 250 ms. Esto hace que el mod funcione bien incluso con
servidores de más de cien jugadores.

## Estado que ves en otros

- En la columna **Directos** y en la lista **En línea** ves el símbolo junto a cada nombre.
- En el **perfil** ves el estado en texto y su frase personalizada.
- En el **widget del HUD** ves el tuyo (ver [Notificaciones](08-notificaciones.md)).
- `/friend list` muestra el símbolo de cada amigo en el chat.
