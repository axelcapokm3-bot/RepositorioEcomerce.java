package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.StockService;
import com.CapaDominio.Entidades.Producto;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class StockController extends BaseController {

    private final StockService servicio;
    private final IProductoRepository productoRepository;
    private final IProveedorTokenAcceso proveedor;

    public record EvaluarStockRequest(int idProducto) {}

    public StockController(StockService servicio, IProductoRepository productoRepository,
                           IProveedorTokenAcceso proveedor) {
        if (servicio == null || productoRepository == null || proveedor == null) {
            throw new IllegalArgumentException("Las dependencias del controlador de stock son obligatorias.");
        }
        this.servicio = servicio;
        this.productoRepository = productoRepository;
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
            long idNegocio = negocioAutorizado(exchange, principal);

            if ("/api/stock/evaluar".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    EvaluarStockRequest req = parseRequestBody(exchange, EvaluarStockRequest.class);
                    if (req == null || req.idProducto() <= 0) {
                        throw new ControllerException.Validacion("El ID de producto no es válido.", "Debe ser un entero positivo.");
                    }
                    Optional<Producto> productoOpt = productoRepository.buscarPorId(idNegocio, req.idProducto());
                    if (productoOpt.isEmpty()) {
                        throw new ControllerException.NoEncontrado("Producto no encontrado.", "No existe el producto solicitado.");
                    }
                    CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> servicio.evaluarStock(productoOpt.get()));
                    handleAsync(exchange, future, 200);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.startsWith("/api/stock/")) {
                Integer id = getIntIdFromPath(exchange, "/api/stock/");
                if (id == null) {
                    throw new ControllerException.Validacion("El ID de producto no es válido.", "Debe ser un entero positivo.");
                }
                if ("GET".equalsIgnoreCase(method)) {
                    Optional<Producto> productoOpt = productoRepository.buscarPorId(idNegocio, id);
                    if (productoOpt.isEmpty()) {
                        throw new ControllerException.NoEncontrado("Producto no encontrado.", "No existe el producto solicitado.");
                    }
                    handleAsync(exchange, CompletableFuture.supplyAsync(() -> servicio.evaluarStock(productoOpt.get())), 200);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else {
                throw new ControllerException.NoEncontrado("Ruta no encontrada.", "No existe un recurso para la URL solicitada.");
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
