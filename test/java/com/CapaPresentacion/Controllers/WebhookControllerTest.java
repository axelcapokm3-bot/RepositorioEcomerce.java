package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Service.WebhookService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookControllerTest {
    private static final String CLAVE_WEBHOOK_SECRET = "mercadopago.webhook-secret";
    private static final String SECRETO_PRUEBA = "secreto-local-de-prueba";
    private static final String REQUEST_ID = "request-integracion";
    private static final String TIMESTAMP = String.valueOf(java.time.Instant.now().getEpochSecond());
    private static final String ORDER_ID = "order-prueba-123";
    private static final String CUERPO =
            "{\"type\":\"order\",\"data\":{\"id\":\"" + ORDER_ID + "\"}}";

    private final String secretoAnterior = System.getProperty(CLAVE_WEBHOOK_SECRET);
    private HttpServer server;
    private IServiceExecutor executor;
    private CountDownLatch tareaProgramada;

    @BeforeEach
    void iniciarServidor() throws Exception {
        System.setProperty(CLAVE_WEBHOOK_SECRET, SECRETO_PRUEBA);
        executor = mock(IServiceExecutor.class);
        tareaProgramada = new CountDownLatch(1);
        doAnswer(invocation -> {
            tareaProgramada.countDown();
            return null;
        }).when(executor).ejecutar(any(Runnable.class));
        WebhookService webhookService = new WebhookService(
                null,
                mock(IPagoRepository.class),
                mock(IVentaRepository.class),
                mock(EventBus.class),
                executor,
                null,
                null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/webhooks/mercado-pago", new WebhookController(webhookService));
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
    void aceptaNotificacionConFirmaValida() throws Exception {
        HttpResponse<String> response = enviarNotificacion(
                crearFirma(ORDER_ID, REQUEST_ID, TIMESTAMP));

        assertEquals(200, response.statusCode());
        assertTrue(tareaProgramada.await(1, TimeUnit.SECONDS));
        verify(executor).ejecutar(any(Runnable.class));
    }

    @Test
    void rechazaNotificacionConFirmaInvalida() throws Exception {
        HttpResponse<String> response = enviarNotificacion("ts=" + TIMESTAMP + ",v1=00");

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"error\":\"Firma de Mercado Pago inválida.\""));
        assertTrue(response.body().contains("\"codigo\":401"));
        assertTrue(response.body().contains("\"timestamp\":"));
        verifyNoInteractions(executor);
    }

    @Test
    void informaCuandoNoHaySecretoDeWebhook() throws Exception {
        System.clearProperty(CLAVE_WEBHOOK_SECRET);

        HttpResponse<String> response = enviarNotificacion(
                crearFirma(ORDER_ID, REQUEST_ID, TIMESTAMP));

        assertEquals(503, response.statusCode());
        verifyNoInteractions(executor);
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

    private static String crearFirma(String id, String requestId, String timestamp) throws Exception {
        String manifiesto = "id:" + id + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRETO_PRUEBA.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(hmac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
        return "ts=" + timestamp + ",v1=" + hash;
    }
}
