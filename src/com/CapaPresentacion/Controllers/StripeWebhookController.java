package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Endpoint para eventos firmados de Stripe. */
public class StripeWebhookController extends BaseController {
    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        if (webhookService == null) throw new IllegalArgumentException("StripeWebhookService es obligatorio.");
        this.webhookService = webhookService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Método no permitido. Use POST."));
            return;
        }

        String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!webhookService.haySecretoFirmaConfigurado()) {
                sendResponse(exchange, 503, Map.of("error", "El secreto del webhook de Stripe no está configurado."));
                return;
            }
            String signature = exchange.getRequestHeaders().getFirst("Stripe-Signature");
            Event event = webhookService.validarEvento(payload, signature);
            sendResponse(exchange, 200, Map.of("status", "accepted"));
            webhookService.procesarAsync(event);
        } catch (SignatureVerificationException e) {
            sendResponse(exchange, 400, Map.of("error", "Firma de Stripe inválida."));
        } catch (IllegalStateException e) {
            sendResponse(exchange, 503, Map.of("error", "El secreto del webhook de Stripe no está configurado."));
        } catch (RuntimeException e) {
            sendResponse(exchange, 503, Map.of("error", "No se pudo validar la configuración del webhook de Stripe."));
        }
    }
}
