package com.CapaPresentacion.Controllers;

import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaAplicacion.Service.CarritoService;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaPresentacion.Utilidades.ResolutorNegocio;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class CarritoController extends BaseController {
    private static final int LIMITE_CONSULTA = 240;
    private static final int LIMITE_MODIFICACION = 120;

    private final CarritoService servicio;
    private final IProveedorTokenAcceso proveedor;

    public record AgregarItemRequest(int idProducto, int cantidad) {
    }

    public record CarritoResponse(int idCliente, List<ItemCarritoResponse> items, BigDecimal total) {
        public static CarritoResponse desde(Carrito carrito) {
            List<ItemCarrito> ordenados = new ArrayList<>(carrito.getItems().values());
            ordenados.sort(Comparator.comparingInt(item -> item.getProducto().getId()));
            List<ItemCarritoResponse> items = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (ItemCarrito item : ordenados) {
                BigDecimal subtotal = item.getSubtotalBruto();
                total = total.add(subtotal);
                items.add(new ItemCarritoResponse(
                        item.getProducto().getId(),
                        item.getProducto().getNombre(),
                        item.getProducto().getPrecio(),
                        item.getCantidad(),
                        subtotal));
            }
            return new CarritoResponse(carrito.getIdCliente(), items, total);
        }

        public record ItemCarritoResponse(int idProducto, String nombre, BigDecimal precio,
                                          int cantidad, BigDecimal subtotal) {
        }
    }

    public CarritoController(CarritoService servicio, IProveedorTokenAcceso proveedor,
                             ResolutorNegocio resolutorNegocio) {
        super(resolutorNegocio);
        if (servicio == null || proveedor == null) {
            throw new IllegalArgumentException("Las dependencias del controlador de carrito son obligatorias.");
        }
        this.servicio = servicio;
        this.proveedor = proveedor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange)) {
            return;
        }

        try {
            Principal principal = exigirAutenticacion(exchange, proveedor);
            long idNegocio = negocioAutorizado(exchange, principal);
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/carrito")) {
                manejarCarrito(exchange, principal, idNegocio, method);
            } else if (path.equals("/api/carrito/items")) {
                manejarItems(exchange, principal, idNegocio, method);
            } else if (path.startsWith("/api/carrito/items/")) {
                manejarItem(exchange, principal, idNegocio, method);
            } else {
                throw new ControllerException.NoEncontrado(
                        "Ruta no encontrada.",
                        "No existe un recurso para la URL solicitada.");
            }
        } catch (ControllerException e) {
            manejarError(exchange, e);
        } catch (IOException e) {
            sendError(exchange, 503, new ControllerException.ServicioNoDisponible(
                    "No fue posible responder al cliente.",
                    "Problema de red o comunicación.").respuesta());
        } catch (Exception e) {
            manejarError(exchange, e);
        }
    }

    private void manejarCarrito(HttpExchange exchange, Principal principal, long idNegocio,
                                String method) {
        if ("GET".equalsIgnoreCase(method)) {
            if (limitarSolicitud(exchange, principal, "carrito-consulta", LIMITE_CONSULTA)) {
                return;
            }
            handleAsync(exchange, mapear(servicio.consultarAsync(idNegocio, principal.idCliente())), 200);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            if (limitarSolicitud(exchange, principal, "carrito-modificacion", LIMITE_MODIFICACION)) {
                return;
            }
            handleAsync(exchange, servicio.vaciarAsync(idNegocio, principal.idCliente()), 204);
            return;
        }
        throw new ControllerException.Validacion(
                "Método no permitido.", "La ruta no admite este método HTTP.");
    }

    private void manejarItems(HttpExchange exchange, Principal principal, long idNegocio,
                              String method) throws IOException {
        if (!"POST".equalsIgnoreCase(method)) {
            throw new ControllerException.Validacion(
                    "Método no permitido.", "La ruta no admite este método HTTP.");
        }
        if (limitarSolicitud(exchange, principal, "carrito-modificacion", LIMITE_MODIFICACION)) {
            return;
        }
        AgregarItemRequest request = parseRequestBody(exchange, AgregarItemRequest.class);
        if (request == null || request.idProducto() <= 0 || request.cantidad() <= 0) {
            throw new ControllerException.Validacion(
                    "El artículo no es válido.",
                    "Debe indicar un producto y una cantidad positivos.");
        }
        handleAsync(exchange, mapear(servicio.agregarAsync(
                idNegocio, principal.idCliente(), request.idProducto(), request.cantidad())), 200);
    }

    private void manejarItem(HttpExchange exchange, Principal principal, long idNegocio,
                             String method) {
        if (!"DELETE".equalsIgnoreCase(method)) {
            throw new ControllerException.Validacion(
                    "Método no permitido.", "La ruta no admite este método HTTP.");
        }
        if (limitarSolicitud(exchange, principal, "carrito-modificacion", LIMITE_MODIFICACION)) {
            return;
        }
        Integer idProducto = getIntIdFromPath(exchange, "/api/carrito/items/");
        if (idProducto == null || idProducto <= 0) {
            throw new ControllerException.Validacion(
                    "El producto no es válido.",
                    "El ID del producto debe ser un entero positivo.");
        }
        handleAsync(exchange, mapear(servicio.eliminarItemAsync(
                idNegocio, principal.idCliente(), idProducto)), 200);
    }

    private CompletableFuture<CarritoResponse> mapear(CompletableFuture<Carrito> futuro) {
        return futuro.thenApply(new Function<Carrito, CarritoResponse>() {
            @Override
            public CarritoResponse apply(Carrito carrito) {
                return CarritoResponse.desde(carrito);
            }
        });
    }
}
