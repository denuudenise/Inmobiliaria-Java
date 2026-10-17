# Inmobiliaria Nuevo Hogar

## Abrir y ejecutar

1. En IntelliJ, abrí la carpeta `C:\Inmobiliaria\Inmobiliaria Nuevo Hogar` como proyecto Maven.
2. Recargá Maven y seleccioná JDK 25 como SDK del proyecto y del ejecutor Maven.
3. Iniciá MySQL desde XAMPP.
4. Ejecutá la configuración **Nuevo Hogar** o la clase `org.example.Main`. Ambas inician la aplicación completa del ZIP de tu compañero.

La aplicación ahora está en `src/main/java/ar/edu/nuevohogar`. Conservé el `Main.java` original como lanzador para que no tengas que cambiar tu forma de ejecutarlo.

## Conectar la base que ya importaste

Dejé `config.properties` configurado para `inmobiliaria` en `localhost:3306`, usuario `root` y contraseña vacía, que es la configuración local habitual de XAMPP. Si tu MySQL tiene otra contraseña o puerto, corregí ese archivo.

El proyecto necesita algunas tablas y columnas adicionales para los módulos nuevos. **No vuelvas a importar `01_original.sql`.** Antes de modificar tu base, exportá una copia desde phpMyAdmin. Después revisá `database/README.md` y aplicá `database/02_migracion.sql` una sola vez sobre la base existente. El script no se ejecuta solo desde Java. Si `schema_version` ya muestra la versión 2, no lo vuelvas a aplicar.

El acceso usa la cuenta que ya exista en `usuarios`; el rol `1` lleva al panel Administrador, `2` al panel Agente y `3` al panel Cliente. El código verifica tanto hashes PBKDF2 nuevos como hashes BCrypt existentes.

## Modo de demostración

Si querés abrir datos ficticios en vez de conectar MySQL, renombrá temporalmente `config.properties`. La app usará una base H2 local. Accesos de demostración:

| Perfil | Correo | Contraseña |
|---|---|---|
| Administrador | `admin@nuevohogar.demo` | `DemoHogar2026!` |
| Agente | `agente@nuevohogar.demo` | `DemoHogar2026!` |
| Cliente | `cliente@nuevohogar.demo` | `DemoHogar2026!` |

La base de demostración se guarda en `data/nuevo-hogar.mv.db` y no modifica MySQL.

## Código incorporado

- `src/main/java/ar/edu/nuevohogar`: aplicación, pantallas, servicio, modelos y DAO.
- `src/main/resources`: tema visual y esquema de demostración H2.
- `database/02_migracion.sql`: ampliación del esquema MySQL/MariaDB.
- `docs/capturas`: vistas de ejemplo de los paneles.

No ejecuté ninguna migración contra tu servidor MySQL. Primero hay que respaldar la base y aplicar el script desde phpMyAdmin.
