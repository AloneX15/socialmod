# 13. Accesibilidad y personalización

SocialMod se puede adaptar a tu forma de jugar en **tres capas**: el servidor decide las reglas, los **packs de recursos**
deciden el aspecto y **tú** decides tus preferencias.

## Tus preferencias

Abre **Panel → Ajustes** (o *Mods → SocialMod → Configurar* si tienes ModMenu, incluso desde el menú principal).

### Columna "Estado y privacidad (servidor)"

Estado, estado personalizado, quién te escribe, quién ve tu estado, mostrar dimensión, confirmación de lectura, indicador de
"escribiendo" y exportar tus datos. Se guardan **en el servidor**.

### Columna "Este cliente"

Se aplican **al instante** y se guardan en `config/socialmod/client.json`:

| Ajuste | Qué cambia |
|---|---|
| **Toasts** | Activarlos o no |
| **Posición / Duración / Máx. toasts / Animaciones** | Cómo y dónde aparecen |
| **No molestar intel.** | Esperar durante combate y pantallas abiertas |
| **Toasts de grupo** | Avisar o no de cada mensaje de grupo |
| **Volumen** | Sonido de notificaciones |
| **HUD / Posición / Escala** | El widget de mensajes sin leer |
| **Etiquetas** | Mostrar los `[TAG]` sobre los jugadores |
| **Narrador** | Leer los toasts en voz alta |
| **Alto contraste** | Colores más fuertes y bordes blancos |
| **Margen para minimapas** | Espacio que el panel deja libre a la derecha |

## Accesibilidad

- **Narrador de Minecraft**: si lo tienes activado, los toasts y las notificaciones se leen en voz alta.
- **No dependas del color**: cada estado tiene una **forma propia** además del color — ● en línea, ◐ ausente, ⊘ no molestar,
  ○ desconectado o invisible.
- **Alto contraste**: texto blanco puro, fondos casi opacos y bordes blancos.
- **Texto**: el panel usa la **escala de GUI** que tengas en Minecraft; sube *GUI Scale* para letra más grande. El diseño se
  reorganiza solo (tres columnas, dos, o pestañas) para que siempre quepa.
- **Todo con teclado**: abrir el panel (`K`), responder (`Y`), enviar (`Enter`), autocompletar (`Tab`), recorrer lo enviado (`↑ ↓`).
- **Todo con comandos** como alternativa a la interfaz ([ver la lista](11-sin-el-mod.md)).

## Teclas

En *Opciones → Controles → SocialMod* puedes cambiar:

| Acción | Por defecto |
|---|---|
| Respuesta Rápida (`Mayús` = abrir chat) | `Y` |
| Abrir el panel social | `K` |

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
- **Sonidos**: redefine `assets/socialmod/sounds.json` (`notify.private`, `notify.mention`, `notify.invite`, `notify.event`,
  `notify.group`, `notify.system`).
- **Idiomas**: añade o corrige archivos en `assets/socialmod/lang/`. Vienen inglés y español (España, México, Argentina).

## Para modpacks

Un modpack puede incluir `config/socialmod/client-defaults.json` con los mismos campos que `client.json`: se aplica como valores
**por defecto** sin pisar los cambios que haga el jugador.
