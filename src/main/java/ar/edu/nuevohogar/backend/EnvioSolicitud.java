package ar.edu.nuevohogar.backend;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.IntSupplier;

/** Conserva el identificador y los pasos pendientes si falla un adjunto del formulario. */
public final class EnvioSolicitud {
    @FunctionalInterface
    public interface Paso { void ejecutar(int solicitud) throws Exception; }

    private Integer solicitud;
    private final Queue<Paso> pendientes = new ArrayDeque<>();

    public synchronized boolean creada() { return solicitud != null; }

    public synchronized int enviar(IntSupplier crear, List<Paso> pasos) throws Exception {
        if (solicitud == null) {
            solicitud = crear.getAsInt();
            pendientes.addAll(pasos);
        }
        while (!pendientes.isEmpty()) {
            pendientes.element().ejecutar(solicitud);
            pendientes.remove();
        }
        return solicitud;
    }
}
