package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Interfaces.IAuditoriaService;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

public class AuditoriaController extends BaseController {

    private final IAuditoriaService servicio;

    public record CrearAuditoriaRequest(String accion, int idEntidad, String descripcion) {}

    public AuditoriaController(IAuditoriaService servicio) {
        this.servicio = servicio;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (handlePreflight(exchange)) {
            return;
        }

        try {
            if ("/api/auditoria".equals(path) || "/api/auditoria/".equals(path)) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.obtenerTodosAsync(), 200);
                } else if ("POST".equalsIgnoreCase(method)) {
                    CrearAuditoriaRequest req = parseRequestBody(exchange, CrearAuditoriaRequest.class);
                    if (req == null || req.accion() == null || req.descripcion() == null) {
                        sendError(exchange, 400, "La acción, el ID y la descripción son obligatorios.");
                        return;
                    }
                    AccionAuditoria accion = AccionAuditoria.valueOf(req.accion().toUpperCase());
                    handleAsync(exchange, servicio.registrarAuditoriaAsync(new Auditoria(accion, req.idEntidad(), req.descripcion())), 201);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else if (path.startsWith("/api/auditoria/producto/")) {
                Integer id = getIntIdFromPath(exchange, "/api/auditoria/producto/");
                if (id == null) {
                    sendError(exchange, 400, "ID de entidad inválido o ausente.");
                    return;
                }
                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.obtenerPorProductoAsync(id), 200);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else if (path.startsWith("/api/auditoria/accion/")) {
                String accionRaw = path.substring("/api/auditoria/accion/".length());
                if (accionRaw == null || accionRaw.trim().isEmpty()) {
                    sendError(exchange, 400, "Parámetro de acción requerido.");
                    return;
                }
                if ("GET".equalsIgnoreCase(method)) {
                    AccionAuditoria accion = AccionAuditoria.valueOf(accionRaw.toUpperCase());
                    handleAsync(exchange, servicio.obtenerPorAccionAsync(accion), 200);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else {
                sendError(exchange, 404, "Ruta no encontrada");
            }
        } catch (Exception e) {
            sendError(exchange, 400, "Error en la petición: " + e.getMessage());
        }
    }
}
