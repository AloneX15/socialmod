# 18. Integraciones con otros mods

Todas son **opcionales**: SocialMod funciona igual sin ellas y cada una se activa sola si el mod está instalado. Ninguna
usa mixins; las que no tienen API pública estable se usan por reflexión con protección ante errores (si una versión
nueva del otro mod cambia algo, la integración se desactiva con un aviso en el log y SocialMod sigue funcionando).

Comprobado en octubre de 2026 contra las versiones publicadas en Modrinth para **26.1.2, 26.2 y 26.3**.

## Disponibles

| Mod | Versión probada | Lado | Qué hace SocialMod | Cómo |
|---|---|---|---|---|
| **Xaero's Minimap** | 26.5.0 / 26.5.1 / 26.5.3 | Cliente | Waypoint desde coordenadas compartidas; aparta toasts y HUD del minimapa la primera vez | Reflexión (`MapCompat`) |
| **Xaero's World Map** | 1.46.0 / 1.46.1 / 1.46.4 | Cliente | Muestra los waypoints del Minimap; sin Minimap se copian las coordenadas | — |
| **JourneyMap** | 6.0.9 | Cliente | Waypoint desde coordenadas compartidas | Plugin de su API v2 (entrypoint `journeymap`) |
| **Simple Voice Chat** | 2.6.24 | Servidor + cliente | Chat de voz por grupo y party (`☏`, `/g voice`, `/party voice`) | Plugin de su API (entrypoint `voicechat`) |
| **Open Parties and Claims** | 0.31.6 (necesita Forge Config API Port) | Servidor | Sincroniza un grupo con la party de claims del líder | API pública; `integrations.claimsSync` + `/g claims link` |
| **LuckPerms** | — | Servidor | Permisos y límites por rango | Fabric Permission API |
| **Text Placeholder API** | 3.x | Servidor | 5 placeholders | API pública |
| **ModMenu** | — | Cliente | Botón de configuración | API pública |
| **Geyser** (Bedrock) | — | Servidor | Comandos y el menú `/social` funcionan en Bedrock | Sin código específico |

### Xaero: detalles

- El waypoint se crea en el **conjunto actual** del minimapa y en la **dimensión actual** (el "mundo" de Xaero es por
  dimensión): si las coordenadas son de otra dimensión, se copian.
- Xaero usa por defecto la tecla **Y** para sus ajustes, la misma que Respuesta rápida: SocialMod mueve la suya a una libre
  la primera vez (te lo dice en el chat). **M** (mapa), **B** (nuevo waypoint), **U** (waypoints) y **Z** no chocan con
  SocialMod.
- **Solo en pruebas automáticas:** con Xaero instalado, el arnés de pruebas de cliente de Fabric a veces se interbloquea
  al salir del mundo (la ventana queda en "No responde"), con o sin waypoints. En el juego real no pasa: entrar, crear un
  waypoint, salir y cerrar funcionó 5 de 5 veces. Detalle en el [registro de errores](../registro-de-errores.md).

## Pendientes (y por qué)

| Mod / función | Estado en 26.1.2–26.3 | Qué falta para implementarlo |
|---|---|---|
| **FTB Chunks** | No existe para estas versiones | Que se publique para 26.x; entonces, el mismo esquema que OPAC. |
| **Cadmus** | No existe para estas versiones | Igual que FTB Chunks. |
| **Plasmo Voice** | Solo **beta** (2.2.0-beta.1); los grupos están en el addon `pv-addon-groups` (1.1.1), **sin API pública** | Una versión estable de Plasmo y una API de grupos; usar sus clases internas rompería con cada actualización. Pendiente de probar cuando exista. |
| **Polymer** | Existe (0.16–0.18) | No hace falta para el menú de cofre `/social`, que ya funciona en vanilla y Bedrock. Solo tendría sentido para ítems o bloques propios. |
| **Velocity (redes de servidores)** | No es un mod de Minecraft | Plugin de proxy y un puente (Redis) para mensajes y presencia entre servidores: proyecto aparte (v2.0). |
| **Discord Rich Presence** | Addon aparte por diseño | Jar separado, desactivado por defecto, que nunca muestre coordenadas (PLAN 12): proyecto aparte. |

## Para probarlas

```bash
./gradlew :26.3:runGameTest -PcompatPack        # servidor: Lithium, FerriteCore, Placeholder API, OPAC, Simple Voice Chat
./gradlew :26.3:runClientGameTest -Pxaero       # cliente con Xaero's Minimap y World Map
./gradlew :26.3:runClient -Pxaero               # jugar con Xaero en el entorno de desarrollo
```
