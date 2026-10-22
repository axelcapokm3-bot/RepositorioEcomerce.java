package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Service.StripeWebhookService;
import com.CapaAplicacion.Service.WebhookEventoService;
import com.CapaDominio.Entidades.ProveedorWebhook;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Endpoint para eventos firmados de Stripe. */
public class StripeWebhookController extends BaseController {
    private static final Logger LOGGER =
            Logger.getLogger(StripeWebhookController.class.getName());
    private final StripeWebhookService webhookService;
    private final WebhookEventoService eventoWebhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        this(webhookService, null);
    }

    public StripeWebhookController(StripeWebhookService webhookService,
                                   WebhookEventoService eventoWebhookService) {
        if (webhookService == null) throw new IllegalArgumentException("StripeWebhookService es obligatorio.");
        this.webhookService = webhookService;
        this.eventoWebhookService = eventoWebhookService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Método no permitido. Use POST."));
            return;
        }
        if (limitarSolicitud(exchange, "webhook-stripe", 600)) {
            return;
        }

        String payload = leerCuerpoLimitado(exchange);
        try {
            if (!webhookService.haySecretoFirmaConfigurado()) {
                sendResponse(exchange, 503, Map.of("error", "El secreto del webhook de Stripe no está configurado."));
                return;
            }
            String signature = exchange.getRequestHeaders().getFirst("Stripe-Signature");
            Event event = webhookService.validarEvento(payload, signature);

            if (eventoWebhookService == null) {
                sendResponse(exchange, 200, Map.of("status", "accepted"));
                webhookService.procesarAsync(event);
                return;
            }

            try {
                eventoWebhookService.registrar(
                        ProveedorWebhook.STRIPE, event.getType(), event.getId(), payload);
            } catch (IllegalArgumentException exception) {
                sendResponse(exchange, 400, Map.of("error", exception.getMessage()));
                return;
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "No se pudo persistir el webhook de Stripe.", exception);
                sendResponse(exchange, 500, Map.of("error", "No se pudo registrar el webhook."));
                return;
            }

            sendResponse(exchange, 200, Map.of("status", "accepted"));
            try {
                eventoWebhookService.procesarPendientesAsync();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING,
                        "El webhook quedó registrado; el job lo procesará.", exception);
            }
        } catch (SignatureVerificationException e) {
            sendResponse(exchange, 400, Map.of("error", "Firma de Stripe inválida."));
        } catch (IllegalStateException e) {
            sendResponse(exchange, 503, Map.of("error", "El secreto del webhook de Stripe no está configurado."));
        } catch (RuntimeException e) {
            sendResponse(exchange, 503, Map.of("error", "No se pudo validar la configuración del webhook de Stripe."));
        }
    }
}
