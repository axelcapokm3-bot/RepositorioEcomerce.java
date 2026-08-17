package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Service.StockService;
import com.CapaDominio.Entidades.Producto;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class StockController extends BaseController {

    private final StockService servicio;
    private final IProductoRepository productoRepository;

    public record EvaluarStockRequest(int idProducto) {}

    public StockController(StockService servicio, IProductoRepository productoRepository) {
        this.servicio = servicio;
        this.productoRepository = productoRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (handlePreflight(exchange)) {
            return;
        }

        try {
            if ("/api/stock/evaluar".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    EvaluarStockRequest req = parseRequestBody(exchange, EvaluarStockRequest.class);
                    Optional<Producto> productoOpt = productoRepository.buscarPorId(req.idProducto());
                    if (productoOpt.isEmpty()) {
                        sendError(exchange, 404, "Producto no encontrado.");
                        return;
                    }
                    CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> servicio.evaluarStock(productoOpt.get()));
                    handleAsync(exchange, future, 200);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else if (path.startsWith("/api/stock/")) {
                Integer id = getIntIdFromPath(exchange, "/api/stock/");
                if (id == null) {
                    sendError(exchange, 400, "ID de producto inválido o ausente.");
                    return;
                }
                if ("GET".equalsIgnoreCase(method)) {
                    Optional<Producto> productoOpt = productoRepository.buscarPorId(id);
                    if (productoOpt.isEmpty()) {
                        sendError(exchange, 404, "Producto no encontrado.");
                        return;
                    }
                    handleAsync(exchange, CompletableFuture.supplyAsync(() -> servicio.evaluarStock(productoOpt.get())), 200);
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
