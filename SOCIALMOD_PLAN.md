# SocialMod — Documento de Diseño Técnico (TDD)
**Autor:** TakumiStudios · **Plataforma:** Fabric · **Minecraft:** 26.1.x, 26.2.x, 26.3.x · **Java:** 25

> ⚠️ Antes de publicar, comprobar en Modrinth y CurseForge que el nombre "SocialMod" y el modid `socialmod` estén libres. Es un nombre muy genérico.

---

## 1. Descripción del proyecto
**SocialMod** añade una **capa social completa** dentro de Minecraft para servidores y mundos multijugador, sin sustituir el chat vanilla:

- Mensajes privados, grupos (clanes) con **canales** y fiestas temporales (*parties*).
- Lista de jugadores conectados, búsqueda, amigos, solicitudes y bloqueos.
- Perfiles con cabeza o skin del jugador, estado y grupo.
- Notificaciones flotantes (*toasts*) configurables y **Respuesta Rápida** (*Quick-Reply*).
- Identidad del grupo junto al nombre del jugador (*nametag*).
- **Buzón:** los mensajes enviados a jugadores desconectados se entregan al conectarse.
- Compartir **coordenadas** (con un clic se crea un waypoint en Xaero o JourneyMap) e **ítems** (tooltip con su información).
- Herramientas de **moderación** para el staff del servidor.

**Objetivo:** que sea la capa social de referencia en Fabric. Debe ser útil sin configurar nada, personalizable a fondo y **nunca romper otros mods**.

---

## 2. Principios fundamentales
```text
                         SOCIALMOD
                             |
     +-----------+-----------+-----------+-----------+
     |           |           |           |           |
  ESTETICA    COMPATIBLE  CONFIGURABLE  SEGURO Y    LIGERO
  VANILLA     CON MODS    (pack/server) MODERABLE   (red/CPU)
```
1. **Estética vanilla:** widgets, fuentes, sonidos y texturas del estilo del juego. Todo se puede cambiar con resource packs.
2. **Compatibilidad obligatoria:** nunca debe ser la causa de un crash. Usa eventos de Fabric API, mixins mínimos (MixinExtras, nunca `@Overwrite` ni `@Redirect`) y todas las integraciones son opcionales (ver sección 14).
3. **Personalización por capas:**
   - el **servidor** decide las reglas (config y datapack);
   - el **resource pack** decide el aspecto;
   - el **jugador** decide sus preferencias personales (config del cliente).
4. **Modularidad:** cada módulo (chat, grupos, presencia, notificaciones, HUD, nametags, integraciones) se puede desactivar por separado.
5. **No interferencia:** no se muestran notificaciones en mal momento (modo combate o pantalla completa), no se manda tráfico innecesario y no se hace trabajo en cada tick.
6. **Funciona sin el mod en el cliente:** los jugadores vanilla o de Bedrock (Geyser) pueden usar comandos y reciben los mensajes en el chat normal.
7. **Seguro y moderable** (nuevo): el servidor valida todo, hay límites contra spam y el staff puede moderar.

---

## 3. Stack técnico
| Elemento | Elección |
|---|---|
| Minecraft | 26.1.x, 26.2.x, 26.3.x (multiversión con **Stonecutter**) |
| Loader / API | Fabric Loader + Fabric API (única dependencia obligatoria) |
| Mappings | Oficiales de Mojang (Yarn no se mantiene desde 26.1) |
| Java | 25 |
| Build | Gradle + Loom (`net.fabricmc.fabric-loom`) |
| Lado | **Servidor obligatorio, cliente opcional**: con el mod en el cliente se obtiene la UI completa; sin él, comandos y chat |
| Paquete | `com.takumistudios.socialmod` · modid `socialmod` |

---

## 4. Arquitectura
### 4.1 Módulos
```text
socialmod/
  common/        modelos (PlayerProfile, Group, Channel, Message), payloads, validacion
  server/        servicios: ChatService, GroupService, PresenceService, Mailbox,
                 ModerationService, StorageBackend, PermissionBridge, comandos
  client/        pantallas, HUD, toasts, quick-reply, cache local, keybinds
  integrations/  voicechat, placeholders, luckperms, claims, mapas (cargados solo si el mod existe)
  api/           SocialModServerAPI, SocialModClientAPI, eventos
```

### 4.2 Servidor como fuente de verdad (corrige el "relay sin estado")
El TDD original guardaba los historiales **solo en el cliente**, con SQLite. Eso impide entregar mensajes a jugadores desconectados, se pierde al cambiar de PC, deja a los nuevos miembros sin historial, hace imposible moderar y añade librerías nativas.

**Nuevo diseño:**
- El **servidor** guarda grupos, amistades, bloqueos y el historial reciente, con **retención configurable** (por defecto 30 días o los últimos 500 mensajes por conversación).
- **`StorageBackend` intercambiable:**
  - `file` (por defecto): JSON comprimido por conversación más un índice. Sin dependencias, en `world/socialmod/`.
  - `h2`: Java puro, sin librerías nativas, para servidores grandes.
  - `mysql` / `mariadb`: para redes con varios servidores.
- Escritura **asíncrona** en un hilo propio con una cola. El hilo principal del servidor nunca espera al disco.
- El **cliente** guarda una caché opcional por servidor (`config/socialmod/cache/<serverHash>/`) para abrir el panel al instante. No es la fuente de verdad.
- **Transparencia:** la UI avisa de que *los mensajes los gestiona el servidor y el staff puede consultarlos para moderar*. No se promete cifrado de extremo a extremo.

### 4.3 Red
- Payloads de Fabric (`CustomPacketPayload` + `StreamCodec`) con un **handshake de versión de protocolo**. Si las versiones no coinciden, el cliente funciona en modo "solo chat" y no hay crash.
- **Sincronización por eventos (delta):** solo se envía lo que cambia (`ONLINE`, `AFK`, `DND`, `GROUP_CHANGE`).
- **Escalabilidad:** la presencia **solo se envía a quien le interesa** (amigos, miembros del grupo y quien tenga el panel abierto). Enviar cada cambio a todos los jugadores crece en O(n²) y no escala a más de 100 jugadores.
- **Agrupación:** los cambios se juntan y se envían cada 250 ms como máximo.
- **Paginación** de listas grandes e historial (*lazy loading*), dentro de los límites de tamaño de los payloads.
- **Rate limit por jugador** en todos los paquetes C→S. Los paquetes malformados se ignoran y se registran.
- **Redes con proxy (Velocity), en v2:** mensajes y presencia entre servidores con Redis o un plugin puente.

---

## 5. Funcionalidades sociales
### 5.1 Mensajes privados
- Historial, indicador de "escribiendo..." y "leído" (ambos opcionales y desactivables por privacidad), editar o borrar un mensaje durante 2 minutos.
- **Buzón:** los mensajes a jugadores desconectados se entregan al conectarse, con un resumen ("Tienes 3 mensajes de Alex").
- **Menciones** `@jugador` y `@grupo`, que generan una notificación prioritaria.
- **Formato seguro:** solo un subconjunto de markdown (`**negrita**`, `*cursiva*`, `` `código` ``). **Nunca** se acepta JSON de texto enviado por el cliente, para evitar eventos de clic maliciosos o suplantación.
- **Enlaces:** se pueden pulsar, pero piden confirmación (igual que vanilla) y el servidor puede desactivarlos.

### 5.2 Grupos (clanes) y parties
- **Grupo persistente:** nombre, etiqueta corta, color, emblema (de una lista de iconos o un banner de Minecraft), descripción y mensaje del día.
- **Roles configurables:** Líder, Oficial, Miembro, Recluta, con permisos por rol (invitar, expulsar, gestionar canales, fijar mensajes).
- **Canales** dentro del grupo (`#general`, `#comercio`, `#eventos`...): se pueden crear y borrar, silenciar por canal y tener permisos por rol.
- **Party temporal** (nuevo): grupo rápido para una sesión (ir al End, una mazmorra), que desaparece al salir todos. Opcionalmente muestra la vida de los miembros en el HUD.
- **Eventos del grupo** (nuevo): crear un evento con fecha y hora, que avisa a los miembros conectados antes de empezar.
- **Grupo principal:** el que se muestra en el nametag y en los placeholders.
- **Límites** por permiso o config: grupos por jugador, miembros por grupo, canales por grupo.

### 5.3 Amigos y bloqueos
- Solicitudes de amistad, favoritos y notas privadas sobre un jugador.
- **Bloquear se aplica en el servidor:** un jugador bloqueado no puede mandarte mensajes privados, invitarte ni mencionarte, y no ve tu estado.
- **Privacidad:** "quién puede escribirme" (todos / amigos / nadie) y "quién ve mi estado".

### 5.4 Presencia y estado
- Estados: En línea, Ausente (AFK automático tras X minutos sin actividad, detectado en el servidor), No molestar y Invisible.
- Estado personalizado de texto, con límite de caracteres y filtrado.
- Dimensión o actividad visible **solo si el jugador lo permite** (por defecto oculto, para que nadie pueda localizarlo).

### 5.5 Compartir dentro del chat (nuevo)
- **Coordenadas:** `[coords]` inserta la posición actual. Al pulsarla, crea un waypoint en Xaero's Minimap o JourneyMap si están instalados, o muestra la distancia y la dirección si no.
- **Ítems:** `[item]` muestra el ítem de la mano con su tooltip. El servidor genera el contenido; el cliente no puede falsificarlo.
- **Ping en el mundo:** marcar un punto que los miembros de la party ven durante 10 segundos.

---

## 6. Notificaciones (toasts)
```text
+------------------------------------------------------+
| [cabeza] ALEX                          hace 10 s     |
| "Nos vemos en x:120, z:-450?"                        |
| [Y] Responder   [Shift+Y] Abrir chat                 |
+------------------------------------------------------+
```
- **Posición:** `TOP_RIGHT` (por defecto), `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT` o **margen personalizado**. Se colocan automáticamente para no tapar minimapas ni otras notificaciones.
- **Duración** de 3 a 15 s y **animaciones** de deslizamiento o fundido (se pueden desactivar).
- **Estilo:** fondo tipo toast vanilla, temas de resource pack y cabeza del jugador o emblema del grupo.
- **Sonido** por tipo de notificación (privado, mención, invitación, evento), con volumen propio y un deslizador en la configuración.
- **Apilamiento:** máximo N visibles a la vez, y los mensajes del mismo remitente se agrupan ("Alex (3 mensajes)").
- **Prioridades** (nuevo): las menciones y las invitaciones se muestran por encima de los mensajes normales de grupo.
- **Modo No molestar inteligente** (nuevo): se ocultan durante el combate (al recibir daño o con un jefe activo), con una pantalla abierta o en modo pantalla completa, y se muestran como resumen al terminar.
- **Implementación:** se dibujan registrando un elemento de HUD de Fabric API, para convivir y ordenarse con otros HUD. No hay mixins sobre el `ToastManager` vanilla.

---

## 7. Calidad de vida
### 7.1 Respuesta Rápida (Quick-Reply)
```text
+------------------------------------------------------+
| Respondiendo a: Alex                    [Esc] cerrar |
+------------------------------------------------------+
| > Voy en camino con los materiales!_                 |
+------------------------------------------------------+
```
- **Tecla por defecto: `Y`.** Se evita `R` y `U`, que usan JEI, REI y EMI. Se puede cambiar en Controles y se avisa si hay conflicto.
- `Tab` cambia entre las últimas 5 conversaciones. Hay historial con las flechas y autocompletado de `@menciones`.
- Al abrirse no pausa el juego ni suelta el ratón más de lo necesario.

### 7.2 HUD social
- Widget pequeño: mensajes no leídos, estado actual y canal de voz activo (si existe la integración).
- Posición y escala configurables, y se oculta con F1 como el resto del HUD.

### 7.3 Accesibilidad (nuevo)
- Compatible con el **Narrador** de Minecraft: los toasts y los mensajes se leen en voz alta.
- **Los estados no dependen solo del color:** además del color llevan un icono con forma distinta (●, ◐, ⊘), pensando en el daltonismo.
- Escala de texto propia y modo de alto contraste.

---

## 8. Interfaz y wireframes
> Los wireframes son orientativos. **La fuente de Minecraft no tiene emojis**, así que en el juego todo icono será una textura (`assets/socialmod/textures/gui/icons/`).

### 8.1 Vista principal (3 columnas)
```text
+-------------------------+-------------------------------+---------------------+
| CONVERSACIONES          | GRUPO: Team Forest  #general  | EN LINEA (24)       |
+-------------------------+-------------------------------+---------------------+
| [Buscar...]             | Steve: Alguien tiene diamantes| (o) Alex   Lider    |
|                         | Alex : Tengo un stack en base | (o) Steve           |
| -- DIRECTOS ----------- | Luna : Voy para alla          | (-) Luna   AFK      |
| (o) Alex  "Mira la b.." |                               |                     |
| (x) Steve "Nos vemos.." | [Fijado] Reunion sabado 18:00 | -- MIEMBROS ------- |
|                         |                               | [L] Alex            |
| -- GRUPOS ------------- |                               | [O] Steve           |
| [T] Team Forest     (3) |                               | [M] Luna            |
| [E] Exploradores        |                               |                     |
|                         | > Escribe un mensaje...       | [+ Invitar]         |
| [+ Nuevo grupo]         | [coords] [item]               | [Ajustes grupo]     |
+-------------------------+-------------------------------+---------------------+
```

### 8.2 Perfil rápido
```text
+-----------------------------------------+
| [skin 3D]  Alex                         |
|            Team Forest - Lider          |
|            (o) En linea                 |
| [Mensaje] [Anadir amigo] [Voz] [Bloquear]|
+-----------------------------------------+
```

### 8.3 Adaptación de pantalla (corregido)
Los breakpoints van en **píxeles escalados de la GUI** (`guiScaledWidth`), no en píxeles reales. Así funciona con cualquier GUI Scale:
| Ancho escalado | Modo |
|---|---|
| ≥ 480 | 3 columnas |
| 320 – 479 | 2 columnas (la lista de jugadores pasa a una pestaña) |
| < 320 | Pestañas a pantalla completa |

El panel respeta los márgenes que ocupan los minimapas y otros HUD (configurable).

---

## 9. Moderación y seguridad (nuevo, imprescindible en servidores)
- **Toda acción se valida en el servidor:** permisos, bloqueos, límites, pertenencia al grupo y longitud del mensaje (máximo 256 caracteres, configurable).
- **Anti-spam:** límite de mensajes por segundo, detección de mensajes repetidos y silencio automático temporal.
- **Filtro de palabras** configurable (lista o expresiones regulares) y un hook en la API para usar filtros externos.
- **Reportes:** el jugador reporta un mensaje y el staff recibe el reporte con su contexto (los mensajes de alrededor).
- **Comandos de staff:** `/socialmod mod mute|unmute|history|disband|inspect`, siempre con permisos de LuckPerms.
- **"Spy" de staff opcional y visible:** si el servidor lo activa, se indica en la UI de los jugadores.
- **Restricciones de cuenta:** si el cliente tiene el chat desactivado (opciones de chat de Minecraft o restricciones de la cuenta Microsoft), la UI de chat no se abre y no se reciben mensajes.
- **Privacidad de datos:** `/socialmod data export` y `/socialmod data delete` para que un jugador obtenga o borre sus datos (siempre que las reglas del servidor lo permitan), con la retención configurable de la sección 4.2.
- **Registro de auditoría** de acciones de moderación y de gestión de grupos.

---

## 10. Jugadores sin el mod (vanilla / Bedrock)
- Comandos equivalentes: `/msg`, `/r`, `/g <mensaje>`, `/g join|leave|invite`, `/friend`, `/party`.
- Los mensajes les llegan como chat de sistema con un formato configurable.
- Las notificaciones les llegan en la action bar o en el chat.
- Con **Polymer** (si está disponible para 26.x) se puede ofrecer una GUI con menús de cofre a clientes vanilla y Bedrock.

---

## 11. Nametags e identidad de grupo
- **Con el mod en el cliente:** se dibuja la etiqueta del grupo encima o debajo del nombre, mediante el evento de renderizado de entidades. Respeta la distancia, el agacharse y la invisibilidad (no revela a jugadores invisibles).
- **Sin el mod:** fallback **opcional y desactivado por defecto** con un prefijo de team del scoreboard, porque choca con otros mods o plugins que usan teams (TAB, mods de scoreboard).
- El jugador puede ocultar las etiquetas de los demás en su cliente.

---

## 12. Integraciones con el ecosistema (todas opcionales)
| Integración | Uso |
|---|---|
| **LuckPerms** (vía fabric-permissions-api) | Nodos `socialmod.group.create`, `socialmod.chat.private`, `socialmod.limit.groups.<n>`, `socialmod.mod.*` |
| **Text Placeholder API** (Patbox) *(corrige "PAPI", que es de Bukkit)* | Placeholders `%socialmod:main_group%`, `%socialmod:unread%`, `%socialmod:status%`. También se pueden usar placeholders externos en los formatos |
| **Simple Voice Chat** (API de grupos) / **Plasmo Voice** | Crear y unirse al grupo de voz del clan o de la party desde la UI |
| **Claims:** Open Parties and Claims, FTB Chunks, Cadmus | Sincronizar grupos con parties o equipos, en ambas direcciones y configurable |
| **Mapas:** Xaero's Minimap, JourneyMap | Crear waypoints desde las coordenadas compartidas; respetar el espacio que ocupa el minimapa |
| **Mods de protección** (por ejemplo SecureLock) | Exponer los grupos en la API para que otros mods den permisos por grupo |
| **ModMenu + YACL / Cloth Config** | Pantalla de configuración del cliente |
| **Discord Rich Presence** | **Addon aparte** (jar separado). Opcional y **desactivado por defecto**; nunca muestra coordenadas, y la dimensión solo si el jugador lo activa |

---

## 13. Rendimiento y renderizado
- La UI usa `GuiGraphics` y los widgets vanilla. Sin llamadas directas a OpenGL ni mixins de renderizado, para ser compatible con Sodium, Iris y otros mods de renderizado.
- **Skins y cabezas:** se usa el **gestor de skins de vanilla** (las texturas que el cliente ya descarga), sin descargas propias a la API de Mojang. Así se evitan límites de peticiones y funciona con SkinsRestorer y en servidores offline. Mientras carga se muestra Steve o Alex.
- Render 3D del perfil solo cuando la pantalla está abierta. Las cabezas se guardan en caché como texturas.
- **Objetivos:** 0 asignaciones por frame en el HUD cuando no hay notificaciones, menos de 0,05 ms por tick en el servidor con 200 jugadores, y medirlo con Spark.

---

## 14. Compatibilidad máxima ("nunca ser el mod que lo rompe")
- **Única dependencia obligatoria: Fabric API.** Todo lo demás va en `suggests` / `recommends`.
- **Mixins mínimos**, siempre con MixinExtras, documentados en `MIXINS.md`. Los de integración son condicionales (`IMixinConfigPlugin` + `isModLoaded`). Si una inyección falla, la función se desactiva con un aviso y el juego no crashea.
- Cada handler va dentro de `try/catch`: un error en SocialMod nunca tumba el servidor ni el cliente.
- **No toca el chat vanilla:** no intercepta ni modifica la firma de mensajes ni el sistema de reportes de Mojang.
- **No incluye dentro del jar librerías con código nativo.** H2 y los drivers SQL se descargan aparte o se usan solo si el servidor los instala.
- **Matriz de pruebas en la CI** con: Sodium, Iris, Lithium, ModernFix, FerriteCore, JEI/REI/EMI, Xaero's, JourneyMap, Simple Voice Chat, OPAC, FTB Chunks, Polymer, Geyser, Styled Chat, mods de HUD y de chat (Chat Heads, Chat Patches...).
- **Keybinds:** se detectan conflictos al arrancar y se avisa en vez de quitarle la tecla a otro mod.

---

## 15. Personalización (data-driven, corregido)
Separación clara de quién controla qué:
| Capa | Archivo | Contenido | Recarga |
|---|---|---|---|
| Servidor (reglas) | `config/socialmod/server.json` + datapack `data/socialmod/` | Límites, roles por defecto, canales por defecto, filtros, retención, módulos activos | `/socialmod reload` |
| Resource pack (aspecto) | `assets/socialmod/themes/*.json` + texturas | Tema, colores, iconos, sonidos, layout | F3+T (reload listener) |
| Cliente (preferencias) | `config/socialmod/client.json` (pantalla de ajustes) | Posición y duración de toasts, sonidos, tecla, privacidad, HUD | Al instante, desde la UI |

Ejemplo de tema (**anclas y pesos** en vez de x/y absolutos, que se rompen con otra escala o idioma):
```json
{
  "layout": "three_column",
  "columns": [
    { "id": "conversations", "weight": 1, "min_width": 110 },
    { "id": "chat",          "weight": 2, "min_width": 160 },
    { "id": "players",       "weight": 1, "min_width": 90, "collapsible": true }
  ],
  "background": "socialmod:textures/gui/themes/dark_stone.png",
  "colors": { "text": "#E0E0E0", "accent": "#55FF55", "unread": "#FFAA00" },
  "toast": { "texture": "socialmod:textures/gui/toast/default.png" }
}
```
- El JSON se valida con **Codecs**. Si hay un error, se registra con el archivo y la línea y se usa el tema por defecto, sin crashear.
- Los **ajustes por defecto del cliente** se pueden incluir en un modpack (`config/socialmod/client-defaults.json`) sin pisar los cambios que haya hecho el jugador.

---

## 16. API pública (separada por lado, corregida)
```java
// Servidor
public interface SocialModServerAPI {
    Optional<Group> getMainGroup(UUID player);
    Collection<Group> getGroups(UUID player);
    boolean areFriends(UUID a, UUID b);
    boolean isBlocked(UUID blocker, UUID target);
    void sendSystemNotification(ServerPlayer player, Notification notification);
    void registerStatusProvider(ResourceLocation id, StatusProvider provider);
    void registerMessageFilter(MessageFilter filter);
}

// Cliente
public interface SocialModClientAPI {
    void openPanel();
    void openPrivateChat(UUID target);
    void openGroupChat(UUID groupId);
    void showToast(ToastData toast);
}
```
- **Eventos** de Fabric: `GroupEvents.CREATED/MEMBER_JOINED/MEMBER_LEFT`, `ChatEvents.ALLOW_MESSAGE` (cancelable), `PresenceEvents.STATUS_CHANGED`.
- Se publica como artefacto aparte (`socialmod-api`) en un Maven, con versionado semántico.

---

## 17. Hoja de ruta
| Fase | Contenido | Resultado |
|---|---|---|
| 0 | Proyecto multiversión (Stonecutter 26.1–26.3), CI, StorageBackend `file`, handshake de red | Base sólida |
| 1 (MVP) | Mensajes privados, buzón, amigos y bloqueos, presencia, toasts, comandos para vanilla, anti-spam | **Alpha jugable** |
| 2 | Grupos, roles, canales, parties, nametags, menciones | Beta |
| 3 | Quick-Reply, HUD, compartir coordenadas e ítems, temas JSON, accesibilidad | Beta |
| 4 | Moderación completa, reportes, exportar y borrar datos, LuckPerms, placeholders | **v1.0** |
| 5 | Voice Chat, claims, mapas, API pública, backends H2 y MySQL | v1.x |
| 6 | Redes con proxy (Velocity), Polymer para vanilla y Bedrock, addon de Discord | v2.0 |

---

## 18. Pruebas y publicación
- **Unitarias** (JUnit): servicios de chat, grupos y permisos, filtros, rate limits y codecs.
- **Gametests de Fabric:** varios jugadores falsos enviando mensajes, entrando y saliendo de grupos, y desconexiones en mitad de una acción.
- **Carga:** bots que simulan 200 jugadores (por ejemplo, con un script de clientes headless) para medir el tick y el ancho de banda.
- **Seguridad:** paquetes manipulados (UUIDs ajenos, textos de 1 MB, JSON inyectado), spam y abuso de invitaciones.
- **Compatibilidad:** el modpack de la sección 14 se ejecuta en la CI en cada versión soportada.
- **Publicación:** GitHub Actions → Modrinth y CurseForge (un jar por versión), CHANGELOG y versionado semántico, wiki y Discord de TakumiStudios.
