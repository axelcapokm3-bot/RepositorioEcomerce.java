package com.CapaPresentacion.Controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.VentaService;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.IDescuentos;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Negocio;
import com.CapaDominio.Entidades.PoliticaDescuento;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.Venta;
import com.sun.net.httpserver.HttpExchange;

public class VentaController extends BaseController {

    private static final int LIMITE_POR_MINUTO = 120;

    private final VentaService servicio;
    private final IProductoRepository productoRepository;
    private final IVentaRepository ventaRepository;
    private final IProveedorTokenAcceso proveedor;
    private final INegocioRepository negocioRepository;

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

    public VentaController(VentaService servicio, IProductoRepository productoRepository,
                           IVentaRepository ventaRepository, IProveedorTokenAcceso proveedor) {
        this(servicio, productoRepository, ventaRepository, proveedor, null);
    }

    public VentaController(VentaService servicio, IProductoRepository productoRepository,
                           IVentaRepository ventaRepository, IProveedorTokenAcceso proveedor,
                           INegocioRepository negocioRepository) {
        if (servicio == null || productoRepository == null
                || ventaRepository == null || proveedor == null) {
            throw new IllegalArgumentException("Las dependencias del controlador de ventas son obligatorias.");
        }
        this.servicio = servicio;
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.proveedor = proveedor;
        this.negocioRepository = negocioRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            Principal principalLimite = extraerPrincipal(exchange, proveedor).orElse(null);
            String ambitoLimite;
            if (path.equals("/api/ventas/crear")) {
                ambitoLimite = "ventas-crear";
            } else if (path.equals("/api/ventas/previsualizar")) {
                ambitoLimite = "ventas-previsualizar";
            } else {
                ambitoLimite = "ventas-consulta";
            }
            if (limitarSolicitud(exchange, principalLimite, ambitoLimite, LIMITE_POR_MINUTO)) {
                return;
            }
            if (path.equals("/api/ventas/previsualizar")) {
                if ("POST".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    CreateVentaRequest req = parseRequestBody(exchange, CreateVentaRequest.class);
                    if (req == null) {
                        throw new ControllerException.Validacion(
                                "La venta no es válida.",
                                "Debe enviar el cliente, el tipo de pago y los artículos.");
                    }
                    exigirPermisoSobreCliente(principal, req.idCliente());
                    Carrito carrito = construirCarrito(idNegocio, req);
                    IDescuentos descuento = resolverDescuento(req.descuento());
                    BigDecimal subtotal = carrito.calcularTotalCarrito(null);
                    BigDecimal total = carrito.calcularTotalCarrito(descuento);
                    sendResponse(exchange, 200, new VentaPreview(subtotal, subtotal.subtract(total), total));
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.equals("/api/ventas/crear")) {
                if ("POST".equalsIgnoreCase(method)) {
                    Principal principal = exigirAutenticacion(exchange, proveedor);
                    long idNegocio = negocioAutorizado(exchange, principal);
                    CreateVentaRequest req = parseRequestBody(exchange, CreateVentaRequest.class);
                    if (req == null) {
                        throw new ControllerException.Validacion(
                                "La venta no es válida.",
                                "Debe enviar el cliente, el tipo de pago y los artículos.");
                    }
                    exigirPermisoSobreCliente(principal, req.idCliente());
                    String claveIdempotenciaSolicitud =
                            exchange.getRequestHeaders().getFirst("Idempotency-Key");
                    if (claveIdempotenciaSolicitud == null || claveIdempotenciaSolicitud.isBlank()) {
                        throw new ControllerException.Validacion(
                                "Falta el encabezado Idempotency-Key.",
                                "Debe enviar una clave única y estable para esta creación de venta.");
                    }
                    if (claveIdempotenciaSolicitud.length() > 255) {
                        throw new ControllerException.Validacion(
                                "El encabezado Idempotency-Key no es válido.",
                                "La clave no puede superar 255 caracteres.");
                    }
                    Carrito carrito = construirCarrito(idNegocio, req);
                    IDescuentos descuento = resolverDescuento(req.descuento());

                    handleAsync(
                            exchange,
                            servicio.crearVentaConPagoAsync(
                                    idNegocio, carrito, req.tipoPago(), descuento, claveIdempotenciaSolicitud),
                            201);
                } else {
                    throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                }
            } else if (path.startsWith("/api/ventas/")) {
                Integer id = getIntIdFromPath(exchange, "/api/ventas/");
                if (id == null || id <= 0) {
                    throw new ControllerException.Validacion("El ID de venta no es válido.", "Debe ser un entero positivo.");
                }
                Principal principal = exigirAutenticacion(exchange, proveedor);
                long idNegocio = negocioAutorizado(exchange, principal);
                exigirPermisoSobreVenta(idNegocio, principal, id);

                if (path.endsWith("/pago")) {
                    if ("POST".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.registrarPagoAsync(idNegocio, id), 200);
                    } else {
                        throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                    }
                } else if (path.endsWith("/cancelar")) {
                    if ("POST".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.cancelarVentaAsync(idNegocio, id), 200);
                    } else {
                        throw new ControllerException.Validacion("Método no permitido.", "La ruta no admite este método HTTP.");
                    }
                } else {
      
                    if ("GET".equalsIgnoreCase(method)) {
                        handleAsync(exchange, servicio.buscarPorIdAsync(idNegocio, id), 200);
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

    private Carrito construirCarrito(long idNegocio, CreateVentaRequest req) {
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
        exigirNegocioOperativo(idNegocio);

        Carrito carrito = new Carrito(req.idCliente());
        for (ItemRequest itemReq : req.items()) {
            if (itemReq == null || itemReq.idProducto() <= 0 || itemReq.cantidad() <= 0) {
                throw new ControllerException.Validacion(
                        "Los datos del artículo no son válidos.",
                        "El producto y la cantidad deben ser valores positivos."
                );
            }
            Optional<Producto> prodOpt = productoRepository.buscarPorId(idNegocio, itemReq.idProducto());
            if (prodOpt.isEmpty()) {
                throw new ControllerException.NoEncontrado(
                        "Producto no encontrado.",
                        "No existe un producto con el ID solicitado."
                );
            }
            if (!prodOpt.get().puedeVenderse()) {
                throw new ControllerException.NoEncontrado(
                        "Producto no disponible.",
                        "El producto solicitado ya no está disponible para la venta."
                );
            }
            carrito.agregarItem(new ItemCarrito(prodOpt.get(), itemReq.cantidad()));
        }
        return carrito;
    }

    private void exigirNegocioOperativo(long idNegocio) {
        if (negocioRepository == null) {
            return;
        }
        Optional<Negocio> negocio = negocioRepository.buscarPorId(idNegocio);
        if (negocio.isEmpty() || !negocio.get().estaOperativo()) {
            throw new ControllerException.Conflicto(
                    "El negocio no está disponible.",
                    "El negocio se encuentra inactivo o dado de baja."
            );
        }
    }

    private void exigirPermisoSobreVenta(long idNegocio, Principal principal, int idVenta) {
        Optional<Venta> venta;
        try {
            venta = ventaRepository.buscarPorId(idNegocio, idVenta);
        } catch (IllegalStateException e) {
            throw new ControllerException.ServicioNoDisponible(
                    "No se pudo verificar el acceso a la venta.",
                    "Fallo al consultar el repositorio de ventas.");
        }
        if (venta.isEmpty()) {
            throw new ControllerException.NoEncontrado(
                    "Venta no encontrada.",
                    "No existe una venta con el ID solicitado.");
        }
        exigirPermisoSobreCliente(principal, venta.get().getIdCliente());
    }

    private IDescuentos resolverDescuento(DiscountInfo descuento) {
        return PoliticaDescuento.resolver(descuento == null ? null : descuento.tipo());
    }
}
