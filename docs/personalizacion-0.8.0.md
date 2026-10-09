# Elementos de SocialMod en FancyMenu — 0.8.0

Creado por TakumiStudios. FancyMenu es opcional; sin él se conserva la interfaz nativa. El protocolo sigue siendo 5.

## Barra superior

SocialMod permite que FancyMenu muestre su barra superior. Su visibilidad se controla desde FancyMenu; su atajo
habitual es **Ctrl + Alt + C**. En modo «Usar interfaz original» se desactivan las personalizaciones y la barra
en las pantallas de SocialMod. Reactivar estilos recupera el layout guardado.

## Poner el estandarte en el panel principal

1. Abre el panel de SocialMod y activa la personalización de esa pantalla desde **Customization**.
2. Crea o abre su layout. En el editor, haz clic derecho en el fondo y entra en **New / New Element**.
3. Añade **SocialMod: Estandarte del TEAM**.
4. Haz clic derecho sobre el elemento y elige **Configurar elemento de SocialMod**.
5. Selecciona **Contexto: jugador local** para mostrar el TEAM de quien abre el panel. También puedes elegir
   la selección actual o un destino fijo usando el ID del TEAM.
6. Arrastra y redimensiona el elemento. Añade imágenes para el marco y un texto dinámico para el nombre.
   Ancla la decoración al estandarte para que se muevan juntos.
7. Guarda el layout con **Ctrl + S**. La configuración se guarda en FancyMenu, no en un perfil de SocialMod.

El estandarte utiliza los datos reales del TEAM. Si cambia el equipo o su diseño, se actualiza la vista.
Sin un TEAM disponible se muestra un estandarte blanco; añadir el elemento no crea un equipo ni otorga permisos.

## Módulos completos y controles separados

El catálogo incluye conversaciones, chat, jugadores, búsqueda, TEAM, gestión de TEAM, perfil y relaciones,
ajustes personales, creación de grupos y parties, ajustes del grupo, invitaciones, edición del tag y
estandarte, apariencia, editor visual básico y editor de filas. Solo aparecen los elementos que añadas al layout.

Un **módulo** reutiliza la pantalla nativa dentro de una región del layout. Su ancho determina la escala y el
contenido vertical restante se puede desplazar. Sus listas conservan su propio desplazamiento.

Un **control independiente** reutiliza un botón o campo de un módulo. En su configuración elige el módulo,
el contexto y el control. Se muestran claves estables con un sufijo de ocurrencia para distinguir controles
similares; los controles dinámicos pueden existir solo cuando hay datos o en la página actual del formulario.

El **nombre del formulario compartido** vincula campos y botones separados que pertenecen al mismo módulo
y pantalla. Por ejemplo, un campo y el botón Guardar con el nombre `grupo` editan el mismo borrador.
Usa otro nombre para un formulario independiente. Al cambiar de destino se descarta el borrador anterior;
no se aplica a otro jugador o grupo. Los campos, confirmaciones y permisos son los de las pantallas nativas.

## Contextos

- **Jugador local:** su TEAM, su perfil o su grupo principal, según el módulo.
- **Selección actual:** el TEAM o conversación seleccionado. Un perfil necesita un privado seleccionado.
- **Destino fijo:** ID del TEAM/grupo, UUID del jugador o clave de conversación (`g:<id>:<canal>` o `dm:<uuid>:<uuid>`).

Un destino que no existe o una función sin permisos queda inactiva. Los elementos están soportados únicamente
en las pantallas de SocialMod. Los datos privados no se descargan solo por escribir un identificador.

## Botones normales y texto de FancyMenu

Puedes añadir acciones **SocialMod: Abrir pantalla de SocialMod** y **SocialMod: Control independiente**
a los botones normales de FancyMenu. Su configuración visual permite escoger módulo, contexto y destino.
La segunda acción pulsa un botón del formulario compartido y conserva sus confirmaciones. Para un botón
con imagen, configura su textura y deja vacía su etiqueta; conserva un nombre accesible.

Los placeholders `socialmod_data_team_name`, `socialmod_data_team_tag`, `socialmod_data_team_icon`,
`socialmod_data_player_name`, `socialmod_data_status`, `socialmod_data_conversation` y `socialmod_data_unread`
aceptan los valores `context` (`self`, `selected`, `fixed`) y `target`. Los placeholders anteriores siguen disponibles.

Ejemplo para un texto normal de FancyMenu:

```json
{"placeholder":"socialmod_data_team_name","values":{"context":"self","target":""}}
```

## Compatibilidad

Los layouts anteriores y los datos del servidor se conservan. Los ajustes básicos siguen formando la apariencia
de partida; FancyMenu permite personalizar sus elementos. SpiffyHUD conserva su función de personalización del HUD.
El modo original personal o global oculta los elementos añadidos y vuelve a la base sin borrar el layout.

Documentación oficial: [inicio y barra](https://docs.fancymenu.net/docs/en-US/home),
[anclajes](https://docs.fancymenu.net/docs/en-US/positioning-elements),
[elementos](https://github.com/Keksuccino/FancyMenu-Dev-Docs/wiki/Elements),
[acciones](https://github.com/Keksuccino/FancyMenu-Dev-Docs/wiki/Actions) y
[placeholders](https://github.com/Keksuccino/FancyMenu-Dev-Docs/wiki/Placeholders).

## Controles separados y desplazamiento

Añade **SocialMod: Control independiente**, abre **Configurar**, elige el módulo y pulsa el selector de control
hasta elegir su nombre. Esto funciona en todos los módulos que incluyan botones o campos. En **Conversaciones**
puedes elegir Buscar conversaciones, Buscar jugadores, Nuevo grupo y Ajustes. Comparte el nombre de formulario
con el módulo incrustado para conservar la búsqueda y los borradores. **Ocultar control original: Sí** evita
que aparezca dos veces dentro del módulo; cambia a No si necesitas ambos.

Mueve y redimensiona el control desde FancyMenu. Conserva su acción y permite personalizar fondos, etiquetas
e iconos. Los controles se identifican internamente por claves estables; el selector muestra su nombre traducido.
Los controles dinámicos aparecen cuando están disponibles en el formulario.

El historial tiene una barra vertical: arrastra su cursor para consultar mensajes anteriores o utiliza la rueda
sobre el chat. Llegar al inicio carga mensajes antiguos. Los mensajes nuevos conservan la posición si estás
consultando el historial. Los módulos movidos o escalados envían los eventos a las coordenadas de su contenido.

## Estandarte y TAG del TEAM

**SocialMod: TEAM banner / Estandarte del TEAM** muestra un estandarte blanco cuando no hay equipo.
Al pulsarlo abre el editor si eres líder o administrador y el equipo no está archivado. Si falta el equipo o
no tienes permiso, muestra un aviso que explica el motivo y permite volver a la pantalla. La acción **SocialMod: Editor de estandarte** (`socialmod_edit_team_banner`) permite abrir
el mismo editor desde cualquier botón de FancyMenu en una pantalla social:

```json
{"context":"self"}
```

La acción de búsqueda (`socialmod_search_players`) abre Buscar jugadores; acepta `{}` para el jugador local.
Ambas permiten configurar el contexto y destino desde el editor de acciones.

**SocialMod: TAG del TEAM con icono** (`socialmod_team_tag_view`) muestra solo el icono y la etiqueta de equipo
con su color, sin nombre de jugador ni rango. Admite los contextos local, seleccionado y fijo. Sin equipo no
muestra texto. El formato utiliza los ajustes de iconos y corchetes del TAG.

## Retirada de estilos incluidos

Dedsafio y Navidad ya no se incluyen. Al cargar o importar una configuración que los utilice se restablecen
su apariencia y sus filas al diseño predeterminado y se desactivan sus layouts. Se conservan los archivos de
imágenes del usuario y los layouts ajenos. El ejemplo neutro `clean` sigue disponible en `docs/examples/series/`.

Creado por TakumiStudios.

## Gestión del TEAM

Al abrir Gestionar se conserva el TEAM seleccionado en el catálogo; si no habías seleccionado ninguno, se carga
tu TEAM actual. El nombre y el color iniciales corresponden a ese destino. Sin destino, los botones que requieren
un TEAM quedan desactivados y su ayuda indica que debes elegir uno de la lista. El título muestra el TEAM que
estás editando. Los errores de nombre, color, emblema, jugador y permisos se explican por separado, también
al usar comandos.
