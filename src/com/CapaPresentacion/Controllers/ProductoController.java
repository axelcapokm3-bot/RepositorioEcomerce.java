package com.CapaPresentacion.Controllers;

import java.io.IOException;

import com.CapaAplicacion.DTOentrada.ProductoRequestDTO;
import com.CapaAplicacion.Interfaces.RepositorioProductoService;
import com.sun.net.httpserver.HttpExchange;

public class ProductoController extends BaseController {

    private final RepositorioProductoService servicio;

    public ProductoController(RepositorioProductoService servicio) {
        this.servicio = servicio;
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
                    handleAsync(exchange, servicio.listarTodosAsync(), 200);
                } else if ("POST".equalsIgnoreCase(method)) {
                    ProductoRequestDTO dto = parseRequestBody(exchange, ProductoRequestDTO.class);
                    handleAsync(exchange, servicio.guardarAsync(dto), 201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                Integer id = getIntIdFromPath(exchange, "/api/productos/");
                if (id == null) {
                    throw new ControllerException.Validacion("El ID de producto no es válido.", "Debe ser un entero positivo.");
                }

                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.buscarPorIdAsync(id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    ProductoRequestDTO dto = parseRequestBody(exchange, ProductoRequestDTO.class);
                    handleAsync(exchange, servicio.actualizarAsync(id, dto), 200);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.eliminarAsync(id), 200);
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