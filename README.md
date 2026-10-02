# SocialMod

**Capa social completa para servidores Fabric** · Minecraft 26.1.x, 26.2.x y 26.3.x · Java 25 · por TakumiStudios

> *English summary:* private messages with an offline mailbox, friends and blocks, groups (clans) with roles and channels,
> temporary parties, presence (AFK/DND/invisible), toasts, Quick-Reply, coordinate and item sharing, group tags on
> nametags and moderation tools. **Server-side required, client optional**: vanilla and Bedrock (Geyser) players use
> commands; with the mod on the client you get the full UI. Only dependency: Fabric API. No mixins.

## Qué incluye

| | |
|---|---|
| **Mensajes privados** | Historial en el servidor, **buzón** para jugadores desconectados ("Tienes 3 mensajes de Alex"), editar y borrar durante 2 min, "escribiendo..." y "leído" (desactivables), markdown seguro (`**negrita**`, `*cursiva*`, `` `código` ``), enlaces con confirmación, `@menciones`. |
| **Amigos y bloqueos** | Solicitudes, favoritos, notas privadas. El bloqueo se aplica en el servidor: sin privados, invitaciones, menciones ni estado. Privacidad "quién puede escribirme" y "quién ve mi estado". |
| **Grupos (clanes)** | Nombre, etiqueta, color, descripción, mensaje del día, mensaje fijado. Roles Líder/Oficial/Miembro/Recluta con permisos configurables, **canales** con rol mínimo, eventos con aviso 5 min antes, grupo principal. |
| **Parties** | Grupo rápido en memoria que desaparece cuando se quedan sin miembros conectados. |
| **Presencia** | En línea, Ausente (AFK detectado en el servidor), No molestar, Invisible, estado personalizado y dimensión solo si el jugador la comparte. Se envía solo a quien le interesa, por deltas cada 250 ms. |
| **Interfaz** | Panel de 3 columnas que se adapta a 2 columnas o pestañas según el ancho escalado; perfil con skin 3D; ajustes; toasts con prioridades, agrupación y "no molestar inteligente"; widget de HUD; **Respuesta Rápida** con `Y`. |
| **Compartir** | `[coords]` e `[item]` los genera el servidor (no se pueden falsificar): ítem con tooltip, coordenadas con distancia y dirección. |
| **Nametags** | Etiqueta `[TAG]` del grupo junto al nombre (respeta invisibilidad, agacharse y distancia). Fallback opcional con teams para clientes vanilla. |
| **Moderación** | Validación total en el servidor, rate limit de paquetes, anti-spam con silencio automático, filtro de palabras (config, regex y datapacks), reportes con contexto, silencios, historial, inspección, disolver grupos, "spy" opcional **y visible**, registro de auditoría, exportar y borrar datos. |
| **Integraciones** | LuckPerms (Fabric Permission API), Text Placeholder API, ModMenu. Todas opcionales. |
| **API** | `SocialModServerAPI`, `SocialModClientAPI` y eventos (`ChatEvents`, `GroupEvents`, `PresenceEvents`). |

## Instalación

1. Servidor: Fabric Loader + Fabric API + el jar de SocialMod de tu versión (`socialmod-<versión>+mc26.x.jar`).
2. Cliente (opcional): lo mismo para tener la UI. Sin el mod en el cliente todo funciona con comandos.
3. La configuración se crea en `config/socialmod/server.json` (servidor) y `config/socialmod/client.json` (cliente).

Teclas por defecto: **`Y`** Respuesta Rápida (`Mayús+Y` abre el chat completo) y **`K`** panel social. Si chocan con otra
tecla se avisa al entrar al mundo en lugar de quitársela a otro mod.

## Documentación

- [Comandos y permisos](docs/commands-and-permissions.md)
- [Configuración](docs/configuration.md)
- [API para otros mods](docs/api.md)
- [Estado de la implementación respecto al plan](docs/implementation-status.md)
- [Pruebas y CI](docs/testing.md)
- [Mixins](MIXINS.md): ninguno.

## Compilar

```bash
./gradlew build                          # las 3 versiones
./gradlew :26.3:build :26.3:runGameTest   # una versión con sus gametests
./gradlew :26.3:runClientGameTest         # test de cliente con capturas
```

Los jars quedan en `versions/<versión>/build/libs/`.

## Privacidad

Los mensajes los guarda y gestiona **el servidor** (en `<mundo>/socialmod/`), con retención configurable (por defecto
30 días o 500 mensajes por conversación). El staff puede consultarlos para moderar y la UI lo indica. No hay cifrado
de extremo a extremo. Cada jugador puede exportar o borrar sus datos (`/socialmod data export|delete`) si el servidor
lo permite.

Licencia MIT.
