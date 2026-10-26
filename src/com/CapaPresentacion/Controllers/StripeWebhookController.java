package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Service.StripeWebhookService;
import com.CapaAplicacion.Service.WebhookEventoService;
import com.CapaDominio.Entidades.ProveedorWebhook;
import com.CapaInfraestructura.Configuracion.SanitizadorLog;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;
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
            sendError(exchange, 405, "Método no permitido. Use POST.");
            return;
        }
        if (limitarSolicitud(exchange, "webhook-stripe", 600)) {
            return;
        }

        String payload = leerCuerpoLimitado(exchange);
        try {
            if (!webhookService.haySecretoFirmaConfigurado()) {
                sendError(exchange, 503, "El secreto del webhook de Stripe no está configurado.");
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
                sendError(exchange, 400, exception.getMessage());
                return;
            } catch (RuntimeException exception) {
                LOGGER.severe("No se pudo persistir el webhook de Stripe: "
                        + SanitizadorLog.error(exception));
                sendError(exchange, 500, "No se pudo registrar el webhook.");
                return;
            }

            sendResponse(exchange, 200, Map.of("status", "accepted"));
            try {
                eventoWebhookService.procesarPendientesAsync();
            } catch (RuntimeException exception) {
                LOGGER.warning("El webhook quedó registrado; el job lo procesará: "
                        + SanitizadorLog.error(exception));
            }
        } catch (SignatureVerificationException e) {
            sendError(exchange, 400, "Firma de Stripe inválida.");
        } catch (IllegalStateException e) {
            sendError(exchange, 503, "El secreto del webhook de Stripe no está configurado.");
        } catch (RuntimeException e) {
            sendError(exchange, 503, "No se pudo validar la configuración del webhook de Stripe.");
        }
    }
}
