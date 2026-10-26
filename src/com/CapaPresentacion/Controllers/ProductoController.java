package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.util.Optional;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Interfaces.RepositorioProductoService;
import com.CapaAplicacion.Seguridad.ControlAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.sun.net.httpserver.HttpExchange;

public class ProductoController extends BaseController {

    private static final int LIMITE_ADMIN_POR_MINUTO = 120;
    private static final int LIMITE_PUBLICO_POR_MINUTO = 300;

    private final RepositorioProductoService servicio;
    private final IProveedorTokenAcceso proveedor;

    public ProductoController(RepositorioProductoService servicio, IProveedorTokenAcceso proveedor) {
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
            if (path.equals("/api/productos") || path.equals("/api/productos/")) {
                if ("GET".equalsIgnoreCase(method)) {
                    long idNegocio = exigirNegocio(exchange);
                    if (limitarSolicitudPublica(exchange, idNegocio, "catalogo", LIMITE_PUBLICO_POR_MINUTO)) {
                        return;
                    }
                    handleAsync(exchange, esAdmin(exchange)
                            ? servicio.listarTodosAsync(idNegocio)
                            : servicio.listarActivosAsync(idNegocio), 200);
                } else if ("POST".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    if (limitarSolicitud(exchange, principal, "productos-admin", LIMITE_ADMIN_POR_MINUTO)) {
                        return;
                    }
                    long idNegocio = negocioAutorizado(exchange, principal);
                    ProductoRequestDTO dto = parseRequestBody(exchange, ProductoRequestDTO.class);
                    handleAsync(exchange, servicio.guardarAsync(idNegocio, dto), 201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                Integer id = getIntIdFromPath(exchange, "/api/productos/");
                if (id == null) {
                    throw new ControllerException.Validacion("El ID de producto no es válido.", "Debe ser un entero positivo.");
                }

                if ("GET".equalsIgnoreCase(method)) {
                    long idNegocio = exigirNegocio(exchange);
                    if (limitarSolicitudPublica(exchange, idNegocio, "catalogo", LIMITE_PUBLICO_POR_MINUTO)) {
                        return;
                    }
                    handleAsync(exchange, esAdmin(exchange)
                            ? servicio.buscarPorIdAsync(idNegocio, id)
                            : servicio.buscarActivoPorIdAsync(idNegocio, id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    if (limitarSolicitud(exchange, principal, "productos-admin", LIMITE_ADMIN_POR_MINUTO)) {
                        return;
                    }
                    long idNegocio = negocioAutorizado(exchange, principal);
                    ProductoRequestDTO dto = parseRequestBody(exchange, ProductoRequestDTO.class);
                    handleAsync(exchange, servicio.actualizarAsync(idNegocio, id, dto), 200);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    if (limitarSolicitud(exchange, principal, "productos-admin", LIMITE_ADMIN_POR_MINUTO)) {
                        return;
                    }
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
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Solicitud inválida: " + e.getMessage());
        } catch (Exception e) {
            manejarError(exchange, e);
        }
    }

    private boolean esAdmin(HttpExchange exchange) {
        Optional<Principal> principal = extraerPrincipal(exchange, proveedor);
        return principal.isPresent() && ControlAcceso.esAdmin(principal.get());
    }
}