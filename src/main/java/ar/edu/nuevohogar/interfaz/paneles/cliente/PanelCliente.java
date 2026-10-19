package ar.edu.nuevohogar.interfaz.paneles.cliente;

import ar.edu.nuevohogar.interfaz.paneles.ElementoMenuPanel;
import java.util.List;

/** Menú de propiedades y gestiones del cliente. */
public final class PanelCliente {
    private PanelCliente() {}
    public static List<ElementoMenuPanel> menu(Runnable... a) {
        return List.of(ElementoMenuPanel.opcion("Inicio", a[0], true),
                ElementoMenuPanel.opcion("Propiedades", a[1], false),
                ElementoMenuPanel.opcion("Solicitudes de carga", a[2], false),
                ElementoMenuPanel.opcion("Mis visitas", a[3], false),
                ElementoMenuPanel.opcion("Ofertas y reservas", a[4], false),
                ElementoMenuPanel.opcion("Mis intereses", a[5], false),
                ElementoMenuPanel.opcion("Mi búsqueda", a[6], false),
                ElementoMenuPanel.opcion("Notificaciones", a[7], false),
                ElementoMenuPanel.opcion("Vender una propiedad", a[8], false));
    }
}
