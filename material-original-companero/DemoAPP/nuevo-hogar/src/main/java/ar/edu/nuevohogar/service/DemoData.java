package ar.edu.nuevohogar.service;

import static ar.edu.nuevohogar.dao.JdbcDao.*;

import ar.edu.nuevohogar.dao.*;

import java.math.BigDecimal;
import java.util.*;

public final class DemoData {
    public static final String PASSWORD = "DemoHogar2026!";

    public static void seed(Database db) {
        if (!db.demo || !new JdbcDao(db).list("SELECT id_usuario FROM usuarios").isEmpty()) return;
        AgencyService s = new AgencyService(db);
        s.bootstrapAdmin(user("Ana", "Suárez", "10000001", "admin@nuevohogar.demo"), PASSWORD);
        s.login("admin@nuevohogar.demo", PASSWORD);
        int agent =
                s.createStaffUser(
                        user("Lucía", "Fernández", "10000002", "agente@nuevohogar.demo"),
                        PASSWORD,
                        2);
        int client =
                s.register(user("Juan", "Pérez", "10000003", "cliente@nuevohogar.demo"), PASSWORD);
        s.building("Edificio del Parque", "Av. Maipú 1250", "Vicente López");
        String[] titles = {
            "Casa con jardín en Los Robles",
            "Departamento luminoso en Palermo",
            "Casa con pileta en Tigre",
            "Terreno en Luján",
            "Departamento junto al parque",
            "Oficina en el centro"
        };
        String[] cities = {
            "San Isidro", "Palermo, CABA", "Tigre", "Luján", "Vicente López", "Olivos"
        };
        int[] types = {1, 2, 1, 5, 2, 4};
        int[] prices = {250000, 180000, 1200, 45000, 95000, 750};
        for (int i = 0; i < titles.length; i++) {
            s.login("agente@nuevohogar.demo", PASSWORD);
            Map<String, String> d = new HashMap<>();
            d.put("titulo", titles[i]);
            d.put("direccion", "Av. Libertador " + (1240 + i * 150));
            d.put("localidad", cities[i]);
            d.put(
                    "descripcion",
                    "Una propiedad para empezar una nueva etapa. Espacios amplios, buena luz"
                        + " natural y cercanía a comercios y transporte. Datos de ejemplo para"
                        + " practicar el circuito de gestión inmobiliaria.");
            d.put("id_tipo_propiedad", "" + types[i]);
            d.put("id_propietario", "" + client);
            d.put("superficie_total_m2", i == 3 ? "800" : "180");
            d.put("superficie_cubierta_m2", i == 3 ? "0" : "120");
            d.put("ambientes", "4");
            d.put("dormitorios", "3");
            d.put("banos", "2");
            d.put("antiguedad_anos", "12");
            d.put("capacidad", "5");
            d.put("expensas", "0");
            if (i == 4) {
                d.put("id_edificio", "1");
                d.put("numero_unidad", "3B");
            }
            int id =
                    s.saveProperty(
                            null,
                            d,
                            List.of(
                                    new AgencyService.Offer(
                                            i == 2 || i == 5 ? 2 : 1,
                                            new BigDecimal(prices[i]),
                                            "USD")),
                            i == 3 ? Set.of() : Set.of(2, 6),
                            true);
            if (i < 4) {
                s.login("admin@nuevohogar.demo", PASSWORD);
                int pid = id;
                int request =
                        s.requests().stream()
                                .filter(r -> r.id("id_propiedad") == pid)
                                .findFirst()
                                .orElseThrow()
                                .id("id_publicacion");
                s.review(request, true, "Datos de demostración revisados");
            }
            if (i == 5) {
                s.login("admin@nuevohogar.demo", PASSWORD);
                int pid = id;
                s.review(
                        s.requests().stream()
                                .filter(r -> r.id("id_propiedad") == pid)
                                .findFirst()
                                .orElseThrow()
                                .id("id_publicacion"),
                        false,
                        "Falta completar la documentación.");
            }
        }
        s.login("agente@nuevohogar.demo", PASSWORD);
        s.saveClient(client, Set.of("Propietario", "Comprador"), agent);
        s.contact(
                client,
                1,
                "Llamada",
                "Consulta por la casa en Los Robles.",
                "Coordinar una visita.");
        s.interest(client, 1, "Visita coordinada", "Visita solicitada por el cliente.");
        s.agreement(
                client,
                1,
                "",
                "Comercialización no exclusiva. Comisión a convenir por escrito.",
                java.time.LocalDate.now());
        s.logout();
    }

    private static Map<String, String> user(String name, String last, String dni, String email) {
        return Map.of(
                "nombre",
                name,
                "apellido",
                last,
                "dni",
                dni,
                "email",
                email,
                "telefono",
                "11 5555 0100",
                "direccion",
                "Av. Maipú 1200",
                "localidad",
                "Vicente López");
    }
}
