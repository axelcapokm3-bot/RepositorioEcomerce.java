package com.CapaPresentacion.Controllers;

import com.CapaPresentacion.Utilidades.JsonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class BaseController implements HttpHandler {
    protected boolean handlePreflight(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

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
                sendError(exchange, 500, "Error procesando respuesta: " + e.getMessage());
            }
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                sendError(exchange, 400, cause.getMessage());
            } else {
                sendError(exchange, 500, "Error interno del servidor: " + cause.getMessage());
            }
            return null;
        });
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
