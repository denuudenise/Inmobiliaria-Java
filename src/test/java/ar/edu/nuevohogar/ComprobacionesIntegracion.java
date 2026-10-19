package ar.edu.nuevohogar;

import static ar.edu.nuevohogar.persistencia.dao.ConsultasSQL.*;

import ar.edu.nuevohogar.persistencia.dao.*;
import ar.edu.nuevohogar.backend.*;
import ar.edu.nuevohogar.modelo.Registro;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/** Pruebas de integración ejecutables sin servidor externo ni framework adicional. */
public final class ComprobacionesIntegracion {
    private static int checks;

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        System.out.println("OK " + (++checks) + " · " + name);
    }

    private static void denied(Runnable action, String name) {
        boolean blocked = false;
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            blocked = true;
        }
        check(blocked, name);
    }

    private static Map<String, String> user(String dni, String email) {
        return Map.of("nombre", "Cliente", "apellido", "Prueba", "dni", dni, "email", email);
    }

    private static Map<String, String> property(int owner) {
        Map<String, String> d = new HashMap<>();
        d.putAll(
                Map.of(
                        "titulo",
                        "Casa de prueba",
                        "direccion",
                        "Calle 123",
                        "localidad",
                        "Olivos",
                        "descripcion",
                        "Descripción completa de prueba",
                        "id_tipo_propiedad",
                        "1",
                        "id_propietario",
                        "" + owner,
                        "superficie_total_m2",
                        "180",
                        "superficie_cubierta_m2",
                        "120",
                        "antiguedad_anos",
                        "4",
                        "ambientes",
                        "4"));
        d.putAll(Map.of("dormitorios", "3", "banos", "2", "capacidad", "5", "expensas", "0"));
        return d;
    }

    public static void main(String[] args) throws Exception {
        Path work = Path.of("target/test-files");
        Files.createDirectories(work);
        ConexionBaseDatos db =
                new ConexionBaseDatos(
                        "jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        work.resolve("uploads"));
        db.initialize();
        db.initialize();
        ServicioInmobiliaria admin = new ServicioInmobiliaria(db),
                agent = new ServicioInmobiliaria(db),
                client = new ServicioInmobiliaria(db),
                other = new ServicioInmobiliaria(db),
                guest = new ServicioInmobiliaria(db);
        String password = "ClavePrueba2026!";
        admin.bootstrapAdmin(user("1001", "admin@test.local"), password);
        admin.login("admin@test.local", password);
        check(admin.role() == 1, "Alta y acceso de administrador");
        denied(
                () -> admin.createStaffUser(user("1999", "segundo-admin@test.local"), password, 1),
                "No se permite crear un segundo administrador");
        int aid = admin.createStaffUser(user("1002", "agente@test.local"), password, 2);
        agent.login("agente@test.local", password);
        int cid = client.register(user("1003", "cliente@test.local"), password);
        client.login("cliente@test.local", password);
        int oid = other.register(user("1004", "otro@test.local"), password);
        other.login("otro@test.local", password);
        denied(() -> guest.allProperties(), "Invitado no puede listar fichas privadas");
        denied(
                () -> client.createStaffUser(user("999", "escalada@test.local"), password, 1),
                "Cliente no puede elevar privilegios");
        denied(
                () -> client.register(user("1003", "duplicado@test.local"), password),
                "Documento duplicado rechazado");
        denied(
                () -> guest.login("admin@test.local", "incorrecta"),
                "Contraseña incorrecta rechazada");
        check(
                !new ConsultasSQL(db)
                        .one("SELECT password_hash FROM usuarios WHERE id_usuario=?", cid)
                        .text("password_hash")
                        .contains(password),
                "Contraseña almacenada como hash");
        List<ServicioInmobiliaria.Offer> offers =
                List.of(
                        new ServicioInmobiliaria.Offer(1, new BigDecimal("250000"), "USD"),
                        new ServicioInmobiliaria.Offer(2, new BigDecimal("950000"), "ARS"));
        Map<String, String> d = property(oid);
        int pid = client.saveProperty(null, d, offers, Set.of(1, 2), false);
        check(
                client.property(pid).id("id_propietario") == cid,
                "Cliente no puede asignar otro propietario al crear");
        check(
                guest.properties(true, "", null, null, null, "USD", 0, 0, Set.of()).isEmpty(),
                "Borrador oculto del catálogo");
        denied(() -> other.property(pid), "Borrador privado para otros clientes");
        denied(
                () -> other.saveProperty(pid, property(oid), offers, Set.of(), false),
                "Edición ajena rechazada");
        Map<String, String> bad = property(cid);
        bad.put("superficie_cubierta_m2", "500");
        denied(
                () -> client.saveProperty(null, bad, offers, Set.of(), false),
                "Superficies inconsistentes rechazadas");
        check(client.offers(pid).size() == 2, "Venta y alquiler con precios independientes");
        Path doc = work.resolve("plano.pdf");
        Files.writeString(doc, "%PDF-1.4 test");
        client.attach(pid, doc, "Plano");
        check(client.attachments(pid, true).size() == 1, "Documento privado guardado");
        denied(() -> other.attachments(pid, true), "Documentación inaccesible para otro cliente");
        client.submit(pid);
        check(admin.checklistPublicacion(pid).size() == 3,
                "Enviar una propiedad crea el checklist de documentación obligatoria");
        int requisito = admin.checklistPublicacion(pid).getFirst().id("id_requisito");
        admin.actualizarChecklistPublicacion(pid, requisito, null, "Verificado", "Revisado por administración");
        check(admin.checklistPublicacion(pid).stream().anyMatch(r -> r.id("id_requisito") == requisito && r.text("estado").equals("Verificado")),
                "Administración registra la revisión documental");
        int req = admin.requests().getFirst().id("id_publicacion");
        denied(() -> agent.review(req, true, ""), "Agente no puede aprobar");
        denied(() -> admin.review(req, true, ""), "No se publica sin verificar requisitos documentales");
        denied(() -> admin.review(req, false, ""), "Rechazo exige motivo");
        admin.review(req, false, "Falta información");
        check(client.property(pid).id("id_estado_verificacion") == 4, "Rechazo registrado");
        client.submit(pid);
        int req2 = admin.requests().getFirst().id("id_publicacion");
        for (Registro requirementRow : admin.checklistPublicacion(pid))
            admin.actualizarChecklistPublicacion(pid, requirementRow.id("id_requisito"), null,
                    "Verificado", "Documentación revisada");
        admin.review(req2, true, "Revisada");
        var dashboard = new PanelAdministradorDAO(db).load();
        check(
                dashboard.clients() == 2
                        && dashboard.agents() == 1
                        && dashboard.totalProperties() == 1
                        && dashboard.requestStates().getOrDefault("Publicada", 0L) > 0,
                "Dashboard de administración resume datos reales");
        denied(
                () -> admin.review(req2, false, "Duplicado"),
                "Una solicitud no puede revisarse dos veces");
        check(
                guest.properties(
                                        true,
                                        "Olivos",
                                        1,
                                        2,
                                        new BigDecimal("960000"),
                                        "ARS",
                                        3,
                                        5,
                                        Set.of(1, 2))
                                .size()
                        == 1,
                "Catálogo filtra operación, precio, moneda y características");
        check(
                guest.properties(true, "", null, 2, new BigDecimal("960000"), "USD", 0, 0, Set.of())
                        .isEmpty(),
                "No se mezclan monedas en filtros");
        denied(
                () -> other.attachments(pid, true),
                "Documentos siguen privados con publicación aprobada");
        admin.saveClient(cid, Set.of(), aid);
        check(admin.historialAsignaciones(cid).size() == 1,
                "Las reasignaciones de clientes guardan agente anterior y nuevo");
        check(!new ConsultasSQL(db).list("SELECT * FROM auditoria_cambios WHERE entidad='clientes' AND id_registro=? AND campo='agente_asignado'", cid).isEmpty(),
                "La auditoría conserva el valor anterior y nuevo del agente");
        agent.guardarContactoAdicional(cid, "WhatsApp", "+54 11 5555-0101");
        check(agent.contactosAdicionales(cid).size() == 1,
                "El agente guarda teléfonos alternativos en la ficha de su cliente");
        admin.guardarConfiguracionAlertas(Map.of("dias_cliente_sin_contacto", "10",
                "dias_propiedad_sin_actividad", "25", "dias_aviso_reserva", "2"));
        check(admin.configuracionAlertas().stream().anyMatch(r -> r.text("clave").equals("dias_cliente_sin_contacto") && r.text("valor").equals("10")),
                "El administrador configura los umbrales de alertas");
        db.transaction(c -> {
            exec(c, "UPDATE usuarios SET fecha_registro=? WHERE id_usuario=?", java.time.LocalDateTime.now().minusDays(20), cid);
            return null;
        });
        check(admin.generarAlertasDeSeguimiento() > 0 && !agent.notifications().isEmpty(),
                "Los umbrales generan una alerta para el agente asignado");
        check(admin.generarAlertasDeSeguimiento() == 0,
                "El dashboard no duplica alertas que siguen sin leerse");
        int offerId = client.crearOferta(pid, 1, new BigDecimal("240000"), "USD",
                java.time.LocalDate.now().plusDays(7), "Oferta de prueba");
        check(client.ofertasComerciales().stream().anyMatch(r -> r.id("id_oferta") == offerId),
                "El cliente envía y consulta una oferta");
        agent.revisarOferta(offerId, true, "Propuesta aceptada para avanzar");
        check(client.ofertasComerciales().stream().anyMatch(r -> r.id("id_oferta") == offerId && r.text("estado").equals("Aceptada")),
                "El agente asignado responde la oferta y notifica al cliente");
        int reservationId = agent.solicitarReserva(offerId, LocalDateTime.now().plusDays(3), "Pendiente de autorización");
        check(admin.reservasComerciales().stream().anyMatch(r -> r.id("id_reserva") == reservationId && r.text("estado").equals("Pendiente")),
                "La reserva llega al panel de administración para su autorización");
        admin.resolverReserva(reservationId, true);
        check(client.reservasComerciales().stream().anyMatch(r -> r.id("id_reserva") == reservationId && r.text("estado").equals("Aprobada"))
                        && admin.property(pid).id("id_estado_comercial") == 2,
                "La aprobación administrativa reserva la propiedad y se registra en el historial");
        client.solicitarVisita(pid, LocalDateTime.now().plusDays(2), "Prefiero el horario de la tarde");
        check(client.visitas().size() == 1 && agent.visitas().size() == 1,
                "Solicitud de visita visible para cliente y agente asignado");
        Registro visit = agent.visitas().getFirst();
        agent.actualizarVisita(visit.id("id_visita"), "Realizada", "Visita concretada");
        check(client.visitas().getFirst().text("estado").equals("Realizada"),
                "El cliente ve el resultado de la visita");
        int task = agent.crearTareaSeguimiento(cid, pid, "Enviar documentación", "Compartir planos",
                LocalDateTime.now().plusDays(1));
        check(agent.tareasSeguimiento().stream().anyMatch(r -> r.id("id_tarea") == task),
                "El agente registra un recordatorio para su cartera");
        admin.completarTarea(task);
        check(admin.tareasSeguimiento().stream().anyMatch(r -> r.id("id_tarea") == task && r.text("estado").equals("Completada")),
                "El administrador puede supervisar y completar recordatorios");
        client.interest(cid, pid, "Interesado", "Quiero una visita");
        agent.contact(cid, pid, "Visita", "Recorrido por la propiedad", "Continuar negociación");
        check(
                admin.contacts(cid).size() == 1 && client.interests(cid).size() == 1,
                "Contactos e intereses centralizados");
        agent.saveClient(cid, Set.of("Comprador", "Propietario"), aid);
        check(admin.clientRoles(cid).size() == 2, "Roles comerciales múltiples");
        Map<String, String> pref =
                Map.of(
                        "id_tipo_transaccion",
                        "1",
                        "id_tipo_propiedad",
                        "1",
                        "presupuesto_maximo",
                        "300000",
                        "moneda",
                        "USD",
                        "zona_interes",
                        "Olivos",
                        "ambientes_minimos",
                        "3",
                        "dormitorios_minimos",
                        "2",
                        "superficie_minima_m2",
                        "100");
        client.savePreference(cid, pref, Set.of(1));
        check(client.matches(cid).size() == 1, "Coincidencias según perfil de búsqueda");
        agent.agreement(
                cid,
                pid,
                "Profesional de prueba",
                "Condiciones de comercialización",
                java.time.LocalDate.now());
        check(admin.agreements().size() == 1, "Convenio vinculado al propietario");
        agent.building("Complejo", "Dirección 50", "Olivos");
        Map<String, String> unit = property(cid);
        unit.put("id_edificio", "1");
        unit.put("numero_unidad", "A");
        int unitId = agent.saveProperty(null, unit, offers, Set.of(), false);
        denied(
                () -> agent.saveProperty(null, unit, offers, Set.of(), false),
                "Unidad duplicada rechazada");
        check(admin.buildings().getFirst().id("unidades") == 1, "Edificio conserva sus unidades");
        agent.close(pid, cid, 2, new BigDecimal("900000"), new BigDecimal("10000"));
        check(admin.operations().size() == 1, "Cierre y comisión registrados");
        denied(
                () -> agent.close(pid, cid, 2, new BigDecimal("900000"), BigDecimal.ZERO),
                "Doble cierre rechazado");
        check(
                guest.properties(true, "", null, null, null, "USD", 0, 0, Set.of()).isEmpty(),
                "Propiedad cerrada fuera del catálogo");
        agent.changeState(pid, 1, "Fin del alquiler");
        check(
                admin.property(pid).id("id_estado_verificacion") == 1,
                "Reactivación vuelve a borrador");
        agent.submit(pid);
        admin.review(admin.requests().getFirst().id("id_publicacion"), true, "Nueva revisión");
        admin.saveClient(oid, Set.of(), aid);
        agent.close(pid, oid, 1, new BigDecimal("240000"), new BigDecimal("5000"));
        check(admin.operations().size() == 2, "Dos operaciones históricas en una propiedad");
        agent.changeState(pid, 1, "Corrección del ciclo");
        agent.submit(pid);
        int stale = admin.requests().getFirst().id("id_publicacion");
        List<ServicioInmobiliaria.Offer> revisedOffers = List.of(
                new ServicioInmobiliaria.Offer(1, new BigDecimal("260000"), "USD"),
                new ServicioInmobiliaria.Offer(2, new BigDecimal("950000"), "ARS"));
        agent.saveProperty(pid, property(cid), revisedOffers, Set.of(), false);
        check(!admin.historialPrecios(pid).isEmpty(), "Las modificaciones de precios quedan consultables en el historial");
        check(!new ConsultasSQL(db).list("SELECT * FROM auditoria_cambios WHERE entidad='propiedades' AND id_registro=? AND campo LIKE 'precio_operacion_%'", pid).isEmpty(),
                "La auditoría detallada conserva los cambios de precio");
        denied(
                () -> admin.review(stale, true, ""),
                "Edición invalida solicitud pendiente anterior");
        int before = admin.allProperties().size();
        denied(
                () -> agent.saveProperty(null, property(cid), offers, Set.of(99999), false),
                "FK inválida revierte transacción");
        check(admin.allProperties().size() == before, "Rollback no deja propiedades parciales");
        check(
                !admin.activity().isEmpty() && !client.notifications().isEmpty(),
                "Auditoría y notificaciones generadas");
        admin.setActive(aid, false);
        denied(() -> agent.allProperties(), "Desactivación revoca sesión existente");
        denied(
                () -> admin.setActive(admin.current().id("id_usuario"), false),
                "Administrador no se desactiva a sí mismo");
        ConexionBaseDatos seedDb =
                new ConexionBaseDatos(
                        "jdbc:h2:mem:seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        work.resolve("demo"));
        seedDb.initialize();
        DatosDemostracion.seed(seedDb);
        DatosDemostracion.seed(seedDb);
        check(
                new ConsultasSQL(seedDb).list("SELECT * FROM propiedades").size() == 6,
                "Carga de demostración repetible sin duplicados");
        System.out.println("PASS · " + checks + " comprobaciones de integración");
    }
}
