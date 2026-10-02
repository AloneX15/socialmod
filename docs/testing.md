# Pruebas y CI

```bash
./gradlew :26.3:test                 # unitarias (JUnit)
./gradlew :26.3:runGameTest          # gametests de servidor
./gradlew :26.3:runGameTest -PcompatPack   # con el modpack de compatibilidad
./gradlew :26.3:runClientGameTest    # cliente: capturas en versions/26.3/build/run/clientGameTest/screenshots/
```

## Unitarias (`src/test`)

- `SafeMarkdownTest`: negrita, cursiva, código, escapes, menciones, enlaces y que el JSON de componentes nunca se interpreta.
- `TextSanitizerTest`: controles, códigos `§`, caracteres de dirección e invisibles, espacios y textos de 1 MB.
- `ConversationIdTest`: claves simétricas, ida y vuelta, rechazo de basura y rutas, orden de roles.
- `SecurityTest`: rate limit por jugador, anti-spam (velocidad, repetición, silencio automático), filtro (palabras, regex inválidas, bloqueo).
- `StorageTest`: ida y vuelta comprimida, cuarentena de archivos corruptos, path traversal, retención y paginación.

## Gametests de servidor (`src/gametest`)

Mensajes guardados y no leídos; bloqueos; privacidad "solo amigos"; escribir en un privado ajeno o en un grupo
inexistente; textos enormes y basura; silencio automático por spam; filtro en modo bloqueo; flujo de amistad;
solicitudes de bloqueados descartadas; roles, permisos y canales por rol; herencia del liderazgo y disolución de grupos
vacíos; parties temporales; adjuntos `[item]`/`[coords]` generados por el servidor; borrado de datos.

## Cliente

Crea un mundo, comprueba el handshake real, crea un grupo con canales y mensajes por comandos, captura el panel, el
perfil, los ajustes del grupo y del jugador, los toasts y Quick-Reply; después reinicia el mundo y verifica que el grupo,
el mensaje fijado y el historial persistieron.

## CI

Ver la skill `skills/fabric-mod-github-ci`. Cada push compila y prueba las 3 versiones (con y sin compat pack) y ejecuta
el test de cliente con renderizado por software. Un push a `main` con todo en verde publica la pre-release `dev`; un tag
`vX.Y.Z` crea la release (y publica en Modrinth/CurseForge si están configurados `vars.MODRINTH_ID`/`vars.CURSEFORGE_ID`).
