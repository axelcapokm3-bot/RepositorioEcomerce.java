package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WebhookServiceTest {
    private static final String CLAVE_WEBHOOK_SECRET = "mercadopago.webhook-secret";
    private static final String SECRETO_PRUEBA = "secreto-local-de-prueba";

    private final String secretoAnterior = System.getProperty(CLAVE_WEBHOOK_SECRET);
    private final WebhookService webhookService =
            new WebhookService(
                    null,
                    mock(IPagoRepository.class),
                    mock(IVentaRepository.class),
                    mock(EventBus.class));

    @AfterEach
    void restaurarSecreto() {
        if (secretoAnterior == null) {
            System.clearProperty(CLAVE_WEBHOOK_SECRET);
        } else {
            System.setProperty(CLAVE_WEBHOOK_SECRET, secretoAnterior);
        }
    }

    @Test
    void aceptaFirmaHmacValida() throws Exception {
        System.setProperty(CLAVE_WEBHOOK_SECRET, SECRETO_PRUEBA);
        String paymentId = "123456789";
        String requestId = "request-prueba";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = crearFirma(SECRETO_PRUEBA, paymentId, requestId, timestamp);

        assertTrue(webhookService.haySecretoFirmaConfigurado());
        assertTrue(webhookService.validarFirma(paymentId, signature, requestId));
    }

    @Test
    void rechazaFirmaIncorrectaYSecretoAusente() throws Exception {
        System.setProperty(CLAVE_WEBHOOK_SECRET, SECRETO_PRUEBA);
        String signature = crearFirma(SECRETO_PRUEBA, "123456789", "request-prueba",
                String.valueOf(Instant.now().getEpochSecond()));

        assertFalse(webhookService.validarFirma("123456789", signature, "request-distinto"));
        assertFalse(webhookService.validarFirma("123456789", "ts=1,v1=00", "request-prueba"));

        System.clearProperty(CLAVE_WEBHOOK_SECRET);
        assertFalse(webhookService.haySecretoFirmaConfigurado());
        assertFalse(webhookService.validarFirma("123456789", signature, "request-prueba"));
    }

    @Test
    void rechazaFirmaConTimestampVencido() throws Exception {
        System.setProperty(CLAVE_WEBHOOK_SECRET, SECRETO_PRUEBA);
        String vencido = String.valueOf(Instant.now().getEpochSecond() - 3600);
        String signature = crearFirma(SECRETO_PRUEBA, "123456789", "request-prueba", vencido);

        assertFalse(webhookService.validarFirma("123456789", signature, "request-prueba"));
    }

    private static String crearFirma(
            String secreto,
            String paymentId,
            String requestId,
            String timestamp) throws Exception {
        String manifiesto = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(hmac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
        return "ts=" + timestamp + ",v1=" + hash;
    }
}