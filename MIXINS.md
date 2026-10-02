# Mixins

**SocialMod no usa ningún mixin** (PLAN 14: mixins mínimos, nunca `@Overwrite` ni `@Redirect`).

Todo se hace con eventos y APIs de Fabric:

| Necesidad | Cómo |
|---|---|
| Toasts y widget social | `HudElementRegistry` (Fabric Rendering API), sin tocar el `ToastManager` vanilla. |
| Etiqueta de grupo en el nametag | `LivingEntityRenderLayerRegistrationCallback`: una capa modifica el nametag que vanilla ya decidió mostrar (las capas se procesan antes de que se envíe el nametag). |
| Red | `PayloadTypeRegistry` + `ServerPlayNetworking`/`ClientPlayNetworking`. |
| Conexiones, ticks y arranque | `ServerPlayConnectionEvents`, `ServerTickEvents`, `ServerLifecycleEvents`, `ClientTickEvents`. |
| Comandos | `CommandRegistrationCallback`. |
| Temas y filtros de datapack | `ResourceLoader.registerReloadListener`. |
| Teclas | `KeyMappingHelper`. |
| Permisos | Fabric Permission API. |

El chat vanilla no se intercepta: la firma de mensajes y el sistema de reportes de Mojang quedan intactos.
