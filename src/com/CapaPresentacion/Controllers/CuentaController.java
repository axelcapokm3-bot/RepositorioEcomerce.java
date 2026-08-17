package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.DTOentrada.CuentaRequestDTO;
import com.CapaAplicacion.Service.CuentaService;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class CuentaController extends BaseController {

    private final CuentaService servicio;

    public record TransferRequest(int idCuentaOrigen, int idCuentaDestino, double monto) {}

    public CuentaController(CuentaService servicio) {
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
            if (path.equals("/api/cuentas/transferir")) {
                if ("POST".equalsIgnoreCase(method)) {
                    TransferRequest req = parseRequestBody(exchange, TransferRequest.class);
                    handleAsync(exchange, servicio.transferirAsync(req.idCuentaOrigen(), req.idCuentaDestino(), req.monto()), 200);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else if (path.equals("/api/cuentas/buscarPorCorreo")) {
                if ("GET".equalsIgnoreCase(method)) {
                    String query = exchange.getRequestURI().getQuery();
                    String correo = null;
                    if (query != null && query.contains("correo=")) {
                        String[] pairs = query.split("&");
                        for (String pair : pairs) {
                            int idx = pair.indexOf("=");
                            if (idx > 0 && URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8).equals("correo")) {
                                correo = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                            }
                        }
                    }

                    if (correo == null || correo.trim().isEmpty()) {
                        sendError(exchange, 400, "Parámetro 'correo' es requerido.");
                    } else {
                        handleAsync(exchange, servicio.buscarPorCorreoAsync(correo), 200);
                    }
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else if (path.equals("/api/cuentas") || path.equals("/api/cuentas/")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.listarTodosAsync(), 200);
                } else if ("POST".equalsIgnoreCase(method)) {
                    CuentaRequestDTO dto = parseRequestBody(exchange, CuentaRequestDTO.class);
                    handleAsync(exchange, servicio.guardarAsync(dto), 201);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else {
                Integer id = getIntIdFromPath(exchange, "/api/cuentas/");
                if (id == null) {
                    sendError(exchange, 400, "ID de cuenta inválido o ausente.");
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    handleAsync(exchange, servicio.buscarPorIdAsync(id), 200);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    CuentaRequestDTO dto = parseRequestBody(exchange, CuentaRequestDTO.class);
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
