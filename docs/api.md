# API para otros mods

Paquete `com.takumistudios.socialmod.api`. Usar desde el hilo principal (servidor) o el hilo del cliente.
Declara SocialMod como dependencia opcional (`suggests`) y comprueba `FabricLoader.isModLoaded("socialmod")`.

## Servidor

```java
SocialModServerAPI api = SocialModServerAPI.get();
if (api.isAvailable()) {
    Optional<GroupInfo> main = api.getMainGroup(player.getUUID());   // id, name, tag, color, party, members
    Collection<GroupInfo> groups = api.getGroups(player.getUUID());
    boolean friends = api.areFriends(a, b);
    boolean blocked = api.isBlocked(blocker, target);
    api.sendSystemNotification(player, Notification.of(Component.literal("Mazmorra"), Component.literal("¡Empieza!")));
}

// Actividad que se muestra como estado si el jugador no tiene uno personalizado (se consulta 1 vez/s)
api.registerStatusProvider(Identifier.fromNamespaceAndPath("mimod", "dungeon"),
        player -> inDungeon(player) ? Optional.of("En una mazmorra") : Optional.empty());

// Filtro externo: devuelve el texto (modificado o no) o null para rechazar
api.registerMessageFilter((sender, conversation, text) -> text.replace("lag", "l*g"));
```

Un mod de protección puede dar permisos por grupo con `getGroups(...)`: los roles vienen como `leader`, `officer`,
`member` y `recruit`.

## Eventos

```java
ChatEvents.ALLOW_MESSAGE.register((sender, conversation, text) -> !isJailed(sender));   // cancelable
ChatEvents.MESSAGE_SENT.register((sender, conversation, text) -> log(conversation, text));
GroupEvents.CREATED.register(group -> ...);
GroupEvents.DISBANDED.register(group -> ...);
GroupEvents.MEMBER_JOINED.register((group, player) -> ...);
GroupEvents.MEMBER_LEFT.register((group, player) -> ...);
PresenceEvents.STATUS_CHANGED.register((player, oldStatus, newStatus) -> ...);   // online, away, dnd, invisible, offline
```

`conversation` es `dm:<uuidA>:<uuidB>` (UUIDs ordenados) o `g:<grupo>:<canal>`.

## Cliente

```java
SocialModClientAPI client = SocialModClientAPI.get();   // com.takumistudios.socialmod.api.client
if (client.isConnected()) {
    client.openPanel();
    client.openPrivateChat(uuid);
    client.openGroupChat(groupId);
}
client.showToast(ToastData.system("Título", "Texto"));   // respeta la config de toasts del jugador
```

## Placeholders (Text Placeholder API)

`%socialmod:main_group%`, `%socialmod:tag%`, `%socialmod:unread%`, `%socialmod:status%`, `%socialmod:friends_online%`.

## Artefacto Maven (`socialmod-api`)

Desde la v0.2.0 la API se publica aparte, solo con los paquetes `api.*` (servidor y cliente) y sus fuentes:

```kotlin
repositories { maven("https://tu-maven.example/releases") }   // o mavenLocal()
dependencies {
    compileOnly("com.takumistudios.socialmod:socialmod-api:0.2.0+mc26.3")   // la versión de MC va en el sufijo
}
```

En ejecución la API la aporta el mod: declara `socialmod` en `suggests` (o `depends`) de tu `fabric.mod.json`.
`ToastData` usa el tipo `Payloads.NotifyKind` del mod; si lo necesitas en tu código compila también contra el jar
completo de SocialMod (`modCompileOnly`).

Publicación (mantenedores):

```bash
./gradlew :26.3:publishToMavenLocal                                   # ~/.m2
MAVEN_URL=... MAVEN_USERNAME=... MAVEN_PASSWORD=... ./gradlew :26.3:publish
```

En CI, la release lo publica si existe la variable `vars.MAVEN_URL` (con los secretos `MAVEN_USERNAME` y
`MAVEN_PASSWORD`).

## Versionado

La API sigue versionado semántico junto con el mod. La 0.2.0 no cambia ninguna firma pública de la 0.1.0.
El **protocolo de red actual es 5**, desde SocialMod 0.7.0. Cliente y servidor deben actualizarse juntos;
con versiones incompatibles el cliente pasa a modo «solo chat». Las firmas públicas de `GroupInfo` y
`SocialModClientAPI` se mantienen.

## Personalización y búsqueda (0.7.0)

Los estandartes de TEAM se sincronizan como color base y hasta seis capas de patrones de Minecraft.
No cambian el icono del tag. Su edición exige ser líder del TEAM o tener `admin.teams`.

La búsqueda del panel consulta identidades de jugadores conectados y desconectados conocidos por SocialMod,
con páginas de 20 y presencia filtrada para el solicitante. Abrir un resultado reutiliza `openPrivateChat(UUID)`;
no envía mensajes ni modifica las comprobaciones de permisos, privacidad o bloqueos.

El modo original global se guarda por separado en `config/socialmod/visual-mode.json`, se sincroniza en el estado
social y exige `admin.visuals`. La elección personal se guarda por servidor en `client.json`.
La base global prevalece sobre la elección personal. Ambos modos conservan los diseños guardados.

FancyMenu identifica los controles mediante `socialmod_button_<clave de traducción>` y las entradas mediante
identificadores explícitos. Por ejemplo: `socialmod_button_socialmod.panel.new_group`,
`socialmod_button_socialmod.search.button`, `socialmod_input_socialmod.panel.search`,
`socialmod_team_banner_<id>`, `socialmod_tag_banner_preview` y `socialmod_banner_preview`, `socialmod_banner_container`, `socialmod_tag_banner_container` y `socialmod_tag_banner_name`.
Los cambios de tamaño, posición, texto e imagen conservan las acciones del control.
