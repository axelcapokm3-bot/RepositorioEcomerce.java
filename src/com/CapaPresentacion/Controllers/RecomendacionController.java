package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.RecomendacionService;
import com.CapaDominio.Entidades.Producto;
import com.sun.net.httpserver.HttpExchange;

public class RecomendacionController extends BaseController {

    private final RecomendacionService servicio;
    private final IProductoRepository productoRepository;
    private final IProveedorTokenAcceso proveedor;

   public record RecomendacionRequest(
        int idProductoActual, 
        BigDecimal precioMin, 
        BigDecimal precioMax
    ) {}
    public RecomendacionController(RecomendacionService servicio, IProductoRepository productoRepository,
                                   IProveedorTokenAcceso proveedor) {
        if (servicio == null || productoRepository == null || proveedor == null) {
            throw new IllegalArgumentException("Las dependencias del controlador son obligatorias.");
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
        Principal principal = exigirAutenticacion(exchange, proveedor);
        long idNegocio = negocioAutorizado(exchange, principal);

        try {
            if ("/api/recomendaciones".equals(path) || "/api/recomendaciones/".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    RecomendacionRequest req = parseRequestBody(exchange, RecomendacionRequest.class);
                    if (req == null || req.idProductoActual() <= 0) {
                        throw new ControllerException.Validacion(
                                "La solicitud de recomendación no es válida.",
                                "Debe indicar un ID de producto positivo."
                        );
                    }
                    if (req.precioMin() != null && req.precioMax() != null
                            && req.precioMin().compareTo(req.precioMax()) > 0) {
                        throw new ControllerException.Validacion(
                                "El rango de precios no es válido.",
                                "El precio mínimo no puede superar al precio máximo."
                        );
                    }
                    Optional<Producto> productoActualOpt = productoRepository.buscarPorId(idNegocio, req.idProductoActual());
                    if (productoActualOpt.isEmpty()) {
                        throw new ControllerException.NoEncontrado(
                                "El producto base no existe.",
                                "No se encontró el producto solicitado."
                        );
                    }
                    List<Producto> catalogo = productoRepository.listarTodos(idNegocio);
                    CompletableFuture<Object> future = CompletableFuture.supplyAsync(() ->
                        servicio.obtenerRecomendados(productoActualOpt.get(), catalogo, req.precioMin(), req.precioMax())
                    );
                    handleAsync(exchange, future, 200);
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
