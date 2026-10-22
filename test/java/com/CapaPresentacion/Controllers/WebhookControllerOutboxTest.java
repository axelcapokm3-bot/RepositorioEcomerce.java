package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Service.WebhookEventoService;
import com.CapaAplicacion.Service.WebhookService;
import com.CapaDominio.Entidades.ProveedorWebhook;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookControllerOutboxTest {
    private static final String CLAVE_WEBHOOK_SECRET = "mercadopago.webhook-secret";
    private static final String SECRETO_PRUEBA = "secreto-local-de-prueba";
    private static final String REQUEST_ID = "request-outbox";
    private static final String TIMESTAMP = "1728576000";
    private static final String ORDER_ID = "order-outbox-123";
    private static final String CUERPO =
            "{\"type\":\"order\",\"data\":{\"id\":\"" + ORDER_ID + "\"}}";

    private final String secretoAnterior = System.getProperty(CLAVE_WEBHOOK_SECRET);
    private HttpServer server;
    private WebhookEventoService eventoService;

    @BeforeEach
    void iniciarServidor() throws Exception {
        System.setProperty(CLAVE_WEBHOOK_SECRET, SECRETO_PRUEBA);
        eventoService = mock(WebhookEventoService.class);
        when(eventoService.registrar(any(), anyString(), anyString(), anyString())).thenReturn(true);
        WebhookService webhookService = new WebhookService(
                null,
                mock(IPagoRepository.class),
                mock(IVentaRepository.class),
                mock(EventBus.class),
                mock(IServiceExecutor.class),
                null,
                null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/webhooks/mercado-pago",
                new WebhookController(webhookService, eventoService));
        server.start();
    }

    @AfterEach
    void detenerServidor() {
        if (server != null) {
            server.stop(0);
        }
        if (secretoAnterior == null) {
            System.clearProperty(CLAVE_WEBHOOK_SECRET);
        } else {
            System.setProperty(CLAVE_WEBHOOK_SECRET, secretoAnterior);
        }
    }

    @Test
    void persisteAntesDeResponderYDisparaElProcesamiento() throws Exception {
        HttpResponse<String> response = enviarNotificacion(firmaValida());

        assertEquals(200, response.statusCode());
        verify(eventoService).registrar(
                eq(ProveedorWebhook.MERCADO_PAGO), eq("order"), eq(ORDER_ID), eq(CUERPO));
        verify(eventoService).procesarPendientesAsync();
    }

    @Test
    void responde500SiNoPudoPersistirElEvento() throws Exception {
        doThrow(new RuntimeException("db caída"))
                .when(eventoService).registrar(any(), anyString(), anyString(), anyString());

        HttpResponse<String> response = enviarNotificacion(firmaValida());

        assertEquals(500, response.statusCode());
        verify(eventoService, never()).procesarPendientesAsync();
    }

    @Test
    void responde400SiElEventoEsInvalido() throws Exception {
        doThrow(new IllegalArgumentException("evento inválido"))
                .when(eventoService).registrar(any(), anyString(), anyString(), anyString());

        HttpResponse<String> response = enviarNotificacion(firmaValida());

        assertEquals(400, response.statusCode());
        verify(eventoService, never()).procesarPendientesAsync();
    }

    private HttpResponse<String> enviarNotificacion(String signature) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/api/webhooks/mercado-pago"))
                .header("Content-Type", "application/json")
                .header("x-request-id", REQUEST_ID)
                .header("x-signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(CUERPO))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String firmaValida() throws Exception {
        String manifiesto = "id:" + ORDER_ID + ";request-id:" + REQUEST_ID + ";ts:" + TIMESTAMP + ";";
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRETO_PRUEBA.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(hmac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
        return "ts=" + TIMESTAMP + ",v1=" + hash;
    }
}
