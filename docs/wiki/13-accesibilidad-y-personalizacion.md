# 13. Accesibilidad y personalización

SocialMod se puede adaptar a tu forma de jugar en **tres capas**: el servidor decide las reglas, los **packs de recursos**
deciden el aspecto y **tú** decides tus preferencias.

## Tus preferencias

Abre **Panel → Ajustes** (o *Mods → SocialMod → Configurar* si tienes ModMenu, incluso desde el menú principal).

### Columna "Estado y privacidad (servidor)"

Estado, estado personalizado, quién te escribe, quién ve tu estado, mostrar dimensión, confirmación de lectura, indicador de
"escribiendo" y exportar tus datos. Se guardan **en el servidor**.

### Columna "Este cliente"

Se aplican **al instante** y se guardan en `config/socialmod/client.json`. Están en tres pestañas:

**Avisos**

| Ajuste | Qué cambia |
|---|---|
| **Toasts** / **Posición** / **Duración** / **Máx. toasts** / **Animaciones** | Cómo y dónde aparecen |
| **Silencio inteligente** | Esperar durante combate y pantallas abiertas |
| **Toasts de grupo** | Avisar o no de cada mensaje de grupo |
| **Volumen** | Sonido de notificaciones |
| **Narrador** | Leer los toasts en voz alta |
| **Sonido de ping** | Sonido cuando un compañero marca un punto |

**Interfaz**

| Ajuste | Qué cambia |
|---|---|
| **HUD** / **Posición** / **Escala HUD** | El widget de mensajes sin leer |
| **Vida de la party** | Barras de vida de los compañeros de party |
| **Etiquetas** / **Etiqueta debajo** / **Emblema** / **Rol** | Cómo se ve la etiqueta de grupo sobre los jugadores |
| **Texto** | Escala propia del texto de los mensajes del chat (75–150 %) |
| **Alto contraste** | Colores más fuertes y bordes blancos |

**Mapas**

| Ajuste | Qué cambia |
|---|---|
| **Waypoints** | Crear waypoints en Xaero's Minimap/JourneyMap desde coordenadas compartidas |
| **Ping** | Activar la tecla de ping |
| **Margen der.** / **Margen sup.** | Espacio que el panel deja libre (minimapas) |
| **Toast X** / **Toast Y** | Separación de los toasts respecto al borde |
| **Auto minimapa** | Con Xaero's Minimap, mover toasts y HUD la primera vez para no taparlo (desactivar y activar lo vuelve a medir) |
| **Caché local** | Guardar el último estado de cada servidor para abrir el panel al instante (al desactivarla se borra) |

## Accesibilidad

- **Narrador de Minecraft**: si lo tienes activado, los toasts y las notificaciones se leen en voz alta.
- **No dependas del color**: cada estado tiene una **forma propia** además del color — ● en línea, ◐ ausente, ⊘ no molestar,
  ○ desconectado o invisible.
- **Alto contraste**: texto blanco puro, fondos casi opacos y bordes blancos.
- **Texto**: **Ajustes → Interfaz → Texto** agranda o reduce solo el texto de los mensajes (75–150 %) sin cambiar el resto
  del juego. Para todo el panel, sube la *GUI Scale* de Minecraft: el diseño se reorganiza solo (tres columnas, dos, o
  pestañas) para que siempre quepa.
- **Colores legibles**: los colores que eligen los grupos se aclaran automáticamente si son demasiado oscuros para el fondo.
- **Todo con teclado**: abrir el panel (`K`), responder (`Y`), enviar (`Enter`), autocompletar (`Tab`), recorrer lo enviado (`↑ ↓`).
- **Todo con comandos** como alternativa a la interfaz ([ver la lista](11-sin-el-mod.md)).

## Teclas

En *Opciones → Controles → SocialMod* puedes cambiar:

| Acción | Por defecto |
|---|---|
| Respuesta rápida (`Mayús` = abrir chat) | `Y` |
| Abrir el panel social | `K` |
| Ping para la party | `J` |

Si otro mod usa la misma tecla y la tuya sigue en su valor por defecto, SocialMod la mueve sola a una libre y te lo dice en
el chat (por ejemplo, con Xaero's Minimap, que usa `Y` para sus ajustes, Respuesta rápida pasa a `H`). Si la cambiaste tú,
solo te avisa.

## Packs de recursos (temas)

Un pack de recursos puede cambiar el aspecto del panel y de los toasts sin tocar código. Se recarga con **F3 + T**.

`assets/socialmod/themes/default.json`:

```json
{
  "layout": "three_column",
  "columns": [
    { "id": "conversations", "weight": 1, "min_width": 110 },
    { "id": "chat",          "weight": 2, "min_width": 160 },
    { "id": "players",       "weight": 1, "min_width": 90, "collapsible": true }
  ],
  "colors": {
    "text": "#E0E0E0", "muted": "#909090", "accent": "#55FF55", "unread": "#FFAA00",
    "background": "#C0000000", "panel": "#80101010", "border": "#404040", "highlight": "#40FFFFFF"
  },
  "toast": { "background": "#E0101010", "border": "#555555", "width": 160 }
}
```

- Las columnas usan **pesos y anchos mínimos**, no posiciones fijas, así que valen con cualquier escala o idioma.
- Los colores son `#RRGGBB` o `#AARRGGBB`.
- Si el JSON tiene un error, se anota en el registro con el archivo y se usa el tema por defecto: **nunca crashea**.
- **Texturas** (desde la 0.2.0): añade `"textures": { "background": "mipack:socialmod/fondo", "panel": "mipack:socialmod/panel",
  "toast": "mipack:socialmod/toast" }` (las tres son opcionales). Son sprites de la GUI: el PNG va en
  `assets/mipack/textures/gui/sprites/socialmod/panel.png` y, para que se estire bien, un `panel.png.mcmeta` con
  `{"gui": {"scaling": {"type": "nine_slice", "width": 32, "height": 32, "border": 4}}}`. Con **Alto contraste** se ignoran.
- **Sonidos**: redefine `assets/socialmod/sounds.json` (`notify.private`, `notify.mention`, `notify.invite`, `notify.event`,
  `notify.group`, `notify.system`, `ping`).
- **Idiomas**: añade o corrige archivos en `assets/socialmod/lang/`. Vienen inglés y español (España, México, Argentina).

## Para modpacks

Un modpack puede incluir `config/socialmod/client-defaults.json` con los mismos campos que `client.json`: se aplica como valores
**por defecto** sin pisar los cambios que haga el jugador.
