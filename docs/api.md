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

## Versionado

La API sigue versionado semántico junto con el mod. La publicación como artefacto Maven separado (`socialmod-api`)
está prevista en la fase 5 del plan.
