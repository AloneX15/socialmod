plugins {
    id("net.fabricmc.fabric-loom")
    id("maven-publish")
}

val modId = sc.properties.get<String>("mod.id")
val modVersion = sc.properties.get<String>("mod.version")
val mcVersion = sc.current.version

// Nombre del jar: socialmod-<versión del mod>+mc<versión>.jar
version = "$modVersion+mc$mcVersion"
group = sc.properties.get<String>("mod.group")
base.archivesName = modId

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://maven.maxhenkel.de/repository/public", "henkelmax", "de.maxhenkel.voicechat")
}

// JourneyMap publica su API solo como SNAPSHOT; la estable va dentro del jar del mod (META-INF/jars). Se extrae para
// compilar el plugin (cliente). En ejecución la aporta JourneyMap, que es quien carga el plugin.
val journeyMapMod: Configuration by configurations.creating { isTransitive = false }
val extractJourneyMapApi by tasks.registering(Copy::class) {
    from({ zipTree(journeyMapMod.singleFile) }) {
        include("META-INF/jars/journeymap-api-*.jar")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("journeymap-api"))
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    runConfigs.all {
        runDirectory = rootProject.file("run")
        generateRunConfig = true
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "socialmod-test"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Integraciones opcionales: solo para compilar; en ejecución se usan si FabricLoader.isModLoaded(...)
    compileOnly("maven.modrinth:placeholder-api:${sc.properties.get<String>("deps.placeholder_api")}")
    "clientCompileOnly"("maven.modrinth:modmenu:${sc.properties.get<String>("deps.modmenu")}")
    // Simple Voice Chat: API oficial (la misma para 26.1.2–26.3); el plugin solo se carga si el mod está instalado
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.0")
    // Open Parties and Claims (servidor): sincronización opcional de grupos con parties de claims
    compileOnly("maven.modrinth:open-parties-and-claims:${sc.properties.get<String>("compat.opac")}")
    "gametestCompileOnly"("maven.modrinth:open-parties-and-claims:${sc.properties.get<String>("compat.opac")}")
    // JourneyMap (cliente): waypoints desde coordenadas compartidas
    journeyMapMod("maven.modrinth:journeymap:${sc.properties.get<String>("compat.journeymap")}")
    "clientCompileOnly"(fileTree(layout.buildDirectory.dir("journeymap-api")).builtBy(extractJourneyMapApi))

    "gametestCompileOnly"("net.luckperms:api:5.5")
    if (project.hasProperty("luckPerms")) {
        localRuntime("maven.modrinth:luckperms:DzQPkkXY")
    }

    // Modpack de pruebas de compatibilidad (PLAN 14): ./gradlew runGameTest -PcompatPack
    if (project.hasProperty("compatPack")) {
        localRuntime("maven.modrinth:placeholder-api:${sc.properties.get<String>("deps.placeholder_api")}")
        localRuntime("maven.modrinth:lithium:${sc.properties.get<String>("compat.lithium")}")
        localRuntime("maven.modrinth:ferrite-core:${sc.properties.get<String>("compat.ferritecore")}")
        localRuntime("maven.modrinth:open-parties-and-claims:${sc.properties.get<String>("compat.opac")}")
        localRuntime("maven.modrinth:simple-voice-chat:${sc.properties.get<String>("compat.voicechat")}")
        localRuntime("maven.modrinth:forge-config-api-port:${sc.properties.get<String>("compat.forgeconfigapiport")}")
    }
    // Xaero's Minimap + World Map, solo en el cliente (su librería no arranca en el servidor de gametests):
    //   ./gradlew :26.3:runClientGameTest -Pxaero    o    ./gradlew :26.3:runClient -Pxaero
    if (project.hasProperty("xaero")) {
        localRuntime("maven.modrinth:xaeros-minimap:${sc.properties.get<String>("compat.xaero_minimap")}")
        localRuntime("maven.modrinth:xaeros-world-map:${sc.properties.get<String>("compat.xaero_worldmap")}")
    }

    "gametestCompileOnly"("maven.modrinth:fancymenu:${sc.properties.get<String>("compat.fancymenu")}")
    "gametestCompileOnly"("maven.modrinth:spiffyhud:${sc.properties.get<String>("compat.spiffyhud")}")
    // Optional client editors; never nested in the published jar.
    "clientCompileOnly"("maven.modrinth:fancymenu:${sc.properties.get<String>("compat.fancymenu")}")
    "clientCompileOnly"("maven.modrinth:spiffyhud:${sc.properties.get<String>("compat.spiffyhud")}")
    if (project.hasProperty("fancyMenu")) {
        localRuntime("maven.modrinth:fancymenu:${sc.properties.get<String>("compat.fancymenu")}")
        localRuntime("maven.modrinth:konkrete:${sc.properties.get<String>("compat.konkrete")}")
        localRuntime("maven.modrinth:melody:${sc.properties.get<String>("compat.melody")}")
        if (!project.hasProperty("fancyOnly")) localRuntime("maven.modrinth:spiffyhud:${sc.properties.get<String>("compat.spiffyhud")}")
    }

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Backend h2 en las pruebas (en servidores el driver va en config/socialmod/drivers, no dentro del jar)
    testRuntimeOnly("com.h2database:h2:2.3.232")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

tasks {
    withType<JavaCompile>().configureEach { options.release = 25; options.encoding = "UTF-8" }

    test { useJUnitPlatform() }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to sc.properties.get<String>("mod.name"),
            "version" to version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to sc.properties.get<String>("deps.fabric_loader"),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
        manifest.attributes(
            "Implementation-Title" to sc.properties.get<String>("mod.name"),
            "Implementation-Version" to project.version.toString(),
            "Implementation-Vendor" to "TakumiStudios",
        )
    }
}

// API pública como artefacto Maven aparte (PLAN 16): solo los paquetes api.* (servidor y cliente), para que otros
// mods compilen contra ella con compileOnly sin depender de clases internas. En ejecución la aporta el mod.
//   ./gradlew :26.3:publishToMavenLocal                         -> ~/.m2
//   MAVEN_URL=... MAVEN_USERNAME=... MAVEN_PASSWORD=... ./gradlew :26.3:publish
val apiJar by tasks.registering(Jar::class) {
    archiveClassifier = "api"
    from(sourceSets.main.get().output) { include("com/takumistudios/socialmod/api/**") }
    from(sourceSets.getByName("client").output) { include("com/takumistudios/socialmod/api/**") }
    from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
}
val apiSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier = "api-sources"
    from(sourceSets.main.get().allJava) { include("com/takumistudios/socialmod/api/**") }
    from(sourceSets.getByName("client").allJava) { include("com/takumistudios/socialmod/api/**") }
}

publishing {
    publications {
        register<MavenPublication>("api") {
            groupId = sc.properties.get<String>("mod.group")
            artifactId = "$modId-api"
            version = project.version.toString()
            artifact(apiJar) { classifier = null }
            artifact(apiSourcesJar) { classifier = "sources" }
            pom {
                name = "SocialMod API"
                description = "API pública de SocialMod (eventos, filtros, estado y notificaciones) para Minecraft $mcVersion"
            }
        }
    }
    repositories {
        val remote = providers.environmentVariable("MAVEN_URL")
        if (remote.isPresent) {
            maven {
                name = "remote"
                url = uri(remote.get())
                credentials {
                    username = providers.environmentVariable("MAVEN_USERNAME").orNull
                    password = providers.environmentVariable("MAVEN_PASSWORD").orNull
                }
            }
        }
    }
}

// Boots a clean client with the documented exported modpack already installed.
if (project.hasProperty("seriesPack")) {
    tasks.named<JavaExec>("runClientGameTest") {
        systemProperty("socialmod.test.seriesPack", "true")
        doFirst {
            project.copy {
                val style = "clean"
                from(zipTree(rootProject.file("docs/examples/series/$style-$mcVersion.zip")))
                into(project.layout.buildDirectory.dir("run/clientGameTest"))
            }
        }
    }
}

// Dedicated profile: normal mock players bypass login preloading and LuckPerms rejects them.
if (project.hasProperty("luckPerms")) {
    tasks.named<JavaExec>("runGameTest") {
        systemProperty("fabric-api.gametest.filter", "socialmod-test:social_mod_game_tests_optional_luck_perms_checks_real_permissions_and_integer_metadata")
    }
}
