# App de escritorio en varias computadoras

El uso previsto es instalar la app JavaFX en cada computadora de la empresa y que todas compartan los datos del servidor. La app se conecta a MySQL y el endpoint HTTPS administra las cargas y descargas de multimedia. Esta configuración no modifica tablas ni datos.

## Variables disponibles

| Variable | Uso | Ejemplo |
| --- | --- | --- |
| `DB_URL` | JDBC URL de MySQL/MariaDB | `jdbc:mysql://SERVIDOR:3306/inmobiliaria?sslMode=REQUIRED` |
| `DB_USER` | Usuario exclusivo de la app | `nuevohogar_app` |
| `DB_PASSWORD` | Contraseña de ese usuario | Configurar en el servidor, no en Git |
| `STORAGE_PATH` | Carpeta persistente para documentos privados | `//SERVIDOR/NuevoHogarPrivado` |
| `MEDIA_WEB_ROOT` | Ruta física compartida de la web para multimedia pública | `//SERVIDOR/Inmobiliaria` |
| `MEDIA_WEB_PREFIX` | Prefijo URL público para multimedia | `/Inmobiliaria/` |
| `MEDIA_API_URL` | Endpoint HTTPS para adjuntar y descargar archivos privados | `https://imbnuevohogar.cavernservices.org/Inmobiliaria/api/media.php` |
| `MEDIA_WEB_URL` | Dominio público para mostrar imágenes y videos | `https://imbnuevohogar.cavernservices.org` |

También se pueden guardar estas opciones en `config.properties`, copiando `config.example.properties`. No subir ese archivo con credenciales reales.

## Multimedia

En el hosting, creá `Inmobiliaria/api/` y subí allí `server-api/media.php`. Copiá `server-api/privados.htaccess` como `Inmobiliaria/sistema/privados/.htaccess` para bloquear el acceso web directo a documentos. En cada PC, configurá `media.api.url` con la URL indicada y `media.web.url=https://imbnuevohogar.cavernservices.org`. El endpoint guarda fotos/videos en `Inmobiliaria/archivos/propiedades` o `Inmobiliaria/archivos/solicitudes`; los planos y escrituras se guardan bajo `Inmobiliaria/sistema/privados` y se entregan solo después de validar la sesión y los permisos del usuario. El prefijo guardado en la base queda `/Inmobiliaria/`, por lo que coincide con el sitio publicado.

`MEDIA_WEB_ROOT` solo sirve si Java puede acceder directamente a esa ruta física, como una carpeta compartida en la misma LAN. `C:/xampp/htdocs/Inmobiliaria` apunta a la PC local y no sirve para las otras computadoras ni para el hosting.

`STORAGE_PATH` contiene archivos privados. Debe ser persistente, tener permisos de lectura/escritura para la app y quedar fuera de la carpeta pública de la web.

Para que todas las PCs compartan multimedia, en una red local se pueden publicar dos carpetas de red: la raíz pública de archivos que Apache sirve como `/Inmobiliaria/` y otra carpeta privada para documentos. Todas las PCs deben tener permiso de lectura/escritura y usar la misma ruta UNC, por ejemplo `//SERVIDOR/Inmobiliaria` y `//SERVIDOR/NuevoHogarPrivado`. La cuenta con la que se ejecuta la app debe tener acceso a esos recursos. Una ruta local como `C:/xampp/htdocs/Inmobiliaria` solo apunta al disco de esa PC; no transfiere archivos desde otras computadoras.

La app sigue conectándose directamente a MySQL, así que el hosting debe permitir el acceso de esas PCs al puerto 3306 con TLS. Para mayor seguridad, restringí ese acceso a IPs autorizadas; el endpoint HTTP gestiona solo la multimedia.

## Antes de publicar

1. Configurar una base de prueba y verificar que ya tenga el esquema compatible. No ejecutar scripts SQL sobre producción sin respaldo y autorización.
2. Crear un usuario de base exclusivo para la app, con permisos mínimos; permitir el puerto 3306 solo desde la red de la empresa y exigir TLS si la conexión sale de la LAN.
3. Subir `server-api/media.php` a `Inmobiliaria/api/media.php` y configurar `media.api.url` y `media.web.url` en cada PC.
4. Probar carga de una imagen, apertura de un PDF privado y acceso denegado desde un usuario sin permiso.
5. Respaldar juntos la base y los archivos, y probar la restauración.

## Ejemplo por computadora en una red Windows

Copiá `config.example.properties` como `config.properties` junto al lanzador en cada PC y reemplazá los nombres de ejemplo por los del servidor:

```properties
db.url=jdbc:mysql://SERVIDOR:3306/inmobiliaria?sslMode=REQUIRED
db.user=USUARIO_APP
db.password=CLAVE_DEL_USUARIO_APP
storage.path=data/archivos
media.api.url=https://imbnuevohogar.cavernservices.org/Inmobiliaria/api/media.php
media.web.url=https://imbnuevohogar.cavernservices.org
```

`storage.path` queda como almacenamiento local de trabajo; las cargas nuevas pasan por el endpoint. No agregues `media.web.root` en el despliegue multi-PC. No uses `root` ni expongas el puerto MySQL a toda Internet.

Cada PC debe tener instalado un runtime compatible con la app (Java 25 para el paquete actual), o se debe distribuir un instalador que lo incluya. Las credenciales en `config.properties` pueden ser leídas por usuarios locales con acceso a esa PC; una API evita distribuir la contraseña de MySQL.
