# 10. Compartir coordenadas e ítems

Escribe estas palabras clave en cualquier mensaje (privado, de grupo o de party) y **el servidor** las convierte en contenido
real. Como las genera el servidor, **nadie puede falsificarlas**: si el mensaje dice que llevas una espada de diamante, es que
la llevas.

## `[coords]` — tu posición

Escribe `[coords]` (o pulsa el botón **⌖**) y se inserta tu posición actual en ese momento:

> Alex: nos vemos en **[120, 64, -450]**

Lo que pueden hacer los demás:

- **Pasar el ratón** por encima: ven la dimensión, y si están en la **misma dimensión**, la **distancia y la dirección** desde
  donde están ("A 312 bloques hacia el NE").
- **Crear un waypoint** (si tienen Xaero's Minimap/World Map o JourneyMap): pulsando el mensaje dos veces, o
  seleccionándolo y pulsando el botón **Waypoint**. El waypoint lleva el nombre de quien lo compartió y el color del
  grupo, y aparece en el minimapa y en el mapa completo.
- **Sin mapa** (o si las coordenadas son de otra dimensión): el doble clic **copia** las coordenadas al portapapeles
  (`120 64 -450`). En el chat normal, un clic las copia.

> En las **notificaciones** (toasts) y en la lista de conversaciones las coordenadas se ven ya resueltas:
> `Alex: nos vemos en x: 120, z: -450`. Lo mismo con los ítems: `Luna: mirad, Espada de diamante`.

### Mapas compatibles

| Mapa | Qué hace SocialMod | Cómo |
|---|---|---|
| Xaero's Minimap (+ World Map) | Waypoint permanente en el conjunto actual; aparece también en el World Map | Automático si está instalado |
| Xaero's World Map solo | Muestra los waypoints del Minimap, pero sin Minimap no hay waypoints: se copian las coordenadas | — |
| JourneyMap | Waypoint en el mapa y el minimapa | Automático si está instalado |

Se puede desactivar en **Ajustes → Mapas → Waypoints**.

## `[item]` — lo que llevas en la mano

Escribe `[item]` (o pulsa **✦**) con un ítem en la mano y aparece en el mensaje:

> Luna: ¡mirad lo que me ha caído! **[Espada de diamante]**

Al **pasar el ratón** se ve el tooltip completo: nombre, encantamientos, durabilidad, atributos... exactamente como en el
inventario. Si compartes un stack, se indica la cantidad (`[Diamante x32]`).

## Reglas

- Las palabras clave valen en mayúsculas o minúsculas (`[COORDS]`, `[Item]`) e incluso con la errata `[cords]`.
- Solo se acepta **un `[coords]` y un `[item]` por mensaje**. Si repites la palabra clave, la segunda se muestra como texto
  (`(coords)`, `(item)`).
- Con la **mano vacía**, `[item]` no añade nada.
- El servidor puede **desactivar** esta función (módulo `sharing`); entonces las palabras clave se ven como texto normal.
- Compartir coordenadas es **decisión tuya** en cada mensaje: SocialMod nunca las publica por su cuenta. Tu estado y tu presencia
  **no incluyen coordenadas**; solo la dimensión, y únicamente si tú la activas.

## Ejemplos

| Escribes | Los demás ven |
|---|---|
| `¿Quedamos en [coords]?` | ¿Quedamos en **[120, 64, -450]**? (con distancia al pasar el ratón) |
| `Mira, [item]` | Mira, **[Pico de netherita]** (con tooltip) |
| `Estoy en [coords] con [item]` | Las dos cosas en el mismo mensaje |
