# Personalización de SocialMod 0.7.0

> Desde 0.8.0 puedes añadir el estandarte y controles de otras pantallas al panel principal desde FancyMenu.
> Consulta [el catálogo de elementos](personalizacion-0.8.0.md).

Actualiza cliente y servidor juntos: esta versión utiliza el protocolo 5. FancyMenu y SpiffyHUD son opcionales.

## Estandarte del TEAM

En **TEAM → Color y emblema** puedes configurar el tag y abrir **Estandarte**. El estandarte tiene un color base
y hasta seis patrones de Minecraft. Pulsa el patrón o su color para cambiarlos y utiliza las flechas para ordenar
las capas. **Guardar** conserva el borrador; **Cancelar** lo descarta. La creación envía el estandarte con el TEAM.

Después, selecciona el TEAM y pulsa **Editar estandarte del TEAM**. Solo su líder o un administrador pueden guardar
el cambio. Se muestra en el catálogo y junto al selector del tag. Los equipos anteriores reciben uno blanco.

## Botones e imágenes con FancyMenu

Abre **Editor visual → Editar pantalla con FancyMenu**. Los perfiles y layouts se gestionan en FancyMenu.
SocialMod ya no ofrece selección de diseño, instalación con respaldo, perfiles de serie ni restauración del perfil anterior.

- Selecciona `socialmod_button_socialmod.panel.new_group`, reduce su tamaño, deja la etiqueta vacía y asigna una
  textura de icono —por ejemplo, un libro—. Conserva la acción de crear un grupo y su nombre accesible.
- Los demás botones funcionan del mismo modo. Puedes personalizar posición, tamaño, texto y estados de imagen.
- Oculta `socialmod_input_socialmod.panel.search` si prefieres una interfaz solo con botones. Conserva
  `socialmod_button_socialmod.search.button` para abrir la búsqueda universal; puede llevar una imagen o glifo.
  La barra original filtra conversaciones, mientras que este botón busca jugadores en todo el servidor.
- Los estandartes tienen identificadores `socialmod_team_banner_<id>`, `socialmod_tag_banner_preview` y
  `socialmod_banner_preview`, `socialmod_banner_container`, `socialmod_tag_banner_container` y `socialmod_tag_banner_name`. Puedes moverlos y escalarlos, añadir fondos, marcos y una placa con nombre usando
  las capas de FancyMenu. El estandarte conserva sus proporciones; la decoración no cambia los datos del TEAM.

Las imágenes se configuran mediante FancyMenu. Los símbolos y emojis requieren una fuente de Minecraft que los
incluya; una textura es la opción más fiable para un icono gráfico.

## Ajustes básicos y HUD

Los administradores pueden abrir **Ajustes visuales básicos** con o sin integraciones. Sus valores forman la
apariencia de partida; FancyMenu sustituye las propiedades personalizadas de cada control. Una apariencia local
antigua ya no sustituye por completo el diseño básico publicado por el servidor.

SpiffyHUD sustituye los componentes sociales configurados. Los componentes restantes usan el HUD nativo y un fallo
de integración recupera su equivalente básico. Las preferencias de accesibilidad siguen disponibles.

## Volver a la interfaz original

Cada jugador puede abrir **Ajustes → Apariencia → Usar interfaz original**. Su elección se recuerda por servidor.
**Reactivar mis estilos** recupera sus personalizaciones cuando el servidor permite estilos.

Con `admin.visuals`, **Usar interfaz original para todos** activa la base global. Prevalece sobre la elección
personal, se conserva tras reiniciar y se aplica también a jugadores que entren después. **Reactivar estilos para
todos** permite recuperar los estilos; quienes eligieron personalmente la base continúan usándola.

La base omite temas y layouts de SocialMod, decoraciones, filas personalizadas y reemplazos de su HUD. Conserva
los diseños y archivos para reactivarlos. No cambia las personalizaciones de otras pantallas.

## Buscar jugadores y abrir privados

El botón de búsqueda abre el directorio del servidor. Sin texto muestra jugadores conectados cuya presencia puedes
ver. Con dos o más caracteres también busca jugadores desconectados conocidos por SocialMod. Los resultados se
envían en páginas de 20; las flechas recorren todos los resultados incluso en pantallas pequeñas.

Pulsa un resultado para abrir o reutilizar su mensaje directo. No se envía ningún mensaje automáticamente.
La búsqueda no revela la conexión de jugadores invisibles y el envío sigue respetando permisos, bloqueos y privacidad.

Los ZIP y guías de serie de versiones anteriores son material histórico; no se elimina ningún archivo del usuario.
