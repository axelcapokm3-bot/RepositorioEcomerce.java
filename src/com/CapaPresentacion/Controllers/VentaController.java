package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Service.VentaService;
import com.CapaDominio.Entidades.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
        String tipo, 
        double valor
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
            if (path.equals("/api/ventas/crear")) {
                if ("POST".equalsIgnoreCase(method)) {
                    CreateVentaRequest req = parseRequestBody(exchange, CreateVentaRequest.class);
                    
                    if (req.items() == null || req.items().isEmpty()) {
                        sendError(exchange, 400, "La venta debe incluir al menos un ítem.");
                        return;
                    }

                    Carrito carrito = new Carrito(req.idCliente());
                    for (ItemRequest itemReq : req.items()) {
                        Optional<Producto> prodOpt = productoRepository.buscarPorId(itemReq.idProducto());
                        if (!prodOpt.isPresent()) {
                            sendError(exchange, 400, "Producto no encontrado con ID: " + itemReq.idProducto());
                            return;
                        }
                        carrito.agregarItem(new ItemCarrito(prodOpt.get(), itemReq.cantidad()));
                    }

                    IDescuentos descuento = null;
                    if (req.descuento() != null && req.descuento().tipo() != null) {
                        String tipo = req.descuento().tipo().toUpperCase();
             BigDecimal valor = BigDecimal.valueOf(req.descuento().valor());

                        if (tipo.equals("PORCENTAJE")) {
                            descuento = new DescuentoPorPorcentaje(valor);
                        } else if (tipo.equals("MONTO")) {
                            descuento = new DescuentoPorMonto(valor, BigDecimal.ZERO);
                        } else if (tipo.equals("ESPECIAL")) {
                            descuento = new DescuentosEspeciales();
                        }
                    }

                    handleAsync(exchange, servicio.crearVentaAsync(carrito, req.tipoPago(), descuento), 201);
                } else {
                    sendError(exchange, 405, "Método no permitido");
                }
            } else if (path.startsWith("/api/ventas/")) {
                Integer id = getIntIdFromPath(exchange, "/api/ventas/");
                if (id == null) {
                    sendError(exchange, 400, "ID de venta inválido o ausente.");
                    return;
                }

                if (path.endsWith("/pago")) {
                    if ("POST".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.registrarPagoAsync(id), 200);
                    } else {
                        sendError(exchange, 405, "Método no permitido");
                    }
                } else if (path.endsWith("/cancelar")) {
                    if ("POST".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.cancelarVentaAsync(id), 200);
                    } else {
                        sendError(exchange, 405, "Método no permitido");
                    }
                } else {
     
                    if ("GET".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.buscarPorIdAsync(id), 200);
                    } else {
                        sendError(exchange, 405, "Método no permitido");
                    }
                }
            } else {
                sendError(exchange, 404, "Ruta no encontrada");
            }
        } catch (Exception e) {
            sendError(exchange, 400, "Error en la petición: " + e.getMessage());
        }
    }
}
