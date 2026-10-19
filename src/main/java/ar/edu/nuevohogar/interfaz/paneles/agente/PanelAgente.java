package ar.edu.nuevohogar.interfaz.paneles.agente;

import ar.edu.nuevohogar.interfaz.paneles.ElementoMenuPanel;
import java.util.List;

/** Menú de trabajo y cartera del agente. */
public final class PanelAgente {
    private PanelAgente() {}
    public static List<ElementoMenuPanel> menu(Runnable... a) {
        return List.of(ElementoMenuPanel.opcion("Inicio", a[0], true),
                ElementoMenuPanel.opcion("Propiedades", a[1], false),
                ElementoMenuPanel.opcion("Solicitudes de carga", a[2], false),
                ElementoMenuPanel.opcion("Visitas", a[3], false),
                ElementoMenuPanel.opcion("Ofertas y reservas", a[4], false),
                ElementoMenuPanel.opcion("Seguimientos", a[5], false),
                ElementoMenuPanel.opcion("Clientes", a[6], false),
                ElementoMenuPanel.opcion("Propietarios y convenios", a[7], false),
                ElementoMenuPanel.opcion("Edificios y unidades", a[8], false),
                ElementoMenuPanel.opcion("Ventas y alquileres", a[9], false),
                ElementoMenuPanel.opcion("Reportes", a[10], false),
                ElementoMenuPanel.opcion("Notificaciones", a[11], false));
    }
}
