package com.CapaPresentacion.Controllers;

import java.io.IOException;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Interfaces.RepositorioProductoService;
import com.CapaAplicacion.Seguridad.Principal;
import com.sun.net.httpserver.HttpExchange;

public class ProductoController extends BaseController {

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
                    handleAsync(exchange, servicio.listarTodosAsync(idNegocio), 200);
                } else if ("POST".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
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
                    handleAsync(exchange, servicio.buscarPorIdAsync(idNegocio, id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    exigirAdmin(principal);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    ProductoRequestDTO dto = parseRequestBody(exchange, ProductoRequestDTO.class);
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