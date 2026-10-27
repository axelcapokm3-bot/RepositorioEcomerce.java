package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.CapaAplicacion.DTOsalida.ResultadoPaginado;
import com.CapaAplicacion.Interfaces.IAuditoriaService;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;

public class AuditoriaController extends BaseController {

    private final IAuditoriaService servicio;
    private final IProveedorTokenAcceso proveedor;

    public record CrearAuditoriaRequest(String accion, int idEntidad, String descripcion) {}

    public AuditoriaController(IAuditoriaService servicio, IProveedorTokenAcceso proveedor,
                               ResolutorNegocio resolutorNegocio) {
        super(resolutorNegocio);
        if (servicio == null || proveedor == null) {
            throw new IllegalArgumentException("El servicio y el proveedor de tokens son obligatorios.");
        }
        this.servicio = servicio;
        this.proveedor = proveedor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (handlePreflight(exchange)) {
            return;
        }

        try {
            Principal principal = exigirAutenticacion(exchange, proveedor);
            exigirAdmin(principal);
            long idNegocio = negocioAutorizado(exchange, principal);

            if ("/api/auditoria".equals(path) || "/api/auditoria/".equals(path)) {
                if ("GET".equalsIgnoreCase(method)) {
                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    int parsedPage = 1;
                    int parsedSize = 20;
                    try {
                        parsedPage = Math.max(1, Integer.parseInt(params.getOrDefault("page", "1")));
                        parsedSize = Math.max(1, Math.min(100, Integer.parseInt(params.getOrDefault("size", "20"))));
                    } catch (NumberFormatException e) {
                        // Usar defaults
                    }
                    final int page = parsedPage;
                    final int size = parsedSize;
                    int offset = (page - 1) * size;
                    
                    CompletableFuture<List<Auditoria>> dataFuture = servicio.obtenerTodosAsync(idNegocio, size, offset);
                    CompletableFuture<Long> totalFuture = servicio.contarTotalAsync(idNegocio);
                    
                    dataFuture.thenCombine(totalFuture, (data, total) -> 
                        new ResultadoPaginado<>(data, page, size, total)
                    ).thenAccept(result -> sendResponse(exchange, 200, result))
                     .exceptionally(ex -> {
                         manejarError(exchange, ex);
                         return null;
                     });
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
                    Auditoria auditoria = new Auditoria(accion, req.idEntidad(), req.descripcion());
                    auditoria.setIdNegocio(idNegocio);
                    auditoria.setUsuario(String.valueOf(principal.idCuenta()));
                    auditoria.setIpOrigen(direccionCliente(exchange));
                    auditoria.setUserAgent(exchange.getRequestHeaders().getFirst("User-Agent"));
                    handleAsync(exchange, servicio.registrarAuditoriaAsync(auditoria), 201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.startsWith("/api/auditoria/producto/")) {
                Integer id = getIntIdFromPath(exchange, "/api/auditoria/producto/");
                if (id == null || id <= 0) {
                    throw new ControllerException.Validacion("El ID de entidad no es válido.", "Debe ser un entero positivo.");
                }
                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.obtenerPorProductoAsync(idNegocio, id), 200);
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
                    handleAsync(exchange, servicio.obtenerPorAccionAsync(idNegocio, accion), 200);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                throw new ControllerException.NoEncontrado("Ruta no encontrada.", "No existe un recurso para la URL solicitada.");
            }
        } catch (ControllerException e) {
            manejarError(exchange, e);
        } catch (IOException e) {
            sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                    "No fue posible responder al cliente.", "Problema de red o comunicación."
            ).respuesta());
        } catch (Exception e) {
            manejarError(exchange, e);
        }
    }
}
