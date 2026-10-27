package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Service.WebhookEventoService;
import com.CapaAplicacion.Service.WebhookService;
import com.CapaDominio.Entidades.ProveedorWebhook;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.CapaInfraestructura.Configuracion.SanitizadorLog;
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
    private final WebhookEventoService eventoWebhookService;

    public WebhookController(WebhookService webhookService, ResolutorNegocio resolutorNegocio) {
        this(webhookService, null, resolutorNegocio);
    }

    public WebhookController(WebhookService webhookService, WebhookEventoService eventoWebhookService,
                             ResolutorNegocio resolutorNegocio) {
        super(resolutorNegocio);
        if (webhookService == null) throw new IllegalArgumentException("WebhookService es obligatorio.");
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
        if (limitarSolicitud(exchange, "webhook-mercado-pago", 600)) {
            return;
        }

        final String notificationId;
        final String notificationType;
        final String cuerpoOriginal;
        try {
            cuerpoOriginal = leerCuerpoLimitado(exchange);
            JsonNode body = OBJECT_MAPPER.readTree(cuerpoOriginal);
            JsonNode idNode = body == null ? null : body.path("data").path("id");
            JsonNode typeNode = body == null ? null : (body.has("type") ? body.path("type") : body.path("topic"));
            
            if (idNode == null || idNode.isMissingNode() || idNode.isNull() || idNode.asText().isBlank()) {
                sendError(exchange, 400, "Falta data.id en la notificación.");
                return;
            }
            notificationId = idNode.asText();
            notificationType = typeNode != null && !typeNode.isMissingNode() && !typeNode.isNull()
                    ? typeNode.asText()
                    : "payment";

            if (notificationId.length() > 255) {
                sendError(exchange, 400, "data.id no puede superar 255 caracteres.");
                return;
            }
            if ("payment".equalsIgnoreCase(notificationType)
                    && !notificationId.matches("\\d+")) {
                sendError(exchange, 400, "data.id de un pago debe ser numérico.");
                return;
            }
        } catch (Exception e) {
            LOGGER.warning("Cuerpo de notificación de Mercado Pago inválido: "
                    + SanitizadorLog.error(e));
            sendError(exchange, 400, "El cuerpo JSON de la notificación no es válido.");
            return;
        }

        try {
            if (!webhookService.haySecretoFirmaConfigurado(notificationType, notificationId)) {
                sendError(exchange, 503, "La clave de firma del webhook no está configurada.");
                return;
            }
            String signature = exchange.getRequestHeaders().getFirst("x-signature");
            String requestId = exchange.getRequestHeaders().getFirst("x-request-id");
            if (!webhookService.validarFirma(notificationType, notificationId, signature, requestId)) {
                sendError(exchange, 401, "Firma de Mercado Pago inválida.");
                return;
            }
        } catch (RuntimeException e) {
            sendError(exchange, 503, "No se pudo validar la configuración del webhook.");
            return;
        }

        if (eventoWebhookService == null) {
            sendResponse(exchange, 200, Map.of("status", "accepted"));
            webhookService.procesarAsync(notificationType, notificationId);
            return;
        }

        try {
            eventoWebhookService.registrar(
                    ProveedorWebhook.MERCADO_PAGO, notificationType, notificationId, cuerpoOriginal);
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            LOGGER.severe("No se pudo persistir el webhook de Mercado Pago: "
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
    }
}
