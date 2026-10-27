package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseControllerCabecerasTest {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    private HttpServer server;
    private int puerto;

    @BeforeEach
    void iniciarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new ControladorDePrueba());
        server.start();
        puerto = server.getAddress().getPort();
    }

    @AfterEach
    void detenerServidor() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("aplica las cabeceras de seguridad en una respuesta 2xx")
    void aplicaCabecerasEnRespuestaExitosa() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/ok");

        assertEquals(200, respuesta.statusCode());
        afirmarCabecerasDeSeguridad(respuesta);
    }

    @Test
    @DisplayName("aplica las cabeceras de seguridad en una respuesta 4xx")
    void aplicaCabecerasEnErrorDeCliente() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/solicitud-invalida");

        assertEquals(400, respuesta.statusCode());
        afirmarCabecerasDeSeguridad(respuesta);
    }

    @Test
    @DisplayName("aplica las cabeceras de seguridad en una respuesta 5xx")
    void aplicaCabecerasEnErrorDeServidor() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/error-interno");

        assertEquals(500, respuesta.statusCode());
        afirmarCabecerasDeSeguridad(respuesta);
    }

    @Test
    @DisplayName("aplica las cabeceras de seguridad en el preflight 204")
    void aplicaCabecerasEnPreflight() throws Exception {
        HttpResponse<String> respuesta = enviar("OPTIONS", "/ok");

        assertEquals(204, respuesta.statusCode());
        afirmarCabecerasDeSeguridad(respuesta);
    }

    private HttpResponse<String> enviar(String metodo, String ruta) throws Exception {
        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto + ruta))
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(solicitud, HttpResponse.BodyHandlers.ofString());
    }

    private static void afirmarCabecerasDeSeguridad(HttpResponse<?> respuesta) {
        String politica = respuesta.headers()
                .firstValue("Content-Security-Policy")
                .orElse(null);
        assertEquals(CONTENT_SECURITY_POLICY, politica);
        assertFalse(politica.contains("unsafe-inline"));
        assertEquals("nosniff", respuesta.headers()
                .firstValue("X-Content-Type-Options")
                .orElse(null));
        assertEquals("strict-origin-when-cross-origin", respuesta.headers()
                .firstValue("Referrer-Policy")
                .orElse(null));
        assertEquals("DENY", respuesta.headers()
                .firstValue("X-Frame-Options")
                .orElse(null));
        assertEquals("geolocation=(), microphone=(), camera=()", respuesta.headers()
                .firstValue("Permissions-Policy")
                .orElse(null));
        assertTrue(respuesta.headers().firstValue("X-XSS-Protection").isEmpty());
    }

    private static final class ControladorDePrueba extends BaseController {

        private ControladorDePrueba() {
            super(ResolutorNegocioPrueba.crear());
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) {
                return;
            }
            String ruta = exchange.getRequestURI().getPath();
            if ("/solicitud-invalida".equals(ruta)) {
                sendError(exchange, 400, "Solicitud inválida.");
                return;
            }
            if ("/error-interno".equals(ruta)) {
                sendError(exchange, 500, "Error interno.");
                return;
            }
            sendResponse(exchange, 200, Map.of("estado", "ok"));
        }
    }
}
