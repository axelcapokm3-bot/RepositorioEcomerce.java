package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Service.VentaService;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.IDescuentos;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.PoliticaDescuento;
import com.CapaDominio.Entidades.Producto;
import com.sun.net.httpserver.HttpExchange;

public class VentaController extends BaseController {

    private final VentaService servicio;
    private final IProductoRepository productoRepository;

    public record CreateVentaRequest(
        int idCliente,
        String tipoPago,
        DiscountInfo descuento,
        List<ItemRequest> items
    ) {}

    public record DiscountInfo(
        String tipo
    ) {}

    public record VentaPreview(
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal total
    ) {}

    public record ItemRequest(
        int idProducto,
        int cantidad
    ) {}

    public VentaController(VentaService servicio, IProductoRepository productoRepository) {
        this.servicio = servicio;
        this.productoRepository = productoRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if (path.equals("/api/ventas/previsualizar")) {
                if ("POST".equalsIgnoreCase(method)) {
                    CreateVentaRequest req = parseRequestBody(exchange, CreateVentaRequest.class);
                    Carrito carrito = construirCarrito(req);
                    IDescuentos descuento = resolverDescuento(req.descuento());
                    BigDecimal subtotal = carrito.calcularTotalCarrito(null);
                    BigDecimal total = carrito.calcularTotalCarrito(descuento);
                    sendResponse(exchange, 200, new VentaPreview(subtotal, subtotal.subtract(total), total));
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.equals("/api/ventas/crear")) {
                if ("POST".equalsIgnoreCase(method)) {
                    CreateVentaRequest req = parseRequestBody(exchange, CreateVentaRequest.class);
                    
                    Carrito carrito = construirCarrito(req);
                    IDescuentos descuento = resolverDescuento(req.descuento());

                    handleAsync(exchange, servicio.crearVentaAsync(carrito, req.tipoPago(), descuento), 201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.startsWith("/api/ventas/")) {
                Integer id = getIntIdFromPath(exchange, "/api/ventas/");
                if (id == null || id <= 0) {
                    throw new ControllerException.Validacion("El ID de venta no es válido.", "Debe ser un entero positivo.");
                }

                if (path.endsWith("/pago")) {
                    if ("POST".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.registrarPagoAsync(id), 200);
                    } else {
                        throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                    }
                } else if (path.endsWith("/cancelar")) {
                    if ("POST".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.cancelarVentaAsync(id), 200);
                    } else {
                        throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                    }
                } else {
     
                    if ("GET".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.buscarPorIdAsync(id), 200);
                    } else {
                        throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                    }
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

    private Carrito construirCarrito(CreateVentaRequest req) {
        if (req == null || req.idCliente() <= 0) {
            throw new ControllerException.Validacion(
                    "El cliente de la venta no es válido.",
                    "Debe indicar un ID de cliente positivo."
            );
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new ControllerException.Validacion(
                    "La venta debe incluir al menos un ítem.",
                    "La lista de artículos está vacía o no fue enviada."
            );
        }
        if (req.tipoPago() == null || req.tipoPago().isBlank()) {
            throw new ControllerException.Validacion(
                    "El tipo de pago es obligatorio.",
                    "No se recibió una forma de pago válida."
            );
        }

        Carrito carrito = new Carrito(req.idCliente());
        for (ItemRequest itemReq : req.items()) {
            if (itemReq == null || itemReq.idProducto() <= 0 || itemReq.cantidad() <= 0) {
                throw new ControllerException.Validacion(
                        "Los datos del artículo no son válidos.",
                        "El producto y la cantidad deben ser valores positivos."
                );
            }
            Optional<Producto> prodOpt = productoRepository.buscarPorId(itemReq.idProducto());
            if (prodOpt.isEmpty()) {
                throw new ControllerException.NoEncontrado(
                        "Producto no encontrado.",
                        "No existe un producto con el ID solicitado."
                );
            }
            carrito.agregarItem(new ItemCarrito(prodOpt.get(), itemReq.cantidad()));
        }
        return carrito;
    }

    private IDescuentos resolverDescuento(DiscountInfo descuento) {
        return PoliticaDescuento.resolver(descuento == null ? null : descuento.tipo());
    }
}
