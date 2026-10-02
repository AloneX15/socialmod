---
name: fabric-mod-github-ci
description: Configura la compilación, las pruebas y la publicación automática en GitHub Actions para un mod de Fabric multiversión (Stonecutter + Loom). Úsala al crear o subir a GitHub un mod de Minecraft Fabric, al montar su CI, sus releases o su build "dev" descargable, o al depurar fallos de la CI de un mod (tests de cliente colgados, avisos de Node.js, publicación en Modrinth/CurseForge).
---

# CI y builds en GitHub para mods de Fabric

Receta probada en SecureLock (MC 26.1.2 / 26.2 / 26.3, Fabric Loader 0.19, Loom 1.18, Stonecutter 0.9, Java 25).
El resultado:

- **Cada push y PR:** compila y prueba cada versión de Minecraft (tests unitarios, gametests en servidor, gametests con un "modpack de compatibilidad" y un test de cliente con capturas).
- **Cada push a `main` con todo en verde:** se reemplaza la pre-release `dev` con los jars, descargable por cualquiera sin iniciar sesión.
- **Tag `vX.Y.Z`:** release de GitHub con un jar por versión. Modrinth y CurseForge se publican solo si están configurados.

Las plantillas están en `templates/`. Sustituye los marcadores `__MOD_ID__`, `__VERSIONS__`, etc. (ver "Marcadores").

## 1. Requisitos del proyecto (antes de la CI)

1. **Multiversión con Stonecutter.** `settings.gradle.kts` declara las versiones (`versions("26.1.2", "26.2", "26.3")`) y cada una es un subproyecto `:<versión>`. Las tareas se llaman `:26.3:build`, `:26.3:runGameTest`… Las propiedades por versión van en `stonecutter.properties.toml` (`deps.fabric_api`, `mod.mc_compat`…).
2. **Nombre del jar:** `version = "$modVersion+mc$mcVersion"`, de modo que sale `<modid>-1.0.0+mc26.3.jar` en `versions/<v>/build/libs/`.
3. **Gametests de Loom.** En `build.gradle.kts`:
   ```kotlin
   fabricApi {
       configureTests {
           createSourceSet = true          // src/gametest/java + src/gametest/resources
           modId = "__MOD_ID__-test"
           enableGameTests = true          // tarea runGameTest (servidor dedicado)
           enableClientGameTests = true    // tarea runClientGameTest
           eula = true                     // imprescindible: si no, el servidor de la CI no arranca
       }
   }
   ```
   `src/gametest/resources/fabric.mod.json` declara los entrypoints `fabric-gametest` y `fabric-client-gametest`.
4. **Perfil de compatibilidad opcional** (`-PcompatPack`): mods reales cargados solo en desarrollo, con `localRuntime(...)` dentro de `if (project.hasProperty("compatPack"))`. Usa el repo Maven de Modrinth (`maven("https://api.modrinth.com/maven")`, grupo `maven.modrinth`).
5. **Repositorio Git:**
   - `.gitattributes` con `* text=auto eol=lf`, `*.bat text eol=crlf` y `*.jar binary`. Sin él, `gradlew` acaba con CRLF y falla en Linux.
   - `git update-index --chmod=+x gradlew`.
   - `.gitignore`: `build/`, `.gradle/`, `run/`, `versions/*/build/`, `.idea/runConfigurations/`, `.idea/workspace.xml`, `logs/`. Las run configs que genera Loom tienen rutas de cada máquina.

## 2. Workflows

Copia `templates/build.yml` y `templates/release.yml` a `.github/workflows/`.

### build.yml (push, PR y manual)
- **Job `test`:** matriz `minecraft × compat`. Sin compat ejecuta `:<v>:build` (incluye los tests unitarios) y sube el jar como artifact. En ambos casos ejecuta `:<v>:runGameTest` (con `-PcompatPack` en la variante compat). Si falla, sube los reports y los logs del gametest.
- **Job `client`:** `runClientGameTest` dentro de `xvfb-run` con renderizado por software (ver "Problemas conocidos"). Sube las capturas siempre.
- **Job `dev-release`:** solo en push a `main` y `needs: [test, client]`. Descarga los artifacts y, con el `gh` CLI, **borra y recrea** la pre-release `dev` apuntando al commit actual. Necesita `permissions: contents: write` en ese job.

### release.yml (tags `v*`)
- Matriz por versión: build + gametests, artifact del jar y, **solo si existen** `vars.MODRINTH_ID` o `vars.CURSEFORGE_ID`, publicación con `Kir-Antipov/mc-publish`.
- Job `github-release`: crea la release del tag con los 3 jars (`gh release create "$GITHUB_REF_NAME" jars/*.jar --verify-tag`).
- Para publicar: `git tag v1.0.0 && git push --tags`.

## 3. Problemas conocidos y cómo se resolvieron

| Síntoma | Causa | Solución |
|---|---|---|
| El test de cliente de **26.3** se queda colgado hasta que se cancela ("The operation was canceled"). | 26.3 usa un renderizador nuevo (SDL + OpenGL/Vulkan). En xvfb, OpenGL falla con *"Couldn't find matching GLX visual"*, Vulkan no tiene `VK_KHR_surface` y el juego no se cierra solo. | Instalar `xvfb libgl1-mesa-dri libglx-mesa0 mesa-utils mesa-vulkan-drivers vulkan-tools`, usar `xvfb-run -a -s "-screen 0 1920x1080x24 +extension GLX +render"` y `LIBGL_ALWAYS_SOFTWARE=1`. El juego cae a **Vulkan con lavapipe** y funciona. Añadir siempre `timeout-minutes: 15` al job de cliente. |
| Aviso *"Node.js 20 is deprecated…"*. | Actions `@v4`. | Usar versiones actuales (octubre de 2026): `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`, `actions/download-artifact@v8`. Comprobarlas con `gh api repos/<owner>/<action>/releases/latest --jq .tag_name`. |
| Un job colgado no deja ver su log. | GitHub solo publica el log cuando el job termina. | `gh run cancel <id>` y luego `gh run view <id> --log --job <jobId>`. |
| Los artifacts y la release incluyen `-sources.jar`. | Patrón `*.jar`. | En el `path` del artifact, añadir una exclusión: `!versions/<v>/build/libs/*-sources.jar`. |
| `mc-publish` falla en un repo sin proyecto en Modrinth/CurseForge. | Faltan ids y tokens. | Poner el paso tras `if: ${{ vars.MODRINTH_ID != '' \|\| vars.CURSEFORGE_ID != '' }}` y crear la release de GitHub en un job aparte. |
| Una dependencia de Modrinth Maven resuelve el jar de otro loader. | En proyectos multiloader el `version_number` se repite (Forge/NeoForge/Fabric). | Usar el **id de versión** (p. ej. `JpKvrr9J`) en lugar del número. Se obtiene con `https://api.modrinth.com/v2/project/<slug>/version?loaders=["fabric"]&game_versions=["26.3"]`. |
| Un mod del compat pack no arranca: *HARD_DEP_NO_CANDIDATE*. | Faltan sus dependencias (p. ej. OPAC necesita `forgeconfigapiport`). | Añadirlas también con `localRuntime`. |
| Jade se cae con `StackOverflowError` en `runClientGameTest`. | Fallo de Jade en ese runner, incluso sin tu mod. | No incluir Jade en el test de cliente. Basta con verificar en el log que carga tu plugin. |
| La CI de un repo privado consume minutos rápido. | La matriz lanza unos 9 jobs por push. | En repos públicos es gratis. Si es privado, reducir la matriz o lanzar el compat pack solo con `workflow_dispatch`. |

## 4. Pasos para un mod nuevo

1. Comprobar versiones actuales:
   - Minecraft estables: `https://meta.fabricmc.net/v2/versions/game`
   - Loader: `https://meta.fabricmc.net/v2/versions/loader`
   - Fabric API por versión: `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`
   - Loom y Stonecutter: sus `maven-metadata.xml`.
2. Aplicar la sección 1 (Stonecutter, nombre del jar, `configureTests`, `.gitattributes`, `.gitignore`, `gradlew` ejecutable).
3. Copiar las plantillas y sustituir los marcadores.
4. En local, para cada versión: `./gradlew :<v>:build :<v>:runGameTest` (y `:<v>:runClientGameTest` si hay test de cliente).
5. Crear y subir el repo: `gh repo create <owner>/<repo> --public --source=. --remote=origin --push` (renombrar antes la rama a `main` si hace falta).
6. Activar el reporte privado de vulnerabilidades: `gh api -X PUT repos/<owner>/<repo>/private-vulnerability-reporting`.
7. Seguir el primer run: `gh run list`, `gh run watch <id>`. Revisar que no haya avisos de Node.js y que `dev-release` haya publicado: `gh release view dev`.
8. Opcional: crear los proyectos en Modrinth y CurseForge y añadir `vars.MODRINTH_ID`, `vars.CURSEFORGE_ID`, `secrets.MODRINTH_TOKEN` y `secrets.CURSEFORGE_TOKEN`. Revisar también la lista `dependencies` de `mc-publish`.

## 5. Marcadores de las plantillas

| Marcador | Ejemplo |
|---|---|
| `__MOD_ID__` | `securelock` |
| `__MOD_NAME__` | `SecureLock` |
| `__VERSIONS_JSON__` | `["26.1.2", "26.2", "26.3"]` |
| Bloque `include` de `release.yml` | una entrada por versión con su lista `game-versions` (p. ej. 26.1.2 → `26.1`, `26.1.1`, `26.1.2`) |
| `__MC_PUBLISH_DEPENDENCIES__` | `fabric-api(required)` y los opcionales |

Si el mod no tiene test de cliente, borra el job `client` y quítalo de `needs` en `dev-release`. Si no tiene compat pack, deja `compat: [false]`.
