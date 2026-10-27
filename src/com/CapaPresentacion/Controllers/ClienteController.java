package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.DTOsalida.ClienteResponseDTO;
import com.CapaAplicacion.DTOsalida.ResultadoPaginado;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Interfaces.IServiceCliente;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;

public class ClienteController extends BaseController {

    private static final int LIMITE_POR_MINUTO = 120;

    private final IServiceCliente servicio;
    private final IProveedorTokenAcceso proveedor;

    public ClienteController(IServiceCliente servicio, IProveedorTokenAcceso proveedor,
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
        if (handlePreflight(exchange)) {
            return;
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            Principal principalLimite = extraerPrincipal(exchange, proveedor).orElse(null);
            if (limitarSolicitud(exchange, principalLimite, "clientes-admin", LIMITE_POR_MINUTO)) {
                return;
            }
            if (path.equals("/api/clientes") || path.equals("/api/clientes/")) {
                if ("GET".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    
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
                    
                    CompletableFuture<List<ClienteResponseDTO>> dataFuture = servicio.listarTodosAsync(idNegocio, size, offset);
                    CompletableFuture<Long> totalFuture = servicio.contarTotalAsync(idNegocio);
                    
                    dataFuture.thenCombine(totalFuture, (data, total) -> 
                        new ResultadoPaginado<>(data, page, size, total)
                    ).thenAccept(result -> sendResponse(exchange, 200, result))
                     .exceptionally(ex -> {
                         manejarError(exchange, ex);
                         return null;
                     });
                } else if ("POST".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    ClienteRequestDTO dto = parseRequestBody(exchange, ClienteRequestDTO.class);
                    handleAsync(exchange, servicio.guardarAsync(idNegocio, dto), 201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                Integer id = getIntIdFromPath(exchange, "/api/clientes/");
                if (id == null) {
                    throw new ControllerException.Validacion("El ID de cliente no es válido.", "Debe ser un entero positivo.");
                }

                if ("GET".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirPermisoSobreCliente(principal, id);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    handleAsync(exchange, servicio.buscarPorIdAsync(idNegocio, id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirPermisoSobreCliente(principal, id);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    ClienteRequestDTO dto = parseRequestBody(exchange, ClienteRequestDTO.class);
                    handleAsync(exchange, servicio.actualizarAsync(idNegocio, id, dto), 200);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    handleAsync(exchange, servicio.eliminarAsync(idNegocio, id), 200);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
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
