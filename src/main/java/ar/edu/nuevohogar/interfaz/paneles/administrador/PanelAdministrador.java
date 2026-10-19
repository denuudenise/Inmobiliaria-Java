package ar.edu.nuevohogar.interfaz.paneles.administrador;

import ar.edu.nuevohogar.interfaz.paneles.ElementoMenuPanel;
import java.util.List;

/** Menú principal del panel de administración. */
public final class PanelAdministrador {
    private PanelAdministrador() {}
    public static List<ElementoMenuPanel> menu(Runnable... a) {
        return List.of(
                ElementoMenuPanel.opcion("⌂|Inicio", a[0], true),
                ElementoMenuPanel.seccion("GESTIÓN"),
                ElementoMenuPanel.opcion("♙|Clientes", a[1], false),
                ElementoMenuPanel.opcion("⌂|Propiedades", a[2], false),
                ElementoMenuPanel.opcion("◷|Solicitudes de carga", a[3], false),
                ElementoMenuPanel.opcion("◷|Visitas", a[4], false),
                ElementoMenuPanel.opcion("▣|Ofertas y reservas", a[5], false),
                ElementoMenuPanel.opcion("✓|Seguimientos", a[6], false),
                ElementoMenuPanel.opcion("↔|Ventas y alquileres", a[7], false),
                ElementoMenuPanel.opcion("♙|Agentes", a[8], false),
                ElementoMenuPanel.opcion("♧|Usuarios", a[9], false),
                ElementoMenuPanel.seccion("SISTEMA"),
                ElementoMenuPanel.opcion("▥|Reportes", a[10], false),
                ElementoMenuPanel.opcion("♧|Notificaciones", a[11], false),
                ElementoMenuPanel.opcion("▤|Propietarios y convenios", a[12], false),
                ElementoMenuPanel.opcion("⌂|Edificios y unidades", a[13], false),
                ElementoMenuPanel.opcion("⚙|Configuración", a[14], false));
    }
}
