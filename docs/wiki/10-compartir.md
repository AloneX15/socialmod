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
- **Pulsar el mensaje** (en el panel, dos veces) o hacer clic en el chat normal: **copia** las coordenadas al portapapeles
  (`120 64 -450`) para pegarlas donde quieras.

> **Waypoints automáticos** (Xaero's Minimap y JourneyMap) están previstos para una versión futura. De momento se copian las
> coordenadas.

## `[item]` — lo que llevas en la mano

Escribe `[item]` (o pulsa **✦**) con un ítem en la mano y aparece en el mensaje:

> Luna: ¡mirad lo que me ha caído! **[Espada de diamante]**

Al **pasar el ratón** se ve el tooltip completo: nombre, encantamientos, durabilidad, atributos... exactamente como en el
inventario. Si compartes un stack, se indica la cantidad (`[Diamante x32]`).

## Reglas

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
