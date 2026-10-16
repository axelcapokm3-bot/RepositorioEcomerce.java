package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.CapaPresentacion.Utilidades.JsonUtils;
import com.CapaPresentacion.Utilidades.LimitadorTasa;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.ControlAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Excepciones.PagoRechazadoException;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseController implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(BaseController.class.getName());
    private static final int MAX_CUERPO_BYTES = 1024 * 1024;
    private static final String ORIGENES_PREDETERMINADOS = "http://localhost:5500,http://localhost:8080";

    protected boolean handlePreflight(HttpExchange exchange) throws IOException {
        aplicarCors(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    protected void aplicarCors(HttpExchange exchange) {
        String origen = exchange.getRequestHeaders().getFirst("Origin");
        if (origen != null && esOrigenPermitido(origen)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origen);
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization, Idempotency-Key, X-Negocio-Subdominio");
    }

    private boolean esOrigenPermitido(String origen) {
        String configurados = ConfigLoader.getProperty("app.cors.allowed-origins");
        if (configurados == null || configurados.trim().isEmpty()) {
            configurados = ORIGENES_PREDETERMINADOS;
        }
        String[] permitidos = configurados.split(",");
        for (int i = 0; i < permitidos.length; i++) {
            if (origen.equals(permitidos[i].trim())) {
                return true;
            }
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
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, responseBytes.length == 0 && status != 204 ? -1 : responseBytes.length);
            if (responseBytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error enviando respuesta HTTP.", e);
        } finally {
            exchange.close();
        }
    }

    protected void sendError(HttpExchange exchange, int status, String message) {
        String errorJson = "{\"error\":\"" + (message == null ? "Error desconocido" : message.replace("\"", "\\\"")) + "\"}";
        sendResponse(exchange, status, errorJson);
    }

    protected <T> T parseRequestBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        String cuerpo = leerCuerpoLimitado(exchange);
        return JsonUtils.fromJson(cuerpo, clazz);
    }

    protected String leerCuerpoLimitado(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            StringBuilder sb = new StringBuilder();
            int leidos;
            while ((leidos = is.read(buffer)) != -1) {
                total += leidos;
                if (total > MAX_CUERPO_BYTES) {
                    throw new ControllerException.Validacion(
                            "El cuerpo de la solicitud es demasiado grande.",
                            "El tamaño máximo permitido es 1 MB.");
                }
                sb.append(new String(buffer, 0, leidos, StandardCharsets.UTF_8));
            }
            return sb.toString();
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
                sendError(exchange, 500,
                        "Código: ERROR_SERVIDOR | Mensaje: Error interno del servidor."
                                + " | Causa: Fallo inesperado al procesar la operación.");
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

    private Throwable obtenerCausa(Throwable exception) {        Throwable cause = exception;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    protected Principal exigirAutenticacion(HttpExchange exchange, IProveedorTokenAcceso proveedor) {
        Optional<Principal> principal = extraerPrincipal(exchange, proveedor);
        if (principal.isEmpty()) {
            throw new ControllerException.NoAutenticado(
                    "Se requiere autenticación.",
                    "Debe enviar un token válido en el encabezado Authorization.");
        }
        return principal.get();
    }

    protected Optional<Principal> extraerPrincipal(HttpExchange exchange, IProveedorTokenAcceso proveedor) {
        if (proveedor == null) {
            return Optional.empty();
        }
        String autorizacion = exchange.getRequestHeaders().getFirst("Authorization");
        if (autorizacion == null || autorizacion.isEmpty()) {
            return Optional.empty();
        }
        String prefijo = "Bearer ";
        if (!autorizacion.startsWith(prefijo)) {
            return Optional.empty();
        }
        String token = autorizacion.substring(prefijo.length()).trim();
        if (token.isEmpty() || token.length() > 2048) {
            return Optional.empty();
        }
        return proveedor.validar(token);
    }

    protected void exigirAdmin(Principal principal) {
        if (!ControlAcceso.esAdmin(principal)) {
            throw new ControllerException.Prohibido(
                    "No tiene permisos para esta operación.",
                    "Se requiere rol de administrador.");
        }
    }

    protected void exigirPermisoSobreCliente(Principal principal, int idCliente) {
        if (!ControlAcceso.puedeOperarCliente(principal, idCliente)) {
            throw new ControllerException.Prohibido(
                    "No tiene permisos sobre este recurso.",
                    "Solo puede operar sobre sus propios datos.");
        }
    }

    protected Optional<Long> resolverNegocio(HttpExchange exchange) {
        return ResolutorNegocio.resolver(exchange);
    }

    protected long exigirNegocio(HttpExchange exchange) {
        return resolverNegocio(exchange).orElseThrow(() -> new ControllerException.Validacion(
                "No se pudo identificar el negocio.",
                "Debe indicar el subdominio del negocio en el encabezado X-Negocio-Subdominio o en el host."));
    }

    protected void exigirPermisoSobreNegocio(Principal principal, long idNegocio) {
        if (!ControlAcceso.puedeOperarNegocio(principal, idNegocio)) {
            throw new ControllerException.Prohibido(
                    "No tiene permisos sobre este negocio.",
                    "Su cuenta no pertenece al negocio solicitado.");
        }
    }

    protected long negocioAutorizado(HttpExchange exchange, Principal principal) {
        Optional<Long> solicitado = resolverNegocio(exchange);
        if (solicitado.isPresent() && solicitado.get() != principal.idNegocio()) {
            throw new ControllerException.Prohibido(
                    "No tiene permisos sobre este negocio.",
                    "El negocio solicitado no coincide con el de su sesión.");
        }
        return principal.idNegocio();
    }

    protected boolean excedeLimite(HttpExchange exchange, int maxPorMinuto, String ambito) {
        String direccion = exchange.getRemoteAddress() == null
                ? "desconocida"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
        return LimitadorTasa.excede(direccion + "|" + ambito, maxPorMinuto);
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
