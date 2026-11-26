package ar.edu.nuevohogar;

import ar.edu.nuevohogar.backend.EnvioSolicitud;
import ar.edu.nuevohogar.persistencia.dao.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ComprobacionesAuditoria {
    public static void run() throws Exception {
        var row = new ar.edu.nuevohogar.modelo.Registro();
        row.put("faltan_planos", Boolean.TRUE);
        row.put("falta_escritura", Boolean.FALSE);
        row.put("id_propiedad", 42L);
        if (row.id("faltan_planos") != 1 || row.id("falta_escritura") != 0 || row.id("id_propiedad") != 42)
            throw new AssertionError("Tipos JDBC incompatibles");
        System.out.println("OK auditoría: TINYINT booleano y claves numéricas compatibles");
        var envio = new EnvioSolicitud();
        var created = new AtomicInteger();
        var firstFile = new AtomicInteger();
        var attempts = new AtomicInteger();
        List<EnvioSolicitud.Paso> steps = List.of(
                id -> firstFile.incrementAndGet(),
                id -> { if (attempts.incrementAndGet() == 1) throw new java.io.IOException("corte de red de prueba"); });
        try { envio.enviar(created::incrementAndGet, steps); throw new AssertionError("Debía fallar el segundo archivo"); }
        catch (java.io.IOException expected) { }
        int id = envio.enviar(created::incrementAndGet, steps);
        if (id != 1 || created.get() != 1 || firstFile.get() != 1 || attempts.get() != 2)
            throw new AssertionError("Reintento duplicó solicitud o adjuntos");
        envio.enviar(created::incrementAndGet, steps);
        if (attempts.get() != 2) throw new AssertionError("Reenvío terminado duplicó adjuntos");
        System.out.println("OK auditoría: reintentos conservan solicitud y adjuntos exitosos");

        try (var db = new ConexionBaseDatos("jdbc:h2:mem:legacy_media;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", Files.createTempDirectory("nh-audit"));
                var c = db.open(); var s = c.createStatement()) {
            s.execute("CREATE TABLE solicitud_multimedia(id_multimedia INT,id_solicitud INT,url_archivo VARCHAR(100),tipo VARCHAR(20))");
            s.execute("INSERT INTO solicitud_multimedia VALUES(2,1,'dos','Video'),(1,1,'uno','Imagen')");
            var old = SolicitudPropiedadDAO.mediaForApproval(c, 1);
            if (!old.getFirst().text("url_archivo").equals("uno")) throw new AssertionError("Orden antiguo incorrecto");
            s.execute("ALTER TABLE solicitud_multimedia ADD orden INT DEFAULT 0");
            s.execute("UPDATE solicitud_multimedia SET orden=5 WHERE id_multimedia=1");
            var ordered = SolicitudPropiedadDAO.mediaForApproval(c, 1);
            if (!ordered.getFirst().text("url_archivo").equals("dos")) throw new AssertionError("No conserva orden nuevo");
        }
        System.out.println("OK auditoría: multimedia compatible con esquemas nuevo y anterior");
    }
}
