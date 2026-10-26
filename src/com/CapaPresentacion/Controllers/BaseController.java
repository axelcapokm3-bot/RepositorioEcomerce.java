package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.CapaPresentacion.Utilidades.CabecerasSeguridad;
import com.CapaPresentacion.Utilidades.DireccionClienteResolver;
import com.CapaPresentacion.Utilidades.JsonUtils;
import com.CapaPresentacion.Utilidades.LimitadorTasa;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.ControlAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaInfraestructura.Configuracion.ConfigLoader;
import com.CapaInfraestructura.Configuracion.SanitizadorLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseController implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(BaseController.class.getName());
    private static final int MAX_CUERPO_BYTES = 1024 * 1024;
    private static final String ORIGENES_PREDETERMINADOS =
            "http://localhost:5500,http://127.0.0.1:5500,http://localhost:8080,http://127.0.0.1:8080";

    protected boolean handlePreflight(HttpExchange exchange) throws IOException {
        CabecerasSeguridad.aplicar(exchange);
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

            CabecerasSeguridad.aplicar(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
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
        sendResponse(exchange, status, ManejadorExcepcionesHttp.cuerpoError(status, message));
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
                sendError(exchange, 500, ManejadorExcepcionesHttp.MENSAJE_ERROR_INTERNO);
            }
        }).exceptionally(ex -> {
            manejarError(exchange, ex);
            return null;
        });
    }

    protected void manejarError(HttpExchange exchange, Throwable error) {
        ManejadorExcepcionesHttp.Respuesta respuesta = ManejadorExcepcionesHttp.traducir(error);
        if (respuesta.registrarSevero()) {
            LOGGER.severe("Error inesperado al procesar una solicitud: "
                    + SanitizadorLog.error(respuesta.causaParaLog()));
        } else if (respuesta.registrarAdvertencia()) {
            LOGGER.warning("No se pudo iniciar el checkout con la pasarela: "
                    + SanitizadorLog.error(respuesta.causaParaLog()));
        }
        sendError(exchange, respuesta.status(), respuesta.mensaje());
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
        return extraerToken(exchange).flatMap(proveedor::validar);
    }

    protected Optional<String> extraerToken(HttpExchange exchange) {
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
        return Optional.of(token);
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
        if (ResolutorNegocio.tieneIndicador(exchange)) {
            if (solicitado.isEmpty()) {
                throw new ControllerException.Validacion(
                        "El negocio indicado no es válido.",
                        "El encabezado X-Negocio-Subdominio no corresponde a un negocio activo.");
            }
            if (solicitado.get() != principal.idNegocio()) {
                throw new ControllerException.Prohibido(
                        "No tiene permisos sobre este negocio.",
                        "El negocio solicitado no coincide con el de su sesión.");
            }
        }
        return principal.idNegocio();
    }

    protected boolean limitarSolicitud(HttpExchange exchange, String ambito, int predeterminado) {
        return limitarSolicitud(exchange, null, ambito, predeterminado);
    }

    protected boolean limitarSolicitud(HttpExchange exchange, Principal principal,
                                       String ambito, int predeterminado) {
        int limiteIp = LimitadorTasa.limiteConfigurado(ambito, "ip", predeterminado);
        int limiteCuenta = LimitadorTasa.limiteConfigurado(ambito, "cuenta", predeterminado);
        int limiteNegocio = LimitadorTasa.limiteConfigurado(ambito, "negocio", predeterminado);
        LimitadorTasa.Resultado resultado =
                evaluarLimite(exchange, principal, null, ambito, limiteIp, limiteCuenta, limiteNegocio);
        return responderSiExcedido(exchange, resultado);
    }

    protected boolean limitarSolicitudPorClave(HttpExchange exchange, String clave,
                                               int maximo, long ventanaSegundos) {
        if (clave == null || clave.isBlank()) {
            return false;
        }
        return responderSiExcedido(exchange, LimitadorTasa.evaluar(clave, maximo, ventanaSegundos));
    }

    protected boolean limitarIntentosPorIp(HttpExchange exchange, String ambito,
                                           int maxIntentos, long bloqueoSegundos) {
        String clave = "intentos-ip|" + direccionCliente(exchange) + "|" + ambito;
        return responderSiExcedido(exchange, LimitadorTasa.evaluarIntento(
                clave, maxIntentos, LimitadorTasa.ventanaSegundos(ambito), bloqueoSegundos));
    }

    protected boolean limitarSolicitudPublica(HttpExchange exchange, long idNegocio,
                                              String ambito, int predeterminado) {
        int limiteIp = LimitadorTasa.limiteConfigurado(ambito, "ip", predeterminado);
        int limiteNegocio = LimitadorTasa.limiteConfigurado(ambito, "negocio", predeterminado);
        String direccion = direccionCliente(exchange);
        long ventana = LimitadorTasa.ventanaSegundos(ambito);
        LimitadorTasa.Resultado porIp = LimitadorTasa.evaluar(
                "ip|" + direccion + "|" + ambito, limiteIp, ventana);
        LimitadorTasa.Resultado porNegocio = LimitadorTasa.evaluar(
                "negocio-publico|" + idNegocio + "|" + ambito, limiteNegocio, ventana);
        return responderSiExcedido(exchange, masRestrictivo(porIp, porNegocio));
    }

    private boolean responderSiExcedido(HttpExchange exchange, LimitadorTasa.Resultado resultado) {
        if (!resultado.excedido()) {
            return false;
        }
        aplicarRetryAfter(exchange, resultado);
        sendError(exchange, 429,
                "Demasiadas solicitudes. Reintente en "
                        + resultado.segundosParaReintentar() + " segundos.");
        return true;
    }

    private LimitadorTasa.Resultado evaluarLimite(HttpExchange exchange, Principal principal,
                                                  Long idNegocioPublico, String ambito,
                                                  int limiteIp, int limiteCuenta, int limiteNegocio) {
        String direccion = direccionCliente(exchange);
        long ventana = LimitadorTasa.ventanaSegundos(ambito);
        LimitadorTasa.Resultado porIp = LimitadorTasa.evaluar(
                "ip|" + direccion + "|" + ambito, limiteIp, ventana);
        if (principal == null) {
            if (idNegocioPublico == null) {
                return porIp;
            }
            return masRestrictivo(porIp, LimitadorTasa.evaluar(
                    "negocio-publico|" + idNegocioPublico + "|" + ambito, limiteNegocio, ventana));
        }

        LimitadorTasa.Resultado porCuenta = LimitadorTasa.evaluar(
                "cuenta|" + principal.idCuenta() + "|" + ambito, limiteCuenta, ventana);
        LimitadorTasa.Resultado porNegocio = LimitadorTasa.evaluar(
                "negocio|" + principal.idNegocio() + "|" + ambito, limiteNegocio, ventana);
        return masRestrictivo(porIp, porCuenta, porNegocio);
    }

    private LimitadorTasa.Resultado masRestrictivo(LimitadorTasa.Resultado... resultados) {
        LimitadorTasa.Resultado masRestrictivo = resultados[0];
        for (LimitadorTasa.Resultado candidato : resultados) {
            if (candidato.excedido()
                    && (!masRestrictivo.excedido()
                    || candidato.segundosParaReintentar() > masRestrictivo.segundosParaReintentar())) {
                masRestrictivo = candidato;
            }
        }
        return masRestrictivo;
    }

    protected String direccionCliente(HttpExchange exchange) {
        return DireccionClienteResolver.resolver(exchange);
    }

    private void aplicarRetryAfter(HttpExchange exchange, LimitadorTasa.Resultado resultado) {
        exchange.getResponseHeaders().set(
                "Retry-After", String.valueOf(resultado.segundosParaReintentar()));
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
