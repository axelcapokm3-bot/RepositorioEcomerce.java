package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/** Endpoint para notificaciones de pago enviadas por Mercado Pago. */
public class WebhookController extends BaseController {
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(WebhookController.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        if (webhookService == null) throw new IllegalArgumentException("WebhookService es obligatorio.");
        this.webhookService = webhookService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, Map.of("error", "Método no permitido. Use POST."));
            return;
        }
        if (excedeLimite(exchange, 60, "webhook-mp")) {
            sendResponse(exchange, 429, Map.of("error", "Demasiadas solicitudes. Intente nuevamente en un minuto."));
            return;
        }

        final String notificationId;
        final String notificationType;
        try {
            JsonNode body = OBJECT_MAPPER.readTree(leerCuerpoLimitado(exchange));
            JsonNode idNode = body == null ? null : body.path("data").path("id");
            JsonNode typeNode = body == null ? null : (body.has("type") ? body.path("type") : body.path("topic"));
            
            if (idNode == null || idNode.isMissingNode() || idNode.isNull() || idNode.asText().isBlank()) {
                sendResponse(exchange, 400, Map.of("error", "Falta data.id en la notificación."));
                return;
            }
            notificationId = idNode.asText();
            notificationType = typeNode != null && !typeNode.isMissingNode() && !typeNode.isNull()
                    ? typeNode.asText()
                    : "payment";

            if (notificationId.length() > 255) {
                sendResponse(exchange, 400, Map.of("error", "data.id no puede superar 255 caracteres."));
                return;
            }
            if ("payment".equalsIgnoreCase(notificationType)
                    && !notificationId.matches("\\d+")) {
                sendResponse(exchange, 400, Map.of("error", "data.id de un pago debe ser numérico."));
                return;
            }
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Cuerpo de notificación de Mercado Pago inválido.", e);
            sendResponse(exchange, 400, Map.of("error", "El cuerpo JSON de la notificación no es válido."));
            return;
        }

        try {
            if (!webhookService.haySecretoFirmaConfigurado()) {
                sendResponse(exchange, 503, Map.of("error", "La clave de firma del webhook no está configurada."));
                return;
            }
            String signature = exchange.getRequestHeaders().getFirst("x-signature");
            String requestId = exchange.getRequestHeaders().getFirst("x-request-id");
            if (!webhookService.validarFirma(notificationId, signature, requestId)) {
                sendResponse(exchange, 401, Map.of("error", "Firma de Mercado Pago inválida."));
                return;
            }
        } catch (RuntimeException e) {
            sendResponse(exchange, 503, Map.of("error", "No se pudo validar la configuración del webhook."));
            return;
        }

        sendResponse(exchange, 200, Map.of("status", "accepted"));
        webhookService.procesarAsync(notificationType, notificationId);
    }
}
