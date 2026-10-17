# Verificación de la entrega

Entorno: Windows 11, JDK 27, JavaFX 27, Maven 3.9.16. Fecha: 5 de octubre de 2026.

## Verificaciones realizadas

- Compilación de todas las clases de producción y pruebas.
- `mvn package`: compilación, suite de integración y generación de JAR.
- 39 comprobaciones de integración sobre H2 aislado en memoria: autenticación, permisos, borradores privados, documentos, doble operación, filtros por moneda, solicitudes, rechazo, historial, preferencias, convenios, unidades, cierres, reactivación, rollback y desactivación de sesiones.
- Renderizado de 19 vistas JavaFX sobre escenas reales: catálogo, ingreso, registro, paneles de los tres roles, tablas administrativas y formulario de propiedad.
- Inspección visual de capturas y ajuste del espacio de filtros y estilos de encabezados.

El detalle de las 39 comprobaciones está en `src/test/java/ar/edu/nuevohogar/IntegrationChecks.java`; se puede repetir con `mvn test`. Las pruebas fallan con una excepción si se incumple una regla y Maven informa error de compilación/pruebas.

## No verificado en este entorno

- Importación y ejecución sobre un servidor real MySQL o MariaDB. Los resultados H2 no prueban por sí solos la compatibilidad completa del motor real.
- Prueba con lectores de pantalla, usuarios reales o un conjunto de datos grande.
- Ejecución simultánea de varios puestos con una carpeta de archivos compartida.
- Visualización en visores externos de cada formato de video/documento.

Las capturas en `docs/capturas/` muestran la aplicación ejecutando datos ficticios, no los bocetos originales ni cifras de una base real.
