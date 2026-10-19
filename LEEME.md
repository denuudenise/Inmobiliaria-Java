# Inmobiliaria Nuevo Hogar

## Abrir y ejecutar

1. En IntelliJ, abrí la carpeta `C:\Inmobiliaria\Inmobiliaria Nuevo Hogar` como proyecto Maven.
2. Recargá Maven y seleccioná JDK 25 como SDK del proyecto y del ejecutor Maven.
3. Iniciá MySQL desde XAMPP.
4. Ejecutá la configuración **Nuevo Hogar** o la clase `org.example.Main`. Ambas inician la aplicación completa del ZIP de tu compañero.

La aplicación ahora está en `src/main/java/ar/edu/nuevohogar`. Conservé el `Main.java` original como lanzador para que no tengas que cambiar tu forma de ejecutarlo.

## Conectar la base que ya importaste

Dejé `config.properties` configurado para `inmobiliaria` en `localhost:3306`, usuario `root` y contraseña vacía, que es la configuración local habitual de XAMPP. Si tu MySQL tiene otra contraseña o puerto, corregí ese archivo.

La base que ya usás está en la versión 2. **No vuelvas a importar `01_original.sql` ni `02_migracion.sql`.** Para habilitar agenda de visitas, recordatorios e historial comercial, exportá una copia desde phpMyAdmin y luego importá solamente `database/05_modulos_comerciales.sql` con `inmobiliaria` seleccionada. La migración no se ejecuta sola desde Java.

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

- `src/main/java/ar/edu/nuevohogar/interfaz`: ventanas JavaFX.
- `src/main/java/ar/edu/nuevohogar/interfaz/autenticacion`: formularios de ingreso y registro.
- `src/main/java/ar/edu/nuevohogar/interfaz/componentes`: controles de formulario reutilizables.
- `src/main/java/ar/edu/nuevohogar/interfaz/paneles/administrador`, `/agente` y `/cliente`: navegación específica de cada rol.
- `src/main/java/ar/edu/nuevohogar/backend`: reglas de negocio y coordinación.
- `src/main/java/ar/edu/nuevohogar/backend/autenticacion`: verificación y hash de contraseñas.
- `src/main/java/ar/edu/nuevohogar/persistencia/dao`: acceso SQL separado por responsabilidad.
- `src/main/java/ar/edu/nuevohogar/modelo`: entidades y resultados de consultas.
- `src/main/resources`: tema visual y esquema de demostración H2.
- `database/02_migracion.sql`: migración inicial para una base versión 1.
- `database/05_modulos_comerciales.sql`: visitas, tareas de seguimiento e historiales para una base versión 2.
- `docs/capturas`: vistas de ejemplo de los paneles.

El menú de cada rol está en su propia carpeta de `interfaz/paneles`. En la aplicación se agregaron agenda de visitas, tareas para agentes, ofertas/reservas con autorización administrativa, checklist documental, contactos alternativos, umbrales y generación de alertas, e historiales de precios y asignaciones.

No ejecuté ninguna migración contra tu servidor MySQL. Para poder usar las visitas y los recordatorios, respaldá la base e importá `05_modulos_comerciales.sql` desde phpMyAdmin.
