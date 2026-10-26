package ar.edu.nuevohogar.persistencia.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Traduce rutas públicas de la web sin permitir acceso fuera de las carpetas configuradas. */
public final class RutasAdjuntos {
    private RutasAdjuntos() {}

    public static Path resolve(Path storage, Path webRoot, String webPrefix,
            String value, boolean privateDocument) {
        if (value == null || value.isBlank()) throw invalid();
        Path root = storage.toAbsolutePath().normalize();
        String relative = value.replace('\\', '/');
        if (!privateDocument && webRoot != null && relative.startsWith(webPrefix)) {
            root = webRoot.toAbsolutePath().normalize();
            relative = relative.substring(webPrefix.length());
        }
        // Las URL remotas, rutas absolutas y unidades de Windows no son rutas de adjuntos locales.
        if (relative.startsWith("/") || relative.contains(":")) throw invalid();
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw invalid();
        try {
            if (Files.exists(path) && !path.toRealPath().startsWith(root.toRealPath())) throw invalid();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo acceder al archivo adjunto.", e);
        }
        return path;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Ruta de archivo inválida.");
    }
}
