# SocialMod — Errores Encontrados

**Fecha:** 2026-10-03  
**Mod:** SocialMod (TakumiStudios)  
**Versión:** En desarrollo

---

## 1. Rotación de avatar en cambio de estado

**Problema:** Al seleccionar un estado (En línea, AFK, No molestar, Invisible) o activar opciones como "Mostrar mi dimensión", no rota al pulsar el estado.

**Afecta a:**
- Cambio de estado
- Mostrar/ocultar dimensión
- Otras opciones de perfil

---

## 2. Formateo inconsistente de leyendas

**Problema:** Las leyendas, etiquetas y textos de la interfaz no siguen un formato consistente.

**Inconsistencias detectadas:**
- Tamaño de fuente variable entre secciones
- Alineación desigual de textos
- Espaciado inconsistente entre elementos
- Contraste de colores con el fondo
- Mezcla de mayúsculas/minúsculas (CONVERSACIONES, Chat Grupal, en linea, etc.)

**Esperado:** Todos los textos deben seguir una guía de estilo consistente en todo el panel.

---

## 3. Posición y personalización del tag del grupo

**Problema:** El tag del grupo (nombre del grupo y rol del jugador) necesita mejoras en posición y opciones de personalización.

**Deficiencias:**
- El tag debe estar **debajo del nombre del jugador**, no encima
- No hay forma de agregar iconos o emojis al tag
- El selector de color no es amigable (solo hexadecimal en archivos de config)
- Falta visual para diferenciar grupos rápidamente

**Esperado:**
- Tag posicionado debajo del nombre
- Soporte para iconos/emojis personalizables
- **Selector visual de colores (color picker hexadecimal)** integrado en la interfaz
- Preview en tiempo real del tag

---

## 4. Placeholders en notificaciones no se renderizan

**Problema:** En las notificaciones (toasts), cuando se envían coordenadas u otros elementos especiales, aparecen como texto sin procesar en lugar de sus valores reales.

**Ejemplos:**
- Coordenadas: Muestra `[CORDS]` en lugar de `x: 120, z: -450`
- Ítems: Muestra `[ITEM]` en lugar de la información del ítem
- Otros placeholders: No se renderizan correctamente

**Esperado:**
- Las coordenadas deben mostrar valores reales (`x: 120, z: -450`)
- Los ítems deben mostrar su nombre e información
- Todos los placeholders deben procesarse antes de mostrar la notificación

---

## Resumen

| # | Error | Severidad | Estado |
|---|---|---|---|
| 1 | Rota al cambiar estado | 🔴 Alto | ✅ Resuelto (0.2.0) |
| 2 | Formateo inconsistente de leyendas | 🟡 Medio | ✅ Resuelto (0.2.0) |
| 3 | Tag del grupo: posición y personalización | 🟡 Medio | ✅ Resuelto (0.2.0) |
| 4 | Placeholders no se renderizan en notificaciones | 🔴 Alto | ✅ Resuelto (0.2.0) |


Detalle de cada solución: [docs/implementation-status.md](docs/implementation-status.md) y
[docs/registro-de-errores.md](docs/registro-de-errores.md).
