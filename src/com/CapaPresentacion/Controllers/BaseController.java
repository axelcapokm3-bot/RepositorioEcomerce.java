package com.CapaPresentacion.Controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.CapaPresentacion.Utilidades.JsonUtils;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseController implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(BaseController.class.getName());

    protected boolean handlePreflight(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization, Idempotency-Key");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true; 
        }
        return false; 
    }

    protected void sendResponse(HttpExchange exchange, int status, Object body) {
        try {
            byte[] responseBytes;
            if (body == null) {
                responseBytes = new byte[0];
            } else if (body instanceof String) {
                responseBytes = ((String) body).getBytes(StandardCharsets.UTF_8);
            } else {
                responseBytes = JsonUtils.toJson(body).getBytes(StandardCharsets.UTF_8);
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, responseBytes.length == 0 && status != 204 ? -1 : responseBytes.length);
            if (responseBytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        } catch (IOException e) {
            System.err.println("Error enviando respuesta HTTP: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    protected void sendError(HttpExchange exchange, int status, String message) {
        String errorJson = "{\"error\":\"" + (message == null ? "Error desconocido" : message.replace("\"", "\\\"")) + "\"}";
        sendResponse(exchange, status, errorJson);
    }

    protected <T> T parseRequestBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return JsonUtils.fromJson(sb.toString(), clazz);
        }
    }

    protected <T> void handleAsync(HttpExchange exchange, CompletableFuture<T> future, int successStatus) {
        future.thenAccept(result -> {
            try {
                if (result == null) {
                    sendResponse(exchange, successStatus, null);
                } else if (result instanceof Optional) {
                    Optional<?> opt = (Optional<?>) result;
                    if (opt.isPresent()) {
                        sendResponse(exchange, successStatus, opt.get());
                    } else {
                        sendError(exchange, 404, "Recurso no encontrado");
                    }
                } else {
                    sendResponse(exchange, successStatus, result);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error procesando la respuesta HTTP.", e);
                sendError(exchange, 500, "Error procesando respuesta: " + e.getMessage());
            }
        }).exceptionally(ex -> {
            Throwable cause = obtenerCausa(ex);
            if (cause instanceof ControllerException controllerException) {
                sendError(exchange, controllerException.status(), controllerException.respuesta());
            } else if (cause instanceof PasarelaNoDisponibleException) {
                LOGGER.log(Level.WARNING, "No se pudo iniciar el checkout con la pasarela.", cause);
                sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                        "La pasarela seleccionada no está disponible.",
                        "Verifique la configuración del checkout y vuelva a intentarlo."
                ).respuesta());
            } else if (cause instanceof PagoRechazadoException) {
                sendError(exchange, 400, new ControllerException.Validacion(
                        cause.getMessage(),
                        "La pasarela rechazó los datos del checkout."
                ).respuesta());
            } else if (cause instanceof IllegalArgumentException) {
                sendError(exchange, 400, new ControllerException.Validacion(
                        "Los datos enviados no son válidos.",
                        "El servicio rechazó uno o más valores recibidos."
                ).respuesta());
            } else if (cause instanceof IllegalStateException) {
                sendError(exchange, 409, new ControllerException.Conflicto(
                        "La operación no puede realizarse en el estado actual.",
                        "La regla de negocio impide completar la operación."
                ).respuesta());
            } else if (cause instanceof java.io.IOException) {
                sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                        "No fue posible comunicarse con el servicio requerido.",
                        "Se produjo un problema de red o de comunicación."
                ).respuesta());
            } else {
                LOGGER.log(Level.SEVERE, "Error inesperado al procesar una solicitud asíncrona.", cause);
                sendError(exchange, 500,
                        "Código: ERROR_SERVIDOR | Mensaje: Error interno del servidor."
                                + " | Causa: Fallo inesperado al procesar la operación.");
            }
            return null;
        });
    }

    private Throwable obtenerCausa(Throwable exception) {
        Throwable cause = exception;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    protected Integer getIntIdFromPath(HttpExchange exchange, String contextPath) {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith(contextPath)) {
            String idStr = path.substring(contextPath.length());
            if (idStr.contains("/")) {
                idStr = idStr.substring(0, idStr.indexOf("/"));
            }
            try {
                return Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
