# TEAM y aspecto de una serie

Disponible desde SocialMod 0.3.0; SocialMod 0.6.0 usa protocolo 4. Actualiza el mod del servidor y de los clientes juntos.

## Elegir y crear un TEAM

Abre el panel social con **K** y pulsa **TEAM**. Puedes pertenecer a varios grupos sociales, pero solo a un TEAM.
El nametag usa el nombre completo, color e icono del TEAM, sin depender del grupo principal.

- Selecciona un TEAM y confirma tu elección. Después, únicamente un administrador puede cambiarla.
- **Crear TEAM y unirme** crea también su chat y consume tu elección. Quien ya eligió no puede crear otro TEAM.
- Los administradores pueden crear teams adicionales sin abandonar el suyo.
- No hay un límite adicional de jugadores por TEAM. Los teams no consumen el límite de grupos sociales del jugador.
- La pertenencia al chat vinculado es automática. No se puede salir, invitar, expulsar ni disolver mediante las acciones de grupos.
- El creador administra los canales. Los jugadores conservan sus grupos y parties independientes.

Al actualizar, los grupos y mensajes anteriores se conservan. Nadie recibe un TEAM automáticamente: hasta elegir,
el nametag muestra únicamente el nombre del jugador. Los permisos normales de Minecraft siguen controlando
invisibilidad, distancia y visibilidad del nombre.

## Administrar teams

En **TEAM → Gestionar**, selecciona un equipo. Introduce el nombre o UUID del jugador para **Asignar** o **Reiniciar**,
o usa **Renombrar**, **Color y emblema**, **Archivar** y **Restaurar** sobre el TEAM seleccionado.
El máximo inicial es **8 teams activos**; puedes cambiarlo desde esta pantalla o mediante `maxTeams` en
`config/socialmod/server.json`. Bajar el máximo no elimina teams existentes.

| Comando | Función |
|---|---|
| `/socialteam list` | Lista teams activos con su identificador |
| `/socialteam choose <id> confirm` | Primera elección, con confirmación explícita |
| `/socialteam create confirm <nombre>` | Crea y elige un TEAM |
| `/socialteam assign <jugador-o-uuid> <id>` | Asigna un TEAM, también a jugadores desconectados |
| `/socialteam reset <jugador-o-uuid>` | Quita la asignación y permite elegir otra vez |
| `/socialteam rename <id> <nombre>` | Cambia el nombre sin cambiar su identidad ni historial |
| `/socialteam style <id> swords;#3366FF` | Cambia icono y color |
| `/socialteam archive <id>` | Archiva TEAM y chat, y libera las elecciones |
| `/socialteam restore <id>` | Recupera TEAM y chat, sin reasignar jugadores |
| `/socialteam limit <1-1000>` | Cambia el máximo de teams activos |

Los comandos administrativos funcionan también desde consola. Los jugadores sin mod pueden usar estos comandos
y los comandos habituales `/g to <etiqueta> <canal> <mensaje>` para el chat vinculado.

**Archivar es reversible:** se guarda el historial y el chat queda de solo lectura para sus antiguos miembros,
respetando los permisos de los canales que tenían. Los miembros pueden elegir otro TEAM inmediatamente.
Restaurar requiere una plaza libre y no deshace elecciones posteriores. Para recuperar la pertenencia,
cada jugador vuelve a elegirlo si tiene su elección disponible, o el administrador lo reasigna.
Los mensajes siguen sujetos a la política de retención configurada del servidor.

Permisos Fabric/LuckPerms:

- `socialmod:team.create`: crear teams, concedido por defecto a los jugadores.
- `socialmod:admin.teams`: administrar teams y máximo; OP nivel 2 por defecto.
- `socialmod:admin.visuals`: publicar el aspecto de la serie; OP nivel 2 por defecto.

Las operaciones quedan en el registro de auditoría. `SocialModServerAPI.getTeam(UUID)` consulta la identidad visible;
el placeholder `socialmod:team` devuelve su nombre. Las consultas antiguas de grupos mantienen su función.

## Editar la presentación

El administrador abre **Editor visual** desde el panel. El aspecto publicado es común para todos los clientes con el mod.
Los ajustes de accesibilidad —contraste, tamaño del texto y movimiento reducido— siguen siendo locales.

1. Elige **Ventana compacta**, **Panel lateral** o **Pantalla completa**. La primera es el valor inicial.
2. Usa **Pantalla** para recorrer panel, ajustes, creación, TEAM, perfil, grupo, estilo, invitaciones y respuesta rápida.
3. Cambia la resolución de la vista previa para comprobar una GUI grande, mediana o pequeña.
4. Selecciona un control, texto o fondo en la vista previa. Arrastra con el botón izquierdo para moverlo y con el derecho
   para redimensionarlo. Sus propiedades aparecen primero en el inspector.
5. Edita las propiedades del inspector y pulsa ✓. Los interruptores cambian con un clic y los colores abren un selector
   RGB con opacidad. Las posiciones y tamaños son fracciones de la pantalla, entre 0 y 1.
6. Usa **Deshacer**, **Rehacer** o **Restablecer**. Restablecer elimina la modificación del componente seleccionado;
   sin selección, restaura el diseño entero.
7. **Publicar** aplica el preset a los clientes y lo guarda en `config/socialmod/visual.json`. Salir sin publicar descarta
   la vista previa local. `/socialmod reload` recarga ese archivo y conserva el último diseño válido si hay errores.

Los controles conservan sus acciones y permisos al cambiar su presentación. Las modificaciones se guardan por modo
y pantalla. Los textos traducidos usan sus claves; los textos dinámicos se identifican por su contenido.
La posición de un fondo se edita por separado de sus controles y textos: mover el fondo no traslada automáticamente
los contenidos. Los recursos del juego —skins, fuentes, imágenes y sprites— se suministran mediante resource packs.

El inspector incluye colores y fondos, estados normal/hover/desactivado de botones, bordes, fuentes, texturas,
columnas, HUD social y party, notificaciones, colores de vida, y formato y posición del TEAM en el nametag.
Para cambiar textos e iconos de glifos, incluye traducciones o fuentes en el pack.

## Compartir el diseño y sus recursos

**Exportar** escribe:

- `config/socialmod/presets/series.json`: preset visual versionado.
- `config/socialmod/presets/series.zip`: resource pack con el preset, tema y recursos añadidos.

Coloca tus fuentes, texturas, iconos y traducciones dentro de `config/socialmod/presets/assets/`, siguiendo las rutas
de un resource pack de Minecraft. Para sprites que cambian de tamaño, usa metadatos de escalado nine-slice.
Los campos de recursos aceptan identificadores como `miserie:gui/panel`, no rutas del disco.

Para importar, coloca el pack recibido como `config/socialmod/presets/import.zip` y pulsa **Importar**.
Si ese ZIP no existe, se usa `series.json`. El ZIP recupera también los recursos dentro de `presets/assets/`.
La importación no publica automáticamente ni activa los recursos: instala el pack en Minecraft para previsualizarlos.

Para distribuirlo como obligatorio:

1. Exporta el ZIP y súbelo a una URL HTTPS accesible para los jugadores.
2. Obtén su SHA-1, por ejemplo con PowerShell: `Get-FileHash -Algorithm SHA1 -LiteralPath .\series.zip`.
3. En el editor, introduce primero `resourcePackSha1` y después `resourcePackUrl`.
4. Publica. El servidor solicita el pack como obligatorio mediante el mecanismo de Minecraft, también al conectar.
5. Tras cambiar recursos, vuelve a exportar y subir el pack y publica el nuevo hash. Comprueba el pack en cada versión
   de Minecraft que utilice tu serie: los metadatos exportados corresponden a la versión desde la que exportaste.

El tema JSON existente sigue siendo compatible. Un preset o tema inválido no sustituye al último válido.
Puedes empezar con [el ejemplo](examples/series-visual.json) y añadir tus recursos.

Creado por **TakumiStudios**.

## Mejoras del editor

El inspector agrupa Ventana, Colores, Textos, HUD, Nametag, Recursos y Elemento seleccionado. Plantilla abre el selector con Compacta, Lateral, Pantalla completa y Navidad: Refugio de invierno. Elegir textura muestra miniaturas de los sprites disponibles en los paquetes cargados.

La plantilla navideña incluye texturas pixel art y fuente propia; las guías ilustradas están en [español](series-es.md) y [English](series-en.md).

Arrastrar un fondo de panel mueve los controles y textos contenidos con el mismo desplazamiento. El movimiento se ajusta a pasos de cuatro píxeles; durante el arrastre aparece una cuadrícula. El redimensionado sigue siendo individual.

Publicar comprueba los controles modificados, textos recortados y texturas ausentes. Restaurar recupera el diseño publicado anteriormente, previa confirmación. El servidor conserva `visual.previous.json` y hasta veinte copias en `config/socialmod/visual-history/`.

La selección TEAM muestra el equipo actual; las confirmaciones indican acción, destino y valor. Los controles administrativos se encuentran en Gestionar.

## Integración con FancyMenu y SpiffyHUD (0.4.0)

Con FancyMenu instalado, Editor visual abre Personalización avanzada. Los ajustes básicos siguen accesibles desde esa ventana. Los layouts, las filas y la apariencia local se distribuyen con el modpack del cliente; Publicar en el editor básico sigue publicando solamente el preset básico del servidor. Mientras exista una apariencia local avanzada, esta tiene prioridad sobre el preset básico, excepto durante una vista previa del editor. Restablecer template local devuelve la apariencia al servidor.

Los bloques de conversaciones, chat y jugadores son controles identificables por FancyMenu. Sus límites finales se utilizan para dibujar e interactuar. SpiffyHUD incorpora los componentes de estado, party, pings y avisos; cada componente visible sustituye el HUD nativo equivalente. Las guías de Navidad describen los identificadores y placeholders disponibles, con capturas reales en ambos idiomas.
