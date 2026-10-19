package ar.edu.nuevohogar.interfaz.paneles;

/** Opción de navegación de un panel; una acción nula representa un título de sección. */
public record ElementoMenuPanel(String nombre, Runnable accion, boolean activo, boolean seccion) {
    public static ElementoMenuPanel opcion(String nombre, Runnable accion, boolean activo) {
        return new ElementoMenuPanel(nombre, accion, activo, false);
    }
    public static ElementoMenuPanel seccion(String nombre) {
        return new ElementoMenuPanel(nombre, null, false, true);
    }
}
