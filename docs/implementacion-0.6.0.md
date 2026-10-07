# Implementación del plan aprobado — 0.6.0

## Resultado

Se sustituyen las guías y descargas navideñas por [dos diseños editables](series-es.md): Navidad gráfica y otro inspirado en Dedsafío 4. Ambos mantienen visible el mundo, con paneles semitransparentes, FancyMenu y HUD opcional de SpiffyHUD. Se incluyen guías en español e inglés, doce capturas reales y seis ZIP para Minecraft 26.1.2, 26.2 y 26.3. Navidad reutiliza los seis botones y ocho iconos PNG originales adjuntos, sin modificar sus imágenes. Los botones conservan sus extremos decorados, texto traducido, navegación por teclado y estados de interacción. FancyMenu permite editar sus texturas locales y SpiffyHUD sus marcos e iconos. El alto contraste usa controles sencillos. Los perfiles antiguos conservan compatibilidad y sus recursos locales.

El editor conserva borradores al cambiar de selección o tamaño, impide salir de campos inválidos y limita deshacer a 40 pasos. Las activaciones crean respaldo, comprueban versiones y desactivan el otro ejemplo incluido conservando sus archivos. El cierre termina las operaciones de perfiles ya admitidas, incluidas sus continuaciones.

Las lecturas fallidas de historial no crean conversaciones vacías guardables; el cliente conserva mensajes y permite reintentar al reabrir. Los índices históricos se preparan en lotes de 16 registros. La sincronización usa secciones modificadas y fragmentos acotados con aplicación atómica, presupuesto de 8 MiB y recuperación de la base.

**Cambio incompatible:** protocolo 4; actualizar cliente y servidor juntos a 0.6.0.

## Validación local — 7 de octubre de 2026

| Comprobación | Resultado por versión |
| --- | --- |
| Build, JAR y pruebas unitarias | Correctos; 83 registradas, 82 pasan y una omitida por permisos de enlaces simbólicos en Windows. |
| Gametests de servidor | 33 pasan en base y 33 con compat pack. |
| Cliente nativo | Correcto. |
| FancyMenu sin SpiffyHUD | Correcto. |
| FancyMenu + SpiffyHUD | Correcto; última ejecución incluye guardado y activación pendientes al cerrar. |
| Arranque con ZIP Navidad gráfica | Correcto. |
| Arranque con ZIP Dedsafío | Correcto; comprueba transición entre los ejemplos y recuperación del HUD nativo. |

Los resultados abarcan las tres versiones. Los JAR tienen CRC válido, autoría TakumiStudios y no incluyen clases de gametest. Los seis ZIP, las traducciones y los enlaces de ambas guías están verificados. `git diff --check` pasa.

Evidencia local en `build/christmas-build-final.log`, `build/christmas-compat-final.log`, `build/christmas-clients-native.log`, `build/christmas-clients-fancy-only.log`, `build/christmas-clients-pack.log` y `build/christmas-clients-dedsafio.log`. La última comprobación con ambos mods completó 26.2 y 26.3 en `build/christmas-clients-both-final.log`; 26.1.2 se bloqueó en la coordinación de hilos de Fabric al iniciar y pasó al repetirse por separado en `build/christmas-client-26.1.2-final.log`. Se guardó el volcado de hilos en `build/christmas-client-shutdown-threads.txt`. Los logs no forman parte del JAR.

Estas pruebas usan clientes y servidores locales; no equivalen a mediciones con 200 conexiones reales ni verifican la ejecución remota de GitHub Actions. La matriz de CI incluye los cinco perfiles de cliente.

Creado por **TakumiStudios**.
