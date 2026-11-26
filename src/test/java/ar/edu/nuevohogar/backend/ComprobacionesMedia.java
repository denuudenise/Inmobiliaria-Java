package ar.edu.nuevohogar.backend;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Verifica el protocolo HTTP real contra un receptor local; no sustituye al hosting. */
public final class ComprobacionesMedia {
    public static void run() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var failure = new AtomicReference<Throwable>();
        var requests = new AtomicInteger();
        byte[] fileBytes = "contenido binario de prueba\u0000\u0001".getBytes(StandardCharsets.UTF_8);
        server.createContext("/media", exchange -> {
            try {
                String action = exchange.getRequestURI().getQuery();
                byte[] request = exchange.getRequestBody().readAllBytes();
                String body = new String(request, StandardCharsets.UTF_8);
                String response;
                if (action.equals("action=login")) response = "{\"csrf_token\":\"test-token\"}";
                else {
                    String header = exchange.getRequestHeaders().getFirst("Content-Type");
                    String boundary = header.substring(header.indexOf("boundary=") + 9);
                    if (!body.endsWith("--" + boundary + "--\r\n") || !body.contains("test-token"))
                        throw new AssertionError("Multipart sin cierre o sin CSRF");
                    if (action.equals("action=upload") && !body.contains(new String(fileBytes, StandardCharsets.UTF_8)))
                        throw new AssertionError("Contenido de archivo incompleto");
                    response = "{\"ok\":true}";
                }
                requests.incrementAndGet();
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Throwable ex) {
                failure.set(ex);
                exchange.sendResponseHeaders(500, -1);
            } finally { exchange.close(); }
        });
        server.start();
        var file = Files.createTempFile("nh-media-protocol-", ".mp4");
        try {
            Files.write(file, fileBytes);
            var client = new MediaApiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/media", "");
            client.login("test@example.invalid", "no-real-account");
            if (client.storageWarning() != null) throw new AssertionError(client.storageWarning());
            client.upload("propiedad", 1, "Video", file);
            client.logout();
            if (failure.get() != null) throw new AssertionError(failure.get());
            if (requests.get() != 3) throw new AssertionError("Faltan peticiones");
        } finally { Files.deleteIfExists(file); server.stop(0); }
        System.out.println("OK auditoría: login, transmisión multipart de archivo y logout HTTP local");
    }
}
