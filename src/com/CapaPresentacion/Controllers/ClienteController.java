package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.DTOentrada.ClienteRequestDTO;
import com.CapaAplicacion.Interfaces.IServiceCliente;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

public class ClienteController extends BaseController {

    private final IServiceCliente servicio;

    public ClienteController(IServiceCliente servicio) {
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
            if (path.equals("/api/clientes") || path.equals("/api/clientes/")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.listarTodosAsync(), 200);
                } else if ("POST".equalsIgnoreCase(method)) {
                    ClienteRequestDTO dto = parseRequestBody(exchange, ClienteRequestDTO.class);
                    handleAsync(exchange, servicio.guardarAsync(dto), 201);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else {
                Integer id = getIntIdFromPath(exchange, "/api/clientes/");
                if (id == null) {
                    sendError(exchange, 400, "ID de cliente inválido o ausente.");
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.buscarPorIdAsync(id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    ClienteRequestDTO dto = parseRequestBody(exchange, ClienteRequestDTO.class);
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
