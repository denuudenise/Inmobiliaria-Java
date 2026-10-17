# Inmobiliaria Nuevo Hogar

Aplicación de escritorio para el proyecto de Laboratorio de Programación. Basada en el informe proporcionado, el dump `inmobiliaria.sql` y los cuatro bocetos de interfaces. Implementa Java + JavaFX + JDBC/DAO, con MySQL/MariaDB para uso real y H2 persistente para demostración.

## Abrir en IntelliJ IDEA

1. Abrí **File → Open** y seleccioná esta carpeta o `pom.xml`. Importá como proyecto Maven.
2. Seleccioná **JDK 27** como SDK del proyecto y como JDK del ejecutor de Maven. Esta entrega se comprobó con el JDK 27 que ya está instalado en tu equipo; el código compila con `release 25` y usa JavaFX 27.
3. Esperá que Maven descargue las dependencias. No hace falta instalar un SDK de JavaFX por separado.
4. Ejecutá la configuración **Nuevo Hogar** incluida en `.run/`. Como alternativa, desde la ventana Maven ejecutá `javafx:run`.
5. Si utilizás la terminal con Maven en PATH: `mvn javafx:run`.

El directorio de trabajo debe ser la carpeta que contiene `pom.xml`. No ejecutes el JAR solo con doble clic: requiere las dependencias de JavaFX y JDBC. Maven prepara esa configuración.

La integración de JavaFX con Maven sigue la [documentación oficial de OpenJFX](https://openjfx.io/openjfx-docs/#maven).

## Probar sin configurar MySQL

Sin `config.properties`, se crea una base H2 **local y persistente** en `data/nuevo-hogar.mv.db`. Se cargan seis propiedades ficticias y tres cuentas:

| Perfil | Correo | Contraseña |
|---|---|---|
| Administrador | admin@nuevohogar.demo | DemoHogar2026! |
| Agente | agente@nuevohogar.demo | DemoHogar2026! |
| Cliente | cliente@nuevohogar.demo | DemoHogar2026! |

La carga inicial solo ocurre si la base está vacía. Las modificaciones se conservan al cerrar la aplicación. Estos accesos no se crean en una base MySQL. Para reiniciar la demo, cerrá la aplicación y mové la carpeta `data` a una copia de respaldo; la siguiente ejecución generará datos nuevos.

## Conectar tu base de datos

Consultá [database/README.md](database/README.md) antes de importar.

1. Si empezás con una base vacía, importá `database/01_original.sql` y luego `database/02_migracion.sql`.
2. Si ya importaste exactamente el dump proporcionado, aplicá **solo** `02_migracion.sql`, una vez y sobre una copia de respaldo.
3. Copiá `config.example.properties` como `config.properties`.
4. Completá el servidor, usuario y contraseña de la BDD. El driver MariaDB incluido permite conectar con MySQL y MariaDB usando el prefijo `jdbc:mariadb:`.
5. Reiniciá la aplicación. Si la base no contiene administradores, aparecerá el formulario para crear el primero.

No se modificó tu archivo original de Descargas ni se importaron datos en una base existente. La aplicación no ejecuta automáticamente migraciones sobre MySQL.

## Recorrido sugerido para la presentación

1. Entrá como invitado: filtrá el catálogo y abrí una ficha.
2. Ingresá como cliente: creá una propiedad y guardala como **borrador**. Adjuntá una fotografía o un plano desde la ficha. Volvé a Propiedades y elegí **Enviar a revisión**.
3. Ingresá como administrador: abrí **Solicitudes de carga**, revisá la ficha y aprobá o rechazá con motivo. La propiedad aprobada aparece en el catálogo.
4. Como cliente, abrí una propiedad pública y enviá una consulta desde **Contactar / Me interesa**. La consulta queda registrada y genera una notificación interna para administración.
5. Como agente, abrí **Clientes**: revisá el historial, registrá una visita, actualizá los intereses y guardá preferencias para buscar coincidencias.
6. Registrá un convenio y un edificio. Al cargar una unidad, seleccioná el edificio y un número de unidad único.
7. En Propiedades, elegí una ficha aprobada y **Registrar cierre**. Se guarda la operación, la comisión y el nuevo estado comercial. Desde Reportes o Ventas y alquileres podés exportar CSV.
8. Reactivá la propiedad con un motivo: vuelve a borrador y requiere otra aprobación. Los cierres anteriores se mantienen.

## Funcionalidades incluidas

- Catálogo público: título/localidad/dirección, tipo, operación, precio con moneda, dormitorios, capacidad y características.
- Ficha con descripción, superficies, antigüedad, expensas, características y multimedia.
- Inicio de sesión, registro por pasos, perfil y cambio de contraseña.
- Paneles de cliente, agente y administrador; solicitudes con historial de revisiones.
- Alta y edición de propiedades, propietario, edificio/unidad, venta y alquiler simultáneos con precios separados.
- Archivos de imagen y video; planos, escrituras, comprobantes e impuestos privados.
- Ficha única de cliente, roles comerciales múltiples, agente asignado, contactos e intereses.
- Preferencias de búsqueda y comparación con propiedades publicadas.
- Convenios con propietarios y profesionales; edificios y unidades.
- Estados comerciales, cierres repetidos a lo largo de la vida del inmueble y comisiones.
- Notificaciones internas, auditoría, métricas reales y exportación CSV.
- Creación de usuarios por administración; activación/desactivación de cuentas.

## Estructura para estudiar el código

```text
src/main/java/ar/edu/nuevohogar/
  Main.java                  punto de entrada
  ui/AgencyApp.java           pantallas y navegación JavaFX
  ui/Form.java                formularios reutilizables
  service/AgencyService.java  reglas, validaciones y permisos
  service/Passwords.java      hash y verificación de contraseñas
  service/DemoData.java       datos ficticios del modo demostración
  dao/Database.java          conexiones y transacciones
  dao/JdbcDao.java           consultas parametrizadas
  model/Row.java             resultados de consultas
src/main/resources/
  theme.css                  colores y estilos de los bocetos
  db/demo.sql                esquema independiente H2
src/test/java/               pruebas de integración
database/                   dump original y migración MySQL/MariaDB
docs/                       decisiones, validación y modelo
```

La UI llama al servicio; el servicio valida permisos y reglas; el DAO ejecuta SQL con parámetros. Se utiliza `Row` como DTO genérico para reducir código repetitivo en tablas y formularios. El código está separado por capas, aunque las pantallas están agrupadas en un controlador de navegación y los casos de uso en un servicio central.

## Pruebas

Ejecutá la configuración **Pruebas de integración** o `mvn test`.

Las pruebas usan bases H2 aisladas en memoria y archivos dentro de `target/test-files`. No tocan la base configurada para la aplicación. El objetivo `exec-maven-plugin` ejecuta `IntegrationChecks` en la fase `test`; el resultado esperado es `PASS · 39 comprobaciones de integración`. No se usa JUnit en esta entrega.

`mvn package` compila, ejecuta esas pruebas y genera el JAR. Los resultados y límites de las verificaciones están en [docs/VALIDACION.md](docs/VALIDACION.md).

## Alcance y decisiones

- Se utilizó **JavaFX**, según tu confirmación, resolviendo la mención contradictoria a Qt al final del informe.
- Los bocetos web se adaptaron a una aplicación de escritorio: las secciones, el azul, el fondo cálido y las tarjetas se conservan. No es una reproducción exacta de cada píxel.
- Las viviendas dibujadas son ilustraciones de reemplazo, no fotos de inmuebles reales. Al adjuntar una fotografía, aparece en la tarjeta y la ficha.
- Google Sign-In, recuperación por correo, mapas y envío de emails/SMS no están implementados. Los contactos y las notificaciones funcionan dentro de la aplicación.
- Videos y documentos se abren con el visor del sistema. No se incorporó reproducción interna ni generación de subtítulos.
- Los archivos se copian a `storage.path`. En varios puestos, configurá una carpeta compartida privada accesible por todos; la BDD guarda nombres relativos, no el archivo binario. Las URLs del dump anterior no se descargan automáticamente.
- Arquitectura de escritorio con conexión directa a BDD, como pide el informe. Pensada para un entorno escolar o una red interna confiable. Para ofrecer un portal público por Internet se necesita una API que mantenga las credenciales y los archivos privados en el servidor.
- Las consultas y operaciones normales se ejecutan desde el hilo de la interfaz; la inicialización corre en segundo plano. Para bases grandes o conexiones remotas lentas conviene añadir tareas asíncronas y paginación.
- Las correcciones de perfiles propias y de propiedades están disponibles; las cuentas se desactivan en vez de borrar su historial. No hay edición retroactiva de cierres ni borrado de documentos en esta versión.

## Si algo no arranca

| Mensaje / síntoma | Qué revisar |
|---|---|
| `UnsupportedClassVersionError` | Seleccionar JDK 27 también en Maven Runner. |
| JavaFX no encontrado | Abrir como Maven, recargar dependencias y usar `javafx:run`. |
| Conexión rechazada | Servidor MySQL/MariaDB iniciado, puerto y usuario en `config.properties`. |
| No existe una tabla o columna | Aplicar el dump y la migración en el orden indicado. |
| La propiedad no aparece públicamente | Debe estar aprobada y disponible o reservada. |
| No permite adjuntar archivos | Guardar primero en borrador; luego cargar y enviar a revisión. |
| No encuentra un documento | Revisar `storage.path` y el acceso a la carpeta compartida. |

