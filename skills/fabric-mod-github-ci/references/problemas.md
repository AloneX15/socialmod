# Problemas conocidos de la CI y los tests, y cómo se resolvieron

Buscar aquí el síntoma antes de tocar nada. Añadir cada problema nuevo con su causa y solución.

| Síntoma | Causa | Solución |
|---|---|---|
| El test de cliente de **26.3** se queda colgado hasta que se cancela ("The operation was canceled"). | 26.3 usa un renderizador nuevo (SDL + OpenGL/Vulkan). En xvfb, OpenGL falla con *"Couldn't find matching GLX visual"*, Vulkan no tiene `VK_KHR_surface` y el juego no se cierra solo. | Instalar `xvfb libgl1-mesa-dri libglx-mesa0 mesa-utils mesa-vulkan-drivers vulkan-tools`, usar `xvfb-run -a -s "-screen 0 1920x1080x24 +extension GLX +render"` y `LIBGL_ALWAYS_SOFTWARE=1`. El juego cae a **Vulkan con lavapipe** y funciona. Siempre `timeout-minutes` en el job de cliente. |
| El test de cliente se cuelga **al salir del mundo** solo con cierto mod instalado (visto con Xaero's Minimap). En Windows la ventana queda "No responde" y el proceso sale con `0xCFFFFFFF`. | Interbloqueo del arnés `fabric-client-gametest`: Render thread en `IntegratedServer.halt()`, Server y Test thread en `ThreadingImpl.enterPhase`. No es un fallo del mod ni del juego real. | Antes de buscar el fallo en tu código, repetirlo **sin** ese mod y en el juego real (`runClient -P<perfil>`). Poner en el test un vigilante que vuelque las pilas si salir tarda más de 15 s (ver abajo). Dejar ese perfil fuera de la CI, como diagnóstico manual. |
| El servidor de gametests no arranca con un mod de cliente: `IllegalStateException: Registry is not frozen yet!` (visto con `xaerolib`). | Ese mod no está pensado para el servidor de gametests. | Sacarlo del compat pack y probarlo solo en cliente con un perfil propio. |
| Aviso *"Node.js 20 is deprecated…"*. | Actions antiguas (`@v4`). | Versiones de octubre de 2026: `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`, `actions/download-artifact@v8`. Comprobarlas con `gh api repos/<owner>/<action>/releases/latest --jq .tag_name`. |
| Un job colgado no deja ver su log. | GitHub solo publica el log cuando el job termina. | `gh run cancel <id>` y luego `gh run view <id> --log --job <jobId>`. Los logs del cliente se suben también al cancelar. |
| `/usr/bin/env: 'sh\r'` o `./gradlew: Permission denied`. | `gradlew` con CRLF o sin bit de ejecución. | `.gitattributes` y `git update-index --chmod=+x gradlew` (`requisitos.md`). |
| Los artifacts y la release incluyen `-sources.jar` o el jar de la API. | Patrón `*.jar`. | Exclusiones en el `path`: `!…/*-sources.jar` y `!…/*-api.jar`. |
| `mc-publish` falla en un repo sin proyecto en Modrinth/CurseForge. | Faltan ids y tokens. | Paso con `if: ${{ vars.MODRINTH_ID != '' \|\| vars.CURSEFORGE_ID != '' }}` y release de GitHub en un job aparte. |
| La release falla en `prepare`: *El tag … no coincide con mod.version*. | Se etiquetó sin subir la versión (o al revés). | Borrar el tag (`git push --delete origin vX.Y.Z && git tag -d vX.Y.Z`), corregir `mod.version` y volver a etiquetar. |
| Una dependencia de Modrinth Maven resuelve el jar de otro loader. | En proyectos multiloader el `version_number` se repite (Forge/NeoForge/Fabric), p. ej. Forge Config API Port. | Usar el **id de versión** (p. ej. `JpKvrr9J`) en lugar del número. Se obtiene con `https://api.modrinth.com/v2/project/<slug>/version?loaders=["fabric"]&game_versions=["26.3"]`. |
| Un mod del compat pack no arranca: *HARD_DEP_NO_CANDIDATE* o *requires … which is missing*. | Faltan sus dependencias (p. ej. OPAC necesita `forgeconfigapiport`) o se resolvió la versión de otro loader. | Añadirlas también con `localRuntime`, por id de versión si son multiloader. |
| Jade se cae con `StackOverflowError` en `runClientGameTest`. | Fallo de Jade en ese runner, incluso sin tu mod. | No incluir Jade en el test de cliente. Basta con verificar en el log que carga tu plugin. |
| La CI de un repo privado consume minutos rápido. | La matriz lanza unos 9 jobs por push. | En repos públicos es gratis. Si es privado, reducir la matriz o lanzar el compat pack solo con `workflow_dispatch`. |

**Vigilante de cuelgues para el test de cliente** (activarlo justo antes de cerrar el mundo y desactivarlo después):

```java
private static final AtomicBoolean HANG_WATCH = new AtomicBoolean();

private static void startHangWatchdog() {
    HANG_WATCH.set(true);
    Thread watchdog = new Thread(() -> {
        for (int i = 0; i < 3; i++) {
            try { Thread.sleep(15_000); } catch (InterruptedException e) { return; }
            if (!HANG_WATCH.get()) return;
            StringBuilder dump = new StringBuilder("HANGDUMP salir del mundo tarda más de ").append(15 * (i + 1)).append(" s\n");
            Thread.getAllStackTraces().forEach((thread, stack) -> {
                dump.append("HANGDUMP \"").append(thread.getName()).append("\" ").append(thread.getState()).append('\n');
                for (StackTraceElement e : stack) dump.append("HANGDUMP     at ").append(e).append('\n');
            });
            LOGGER.warn(dump.toString());
        }
    }, "hang watchdog");
    watchdog.setDaemon(true);
    watchdog.start();
}
```

Luego: `grep HANGDUMP versions/<v>/build/run/clientGameTest/logs/latest.log`. Si los hilos de tu mod están libres y el
bloqueo está en `ThreadingImpl` / `IntegratedServer.halt()`, es el arnés.
