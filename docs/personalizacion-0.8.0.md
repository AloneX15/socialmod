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

## Acciones individuales de navegación

En **Add Action → SocialMod** cada panel tiene su propia acción: conversaciones, chat, jugadores, búsqueda,
catálogo y gestión de TEAM, perfil, ajustes personales, crear grupo, ajustes del grupo, invitaciones,
TAG, estandarte, apariencia y editores básico y de filas. También hay accesos a personalización avanzada,
editor de pantalla FancyMenu, editor HUD Spiffy, respuesta rápida y volver.

Para abrir los ajustes administrativos del TEAM, elige **SocialMod: Gestión de TEAM**
(`socialmod_open_team_management`). Con `{}` usa tu TEAM; con contexto `selected` conserva la selección
actual y con `fixed` admite un ID concreto. Requiere los mismos permisos de administración que el botón nativo.
Las acciones de estilo de grupo, grupo nuevo y estandarte del borrador TAG reutilizan sus controles nativos,
conservando el borrador del formulario `main` (o el nombre que configures). El selector de **Control independiente**
permite escoger los controles que dependen de la propiedad o página actual del editor.
Los cuatro identificadores anteriores siguen funcionando en los layouts guardados.

## Fondo y paneles sin controles

FancyMenu reconoce el fondo nativo como **Fondo del panel** (`socialmod_panel_background`). Puedes ocultarlo,
moverlo, cambiar sus dimensiones o sustituir su textura sin ocultar las listas.

Los elementos **SocialMod: Conversaciones** y **SocialMod: Jugadores** muestran solo su contenido por defecto.
En su configuración, **Mostrar controles nativos** permite recuperar la búsqueda y botones integrados;
**Mostrar fondo del panel** permite dejar el contenido transparente sobre tu imagen de FancyMenu.
Los controles independientes siguen disponibles aunque estén desactivados dentro del panel.
Jugadores dispone de su propia columna en distribuciones estrechas o con pestañas y carga los miembros del
TEAM del contexto configurado, además de los jugadores conectados. Usa `self`, `selected` o un ID `fixed`.

## Listas adaptables y roles

Conversaciones y Jugadores usan el ancho y alto reales del elemento FancyMenu sin escalar la fuente.
Puedes mover y redimensionar cada lista; sus filas, zonas clicables y desplazamiento se mantienen independientes,
incluso si dos elementos comparten formulario. El TAG se coloca a 4 píxeles del nombre y pasa a una segunda
línea si no cabe. La ayuda muestra el nombre y TAG completos cuando el espacio es reducido.

Los miembros se muestran con los PNG incluidos: corona para Líder, corazón verde para VIP y persona para
Miembro, Oficial y Recluta. La ayuda distingue el rol. Las filas personalizadas usan estos iconos en los campos
`role` y en el avatar de miembros; las filas de jugadores conectados conservan su cabeza.

VIP existe exclusivamente en TEAM. Su líder puede abrir el perfil de un miembro desde la lista y pulsar
**Conceder VIP** o **Retirar VIP**. VIP mantiene acceso al chat como Miembro y solo añade edición del estandarte,
también desde el elemento o acción FancyMenu. No permite renombrar, cambiar color/icono ni gestionar roles.
Retirar VIP devuelve a Miembro. El servidor comprueba todos los permisos y destinos.

El rol se guarda con el TEAM. Al archivarlo conserva su rol histórico y lo recupera si vuelve a incorporarse
al TEAM restaurado; restaurar no reasigna automáticamente a jugadores que hayan cambiado de TEAM.
Los ordinales anteriores de roles y acciones se conservan. Actualiza cliente y servidor conjuntamente.
