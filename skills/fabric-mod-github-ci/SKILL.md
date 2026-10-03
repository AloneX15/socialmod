---
name: fabric-mod-github-ci
description: CI y publicación en GitHub Actions para mods de Fabric multiversión (Stonecutter + Loom) de TakumiStudios. Úsala al montar o actualizar la CI de un mod de Minecraft Fabric, sus releases, su build "dev" descargable, la publicación en Modrinth/CurseForge o de su API en Maven, o al depurar fallos de la CI (tests de cliente colgados o que no cierran el mundo, xvfb/OpenGL/Vulkan, avisos de Node.js, mods de compatibilidad que no arrancan, tags que no coinciden con la versión).
---

# CI y builds en GitHub para mods de Fabric

Parte de CI del estándar TakumiStudios. Para crear un mod nuevo usa la skill `takumistudios-fabric-mod`: genera el
proyecto con estos workflows ya rellenados. Probada en SecureLock y SocialMod (MC 26.1.2 / 26.2 / 26.3, Loader 0.19,
Loom 1.18, Stonecutter 0.9, Java 25).

Resultado:
- **Cada push y PR:** normas TakumiStudios (job `standards`), build y tests unitarios, gametests de servidor con y sin
  compat pack y test de cliente con capturas, por cada versión de Minecraft. Un push nuevo cancela el run anterior.
- **Push a `main` en verde:** reemplaza la pre-release `dev` con los jars (descargable sin iniciar sesión).
- **Tag `vX.Y.Z`:** comprueba que coincide con `mod.version`, crea la release de GitHub con un jar por versión y la
  sección del `CHANGELOG.md`; Modrinth, CurseForge y Maven solo si están configurados. Tag con guion → pre-release/beta.

## Según la tarea

| Tarea | Qué hacer |
|---|---|
| La CI falla | Buscar el síntoma en `references/problemas.md` antes de tocar nada. Un job colgado no muestra su log hasta terminar: `gh run cancel <id>` y luego `gh run view <id> --log --job <jobId>`. |
| Montar la CI en un proyecto existente | Revisar `references/requisitos.md`, copiar `templates/` a `.github/workflows/` y sustituir los marcadores (abajo). |
| Actualizar una CI existente | Comparar los workflows del proyecto con `templates/` y aplicar solo las diferencias. |
| Publicar una versión | Ver "Publicar". |

Antes de escribir versiones de actions, compruébalas (`gh api repos/<owner>/<action>/releases/latest --jq .tag_name`):
las de las plantillas son de octubre de 2026. Dependabot (`.github/dependabot.yml`) las mantiene al día después.

## Workflows

### build.yml
- `concurrency` por rama con cancelación.
- **`standards`:** autoría TakumiStudios y metadatos de `fabric.mod.json`, README, LICENSE, CHANGELOG y SECURITY; sin
  `System.out`/`printStackTrace` en `src/main` y `src/client`; `MIXINS.md` si hay mixins; `en_us` y `es_es` con las
  mismas claves; validación del Gradle wrapper.
- **`test`:** matriz `minecraft × compat`. Sin compat: `:<v>:build` (incluye unitarios) y sube el jar. Siempre
  `:<v>:runGameTest` (con `-PcompatPack` en la variante compat). Si falla, sube reports y logs.
- **`client`:** `runClientGameTest` en `xvfb-run` con renderizado por software y `timeout-minutes: 15`. Sube capturas
  siempre y logs si falla o se cancela.
- **`dev-release`:** solo push a `main`, `needs: [standards, test, client]`, `concurrency` propia. Borra y recrea la
  pre-release `dev` con `gh`.

### release.yml (tags `v*`)
- **`prepare`:** tag = `v` + `mod.version` de `stonecutter.properties.toml`; extrae la sección `## X.Y.Z` del changelog.
- **`build`:** por versión, build + gametests, artifact y, si hay variables, Maven y `Kir-Antipov/mc-publish`.
- **`github-release`:** release con los jars y las notas (`--verify-tag`).

## Marcadores de las plantillas

`new-mod.sh` de `takumistudios-fabric-mod` los sustituye solo. A mano:

| Marcador | Dónde | Ejemplo |
|---|---|---|
| `__MOD_ID__` | ambas | `securelock` |
| `__MOD_NAME__` | `release.yml` | `SecureLock` |
| `__VERSIONS_JSON__` | `build.yml` | `["26.1.2", "26.2", "26.3"]` |
| `__VERSIONS_TEXT__` | ambas (notas) | `26.1.2, 26.2 y 26.3` |
| `__RELEASE_MATRIX__` | `release.yml` | bloque `include`, una entrada por versión con `game-versions` (sale de `mod.mc_releases`). El de la plantilla es un ejemplo para 26.1.2/26.2/26.3. |
| `__MC_PUBLISH_DEPENDENCIES__` | `release.yml` | `fabric-api(required)` y los opcionales, uno por línea |

Comprobar que no queda ninguno: `grep -rn "__[A-Z_]*__" .github/workflows/`.

Variantes:
- **Sin test de cliente:** borrar el job `client` y dejar `needs: [standards, test]` en `dev-release`.
- **Sin compat pack:** `compat: [false]`.
- **Sin API en Maven:** dejar el paso; sin `vars.MAVEN_URL` no se ejecuta.
- **Sin Stonecutter (una versión):** quitar `:${{ matrix.minecraft }}:` de las tareas y usar `build/` en lugar de
  `versions/<v>/build/`.

## Primer despliegue

1. En local, por versión: `./gradlew :<v>:build :<v>:runGameTest`, aparte `:<v>:runGameTest -PcompatPack` y
   `:<v>:runClientGameTest`.
2. Crear y subir el repo: `gh repo create <owner>/<repo> --public --source=. --remote=origin --push`.
3. Reporte privado de vulnerabilidades: `gh api -X PUT repos/<owner>/<repo>/private-vulnerability-reporting`.
4. Seguir el primer run (`gh run list`, `gh run watch <id>`): todo en verde, sin avisos de Node.js, y
   `gh release view dev` con un jar por versión, sin `-sources` ni `-api`.
5. Opcional:
   - Modrinth/CurseForge: `gh variable set MODRINTH_ID`, `gh secret set MODRINTH_TOKEN` (ídem `CURSEFORGE_*`);
     revisar `dependencies` de `mc-publish`.
   - Maven: `vars.MAVEN_URL`, `secrets.MAVEN_USERNAME`, `secrets.MAVEN_PASSWORD`.

## Publicar

1. Subir `mod.version` en `stonecutter.properties.toml` (semver).
2. Añadir la sección `## X.Y.Z` a `CHANGELOG.md`.
3. Commit, esperar a que `main` esté en verde, y `git tag vX.Y.Z && git push --tags`.
4. Comprobar con `gh release view vX.Y.Z` (y en Modrinth/CurseForge si aplica).
