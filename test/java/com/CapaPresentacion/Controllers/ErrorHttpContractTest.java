package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.CapaDominio.Excepciones.FirmaInvalidaException;
import com.CapaDominio.Excepciones.MetodoPagoNoSoportadoException;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaDominio.Excepciones.ValidacionException;
import com.CapaPresentacion.Utilidades.JsonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorHttpContractTest {

    private HttpServer server;
    private int puerto;

    @BeforeEach
    void iniciarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new ControladorDeErrores());
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
    @DisplayName("las excepciones comparten status y cuerpo de error unificado")
    void contratoDeErrores() {
        assertAll(
                () -> afirmarError("/no-encontrado", 404, "Producto no encontrado."),
                () -> afirmarError("/firma", 401, "Firma inválida."),
                () -> afirmarError("/metodo-pago", 400, "Método no soportado."),
                () -> afirmarError("/validacion", 400, "Datos inválidos."),
                () -> afirmarError("/prohibido", 403, "Sin permiso."),
                () -> {
                    Map<?, ?> json = afirmarError(
                            "/inesperado", 500, ManejadorExcepcionesHttp.MENSAJE_ERROR_INTERNO);
                    assertFalse(json.get("error").toString().contains("boom"));
                    assertFalse(json.get("error").toString().contains("RuntimeException"));
                });
    }

    private Map<?, ?> afirmarError(String ruta, int status, String mensaje) throws Exception {
        HttpResponse<String> respuesta = enviar(ruta);
        assertEquals(status, respuesta.statusCode());
        Map<?, ?> json = (Map<?, ?>) JsonUtils.parse(respuesta.body());
        assertNotNull(json);
        assertEquals(mensaje, json.get("error"));
        assertEquals((long) status, json.get("codigo"));
        assertNotNull(json.get("timestamp"));
        Instant.parse(json.get("timestamp").toString());
        return json;
    }

    private HttpResponse<String> enviar(String ruta) throws Exception {
        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto + ruta))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(solicitud, HttpResponse.BodyHandlers.ofString());
    }

    private static final class ControladorDeErrores extends BaseController {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) {
                return;
            }
            try {
                switch (exchange.getRequestURI().getPath()) {
                    case "/no-encontrado" ->
                            throw new RecursoNoEncontradoException("Producto no encontrado.");
                    case "/firma" ->
                            throw new FirmaInvalidaException("Firma inválida.");
                    case "/metodo-pago" ->
                            throw new MetodoPagoNoSoportadoException("Método no soportado.");
                    case "/validacion" ->
                            throw new ValidacionException("Datos inválidos.");
                    case "/prohibido" ->
                            throw new ControllerException.Prohibido("Sin permiso.", "rol insuficiente");
                    case "/inesperado" ->
                            throw new RuntimeException("boom con stacktrace");
                    default -> sendResponse(exchange, 200, Map.of("estado", "ok"));
                }
            } catch (Exception e) {
                manejarError(exchange, e);
            }
        }
    }
}
