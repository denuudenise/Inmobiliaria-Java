# Servicio compartido de documentos privados

La app de escritorio no debe contener el API Secret de Cloudinary. Para que administración y agentes abran los mismos planos, escrituras y comprobantes desde cualquier lugar, desplegá `ar.edu.nuevohogar.servidor.ServidorDocumentosPrivados` en el alojamiento HTTPS que ya tenés.

## Requisitos de despliegue

El alojamiento debe poder ejecutar Java 25 y conectarse a **la misma base MariaDB central** que usa la app. Configurá estas variables en el panel del proveedor, sin incluir sus valores en Git:

| Variable | Uso |
| --- | --- |
| `PORT` | Puerto que asigna el proveedor; si no lo da, usa `8080`. |
| `DB_URL` | URL JDBC del MySQL central, por ejemplo `jdbc:mysql://HOST:3306/BASE?sslMode=REQUIRED`. Para MariaDB usá `jdbc:mariadb://HOST:3306/BASE`. |
| `DB_USER` | Usuario de base con permisos necesarios para leer usuarios/propiedades y crear filas en `documentos` y `auditoria`. |
| `DB_PASSWORD` | Contraseña de ese usuario. |
| `CLOUDINARY_URL` | URL de conexión con cloud name, API Key y API Secret copiada desde Cloudinary. Solo en el entorno secreto del servidor. |

La MariaDB actual configurada como `localhost:3306` **no sirve desde un servidor remoto**. Hay que alojar/restaurar la base en un servidor accesible por el backend y las instalaciones de escritorio, o dar al backend una VPN privada hacia la base. No abras MariaDB públicamente sin controles de red.

## Compilación y comando de inicio

En el proveedor, usá Java 25 y Maven. Comando de compilación:

```text
mvn -DskipTests package dependency:copy-dependencies -DincludeScope=runtime
```

Comando de inicio en Linux:

```text
java --add-modules jdk.httpserver -cp "target/classes:target/dependency/*" ar.edu.nuevohogar.servidor.ServidorDocumentosPrivados
```

En Windows, el separador del classpath es `;` en lugar de `:`. El proveedor debe publicar el proceso bajo una URL **HTTPS**. El backend genera enlaces Cloudinary autenticados de cinco minutos y comprueba la sesión y relación con la propiedad antes de entregar cada enlace.

## Configuración de cada copia de escritorio

En el `config.properties` local de cada computadora agregá la URL pública HTTPS del backend:

```properties
private_media.api_url=https://TU-SERVIDOR.example.com
```

Las credenciales `db.url`, `db.user` y `db.password` de cada copia también deben conectarse a la base central compartida. El login usa la misma cuenta de la app para obtener una sesión del backend. Los documentos que se suban de ahí en adelante se guardan en Cloudinary como recursos autenticados; los documentos antiguos que tengan una ruta local no se migran solos.

## Rutas del servicio

- `POST /api/sesion`: verifica la cuenta y entrega un token temporal.
- `POST /api/sesion/cerrar`: revoca el token local del servidor.
- `POST /api/documentos`: sube un PDF/JPG/PNG privado, limitado a 50 MB.
- `GET /api/documentos/{id}/url?propiedad={id}`: verifica permisos y entrega URL de cinco minutos.

El preset unsigned `inmuebles_unsigned` sigue usándose solamente para multimedia pública del catálogo. No lo uses para documentos privados.
