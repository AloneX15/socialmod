# Requisitos del proyecto para la CI

La plantilla de `takumistudios-fabric-mod` ya los cumple. Esta lista sirve para adaptar un proyecto existente.

1. **Multiversión con Stonecutter.** `settings.gradle.kts` declara las versiones (`versions("26.1.2", "26.2", "26.3")`)
   y cada una es un subproyecto `:<versión>`. Las tareas se llaman `:26.3:build`, `:26.3:runGameTest`… Las propiedades
   van en `stonecutter.properties.toml`: comunes arriba (`mod.id`, `mod.version`…) y por versión en `["26.3"]`
   (`deps.fabric_api`, `mod.mc_compat`…). `release.yml` lee `mod.version = "X.Y.Z"` de ese archivo.
2. **Nombre del jar:** `version = "$modVersion+mc$mcVersion"` y `base.archivesName = modId`, de modo que sale
   `<modid>-1.0.0+mc26.3.jar` en `versions/<v>/build/libs/`.
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
4. **Repositorios Maven acotados.** Usa `exclusiveContent` para que cada grupo solo se busque en su repo (más rápido y
   evita resolver artefactos equivocados):
   ```kotlin
   repositories {
       fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
           forRepository { maven(url) { name = alias } }
           filter { groups.forEach(::includeGroup) }
       }
       strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
   }
   ```
5. **Perfiles de compatibilidad opcionales**, solo en desarrollo, con `localRuntime(...)` dentro de
   `if (project.hasProperty("..."))`:
   - `-PcompatPack`: mods que funcionan en el **servidor de gametests** (Lithium, FerriteCore, Placeholder API…). Va en la CI.
   - Un perfil aparte por mod que **solo** funcione en cliente o que dé problemas en el arnés (p. ej. `-Pxaero`). No va
     en la CI; se usa como diagnóstico manual. Ver `problemas.md`.
6. **API en Maven (opcional).** Si el mod expone una API, publícala como artefacto aparte (`<modid>-api`) con un `Jar`
   de clasificador `api` y un repositorio que solo exista si hay `MAVEN_URL`:
   ```kotlin
   publishing {
       repositories {
           val remote = providers.environmentVariable("MAVEN_URL")
           if (remote.isPresent) maven {
               name = "remote"; url = uri(remote.get())
               credentials {
                   username = providers.environmentVariable("MAVEN_USERNAME").orNull
                   password = providers.environmentVariable("MAVEN_PASSWORD").orNull
               }
           }
       }
   }
   ```
   En local: `./gradlew :26.3:publishToMavenLocal`. Las plantillas excluyen `*-api.jar` de la release.
7. **Repositorio Git:**
   - `.gitattributes` con `* text=auto eol=lf`, `*.bat text eol=crlf`, `*.jar binary` y `*.png binary`. Sin él,
     `gradlew` acaba con CRLF en Windows y falla en Linux (`/usr/bin/env: 'sh\r'`).
   - `git update-index --chmod=+x gradlew` (si no: `Permission denied` en la CI).
   - `.gitignore`: `build/`, `.gradle/`, `run/`, `versions/*/build/`, `.idea/runConfigurations/`, `.idea/workspace.xml`,
     `logs/`. Las run configs que genera Loom tienen rutas de cada máquina.
   - `CHANGELOG.md` con una sección `## X.Y.Z …` por versión: `release.yml` la usa como notas.
