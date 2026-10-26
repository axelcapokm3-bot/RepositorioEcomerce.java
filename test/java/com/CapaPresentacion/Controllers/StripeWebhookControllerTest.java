package com.CapaPresentacion.Controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Service.StripeWebhookService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StripeWebhookControllerTest {
    private static final String CLAVE_SECRETO = "stripe.webhook-secret";
    private static final String SECRETO = "whsec_prueba_local";
    private static final String CUERPO = "{\"id\":\"evt_test\",\"object\":\"event\","
            + "\"type\":\"checkout.session.completed\",\"data\":{\"object\":{}}}";

    private final String secretoAnterior = System.getProperty(CLAVE_SECRETO);
    private HttpServer server;

    @BeforeEach
    void iniciarServidor() throws Exception {
        System.setProperty(CLAVE_SECRETO, SECRETO);
        IServiceExecutor ejecutor = mock(IServiceExecutor.class);
        StripeWebhookService servicio = new StripeWebhookService(
                null, null, null, null, ejecutor, null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/webhooks/stripe", new StripeWebhookController(servicio));
        server.start();
    }

    @AfterEach
    void detenerServidor() {
        if (server != null) {
            server.stop(0);
        }
        if (secretoAnterior == null) {
            System.clearProperty(CLAVE_SECRETO);
        } else {
            System.setProperty(CLAVE_SECRETO, secretoAnterior);
        }
    }

    @Test
    @DisplayName("rechaza un webhook Stripe con firma inválida")
    void rechazaFirmaInvalida() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        HttpResponse<String> respuesta = enviar("t=" + timestamp + ",v1=00");

        assertEquals(400, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"error\":\"Firma de Stripe inválida.\""));
        assertTrue(respuesta.body().contains("\"codigo\":400"));
        assertTrue(respuesta.body().contains("\"timestamp\":"));
    }

    @Test
    @DisplayName("acepta un webhook Stripe con firma válida")
    void aceptaFirmaValida() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String firma = HexFormat.of().formatHex(hmac((timestamp + "." + CUERPO)
                .getBytes(StandardCharsets.UTF_8)));

        HttpResponse<String> respuesta = enviar("t=" + timestamp + ",v1=" + firma);

        assertEquals(200, respuesta.statusCode());
    }

    private HttpResponse<String> enviar(String firma) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/api/webhooks/stripe"))
                .header("Content-Type", "application/json")
                .header("Stripe-Signature", firma)
                .POST(HttpRequest.BodyPublishers.ofString(CUERPO))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] hmac(byte[] contenido) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return hmac.doFinal(contenido);
    }
}
