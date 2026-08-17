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
                    sendError(exchange, 405, "Método no permitido");
                }
            } else {
                Integer id = getIntIdFromPath(exchange, "/api/productos/");
                if (id == null) {
                    sendError(exchange, 400, "ID de producto inválido o ausente.");
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.buscarPorIdAsync(id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    ProductoRequestDTO dto = parseRequestBody(exchange, ProductoRequestDTO.class);
                    handleAsync(exchange, servicio.actualizarAsync(id, dto), 200);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.eliminarAsync(id), 200);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            }
        } catch (Exception e) {
            sendError(exchange, 400, "Error en la petición: " + e.getMessage());
        }
    }
}