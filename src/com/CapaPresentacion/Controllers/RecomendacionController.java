package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Service.RecomendacionService;
import com.CapaDominio.Entidades.Producto;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RecomendacionController extends BaseController {

    private final RecomendacionService servicio;
    private final IProductoRepository productoRepository;

   public record RecomendacionRequest(
        int idProductoActual, 
        BigDecimal precioMin, 
        BigDecimal precioMax
    ) {}
    public RecomendacionController(RecomendacionService servicio, IProductoRepository productoRepository) {
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
            if ("/api/recomendaciones".equals(path) || "/api/recomendaciones/".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    RecomendacionRequest req = parseRequestBody(exchange, RecomendacionRequest.class);
                    Optional<Producto> productoActualOpt = productoRepository.buscarPorId(req.idProductoActual());
                    if (productoActualOpt.isEmpty()) {
                        sendError(exchange, 404, "Producto base no encontrado.");
                        return;
                    }
                    List<Producto> catalogo = productoRepository.listarTodos();
                    CompletableFuture<Object> future = CompletableFuture.supplyAsync(() ->
                        servicio.obtenerRecomendados(productoActualOpt.get(), catalogo, req.precioMin(), req.precioMax())
                    );
                    handleAsync(exchange, future, 200);
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
