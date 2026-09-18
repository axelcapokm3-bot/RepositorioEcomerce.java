package com.CapaPresentacion.Controllers;

import java.io.IOException;

import com.CapaAplicacion.Interfaces.IAuditoriaService;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import com.sun.net.httpserver.HttpExchange;

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
                    if (req == null || req.accion() == null || req.accion().isBlank()
                            || req.descripcion() == null || req.descripcion().isBlank()
                            || req.idEntidad() <= 0) {
                        throw new ControllerException.Validacion(
                                "Los datos de auditoría no son válidos.",
                                "La acción, el ID de entidad y la descripción son obligatorios."
                        );
                    }
                    AccionAuditoria accion;
                    try {
                        accion = AccionAuditoria.valueOf(req.accion().trim().toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        throw new ControllerException.Validacion(
                                "La acción de auditoría no es válida.",
                                "La acción recibida no existe en el catálogo permitido."
                        );
                    }
                    handleAsync(exchange, servicio.registrarAuditoriaAsync(new Auditoria(accion, req.idEntidad(), req.descripcion())), 201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.startsWith("/api/auditoria/producto/")) {
                Integer id = getIntIdFromPath(exchange, "/api/auditoria/producto/");
                if (id == null || id <= 0) {
                    throw new ControllerException.Validacion("El ID de entidad no es válido.", "Debe ser un entero positivo.");
                }
                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.obtenerPorProductoAsync(id), 200);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.startsWith("/api/auditoria/accion/")) {
                String accionRaw = path.substring("/api/auditoria/accion/".length());
                if (accionRaw == null || accionRaw.trim().isEmpty()) {
                    throw new ControllerException.Validacion("La acción es obligatoria.", "No se recibió el parámetro de acción.");
                }
                if ("GET".equalsIgnoreCase(method)) {
                    AccionAuditoria accion;
                    try {
                        accion = AccionAuditoria.valueOf(accionRaw.trim().toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        throw new ControllerException.Validacion("La acción de auditoría no es válida.", "La acción recibida no existe.");
                    }
                    handleAsync(exchange, servicio.obtenerPorAccionAsync(accion), 200);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                throw new ControllerException.NoEncontrado("Ruta no encontrada.", "No existe un recurso para la URL solicitada.");
            }
        } catch (ControllerException e) {
            sendError(exchange, e.status(), e.respuesta());
        } catch (IOException e) {
            sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                    "No fue posible responder al cliente.", "Problema de red o comunicación."
            ).respuesta());
        } catch (Exception e) {
            sendError(exchange, 500, "Código: ERROR_SERVIDOR | Mensaje: Error interno del servidor. | Causa: Fallo inesperado.");
        }
    }
}
