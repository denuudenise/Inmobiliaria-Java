# Preparación de MySQL / MariaDB

El archivo `01_original.sql` conserva el dump suministrado, generado por MariaDB 10.4.32. `02_migracion.sql` agrega las estructuras necesarias. No crea una BDD ni el usuario SQL de conexión.

## Base nueva

Desde MySQL Workbench, el cliente de MySQL/MariaDB o phpMyAdmin, creá una base con UTF-8:

```sql
CREATE DATABASE inmobiliaria CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE inmobiliaria;
```

Luego importá, en orden:

1. `01_original.sql`.
2. `02_migracion.sql`.
3. `05_modulos_comerciales.sql` (visitas, tareas y trazabilidad de precios/asignaciones).

En phpMyAdmin seleccioná primero la base y usá **Importar** para cada archivo. En el cliente SQL de línea de comandos se puede usar `SOURCE ruta/al/archivo.sql`.

## Base que ya está en uso

No vuelvas a importar `01_original.sql` ni `02_migracion.sql` sobre la base existente. Seleccioná `inmobiliaria` en phpMyAdmin e importá solamente `05_modulos_comerciales.sql`. Es una ampliación aditiva de la versión 2; no reemplaza tus usuarios ni propiedades. La aplicación usa esas tablas nuevas para la agenda de visitas, recordatorios y el historial comercial.

## Si ya existe una base

Realizá un respaldo y aplicá `02_migracion.sql` en una copia que tenga exactamente el esquema del dump. La migración **no es idempotente**: no se debe ejecutar dos veces. Su última instrucción registra la versión 2 en `schema_version`.

El DDL de MySQL/MariaDB realiza commits implícitos. Si hay un fallo a mitad de la migración, no supongas que un ROLLBACK deshizo las alteraciones: restaurá la copia antes de reintentar o revisá explícitamente los pasos aplicados.

Comprobaciones posteriores:

```sql
SELECT * FROM schema_version; -- debe mostrar version = 2
SELECT COUNT(*) FROM propiedad_ofertas; -- copia de las ofertas originales
SELECT COUNT(*) FROM propiedades WHERE id_propietario IS NULL;
SELECT COUNT(*) FROM clientes;
```

Las propiedades preexistentes no tienen propietario en el dump. La migración deja `id_propietario` en NULL para no inventarlo; un agente debe asignar el propietario correcto al editar la ficha. Se conservan los creadores, precios, estados y operaciones originales. Los clientes ya existentes se incorporan a `clientes` usando su fecha de registro.

El dump que recibimos no contiene usuarios ni propiedades de negocio; solo tablas de catálogo. No hay cuentas reales ni contraseñas para convertir. Si aplicás estos scripts a otra base con usuarios cuyos hashes utilicen otro algoritmo, necesitarás un proceso de restablecimiento compatible: la aplicación verifica PBKDF2, no interpreta hashes heredados automáticamente.

## Datos ficticios para practicar

Después de aplicar la migración, podés importar `03_datos_demo.sql` desde phpMyAdmin para agregar un administrador demo, un agente, dos clientes y propiedades de muestra para venta y alquiler. El script puede ejecutarse de nuevo sin duplicar los datos de demostración. Al iniciar sesión, la aplicación dirige al usuario al panel según el rol guardado en `usuarios.id_rol`.

Las cuentas de muestra usan la contraseña `DemoHogar2026!`:

| Rol | Correo |
| --- | --- |
| Administrador | `admin.demo@nuevohogar.local` |
| Agente | `agente.demo@nuevohogar.local` |
| Cliente | `cliente.demo@nuevohogar.local` |
| Cliente | `cliente2.demo@nuevohogar.local` |

La contraseña para todas las cuentas es `DemoHogar2026!`. Son credenciales de demostración local. No las uses en un sistema publicado.

## Credenciales de conexión

Creá un usuario de aplicación mediante la herramienta con la que administrás tu servidor y concedé únicamente SELECT, INSERT, UPDATE y DELETE sobre `inmobiliaria.*`. El usuario que aplica las migraciones necesita permisos DDL; la aplicación normal no.

Copiá `config.example.properties` a `config.properties` y completá:

```properties
db.url=jdbc:mariadb://localhost:3306/inmobiliaria
db.user=inmobiliaria_app
db.password=tu_clave_local
storage.path=data/archivos
```

No guardes el archivo con credenciales en Git. Para una ruta de Windows dentro de un `.properties`, usá barras `/` o escapá cada barra inversa: `storage.path=C:/datos/nuevo-hogar/archivos`.

## Qué cambia y por qué

| Cambio | Requisito resuelto |
|---|---|
| `usuarios`: dirección, localidad, nacimiento, género y tipo documental | Registro y perfil de los bocetos |
| `clientes` y `roles_cliente` | Ficha única y roles comerciales múltiples, distintos del permiso de acceso |
| `propiedades.id_propietario` y creador agente opcional | El cliente puede proponer su propio inmueble |
| `edificios`, edificio y número de unidad en propiedades | Edificios/complejos con unidades individuales y sin números duplicados |
| `capacidad` y tipo Depósito | Filtros y tipos mencionados en el informe |
| `propiedad_ofertas` | Venta y alquiler simultáneos, con importes y monedas separados |
| `convenios` | Acuerdos con propietarios y profesionales |
| `documentos` | Escrituras, planos, comprobantes e impuestos privados |
| `publicaciones` | Solicitudes y revisiones históricas; una edición invalida la solicitud anterior |
| `contactos` e `intereses` | Historial centralizado y vínculo entre cliente y propiedad |
| Moneda de preferencia y `preferencias_caracteristicas` | Búsquedas sin mezclar ARS/USD y características deseadas |
| `historial_estados` | Cambios del estado comercial con autor, motivo y fecha |
| Cierres: índice no único y operación/moneda | Alquilar nuevamente o vender después sin borrar el cierre anterior |
| `notificaciones` y `auditoria` | Seguimiento interno y trazabilidad |

Los campos originales `propiedades.id_tipo_transaccion`, `precio` y `moneda` se mantienen por compatibilidad y reflejan la primera oferta. Las consultas nuevas usan `propiedad_ofertas` como fuente de los precios por operación.

`id_estado_verificacion` es el estado actual de moderación. `publicaciones` conserva cada solicitud. El estado comercial se gestiona aparte. No se agregó otro estado comercial "En venta": venta/alquiler son operaciones, no disponibilidad.

La migración `05_modulos_comerciales.sql` agrega el soporte de visitas, recordatorios, ofertas y reservas, historial de precios/asignaciones, contactos alternativos, checklist documental, umbrales de alertas y auditoría detallada. La aplicación usa esas tablas en los paneles de administración, agente y cliente.

### Límite de validación

Los scripts se prepararon para MySQL 8 / MariaDB 10.4 o posterior, tomando como base el dump entregado. La suite funcional ejecutada utiliza H2 en modo MySQL. En este equipo no se dispuso de un servidor MySQL/MariaDB para ejecutar la migración real; debe probarse primero en una copia de tu base.
