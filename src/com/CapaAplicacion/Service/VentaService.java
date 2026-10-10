package com.CapaAplicacion.Service;

import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.DetalleVenta;
import com.CapaDominio.Entidades.IDescuentos;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaAplicacion.Interfaces.IVentasService;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IClienteRepositorio;
import com.CapaAplicacion.Interfaces.IMetodoPagoRepositorio;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;
import com.CapaAplicacion.Service.PagoService;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaDominio.Excepciones.ReglaNegocioException;
import com.CapaDominio.Excepciones.StockInsuficienteException;
import com.CapaDominio.Excepciones.CarritoVacioException;
import com.CapaInfraestructura.Implementacion.RegistroExecutors;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class VentaService implements IVentasService {

    private final IVentaRepository ventaRepository;
    private final IProductoRepository productoRepository;
    private final IClienteRepositorio clienteRepository;
    private final ITransaccion transaccion;
    private final EventBus eventBus;
    private final ICarritoRepository carritoRepository;
    private final PagoService pagoService;
    private final IServiceExecutor executor;

    public VentaService(ITransaccion transaccion,
                        IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IClienteRepositorio clienteRepository,
                        IMetodoPagoRepositorio metodoPagoRepository,
                        EventBus eventBus,
                        ICarritoRepository carritoRepository) {
        this(transaccion, ventaRepository, productoRepository, clienteRepository,
                metodoPagoRepository, eventBus, carritoRepository, null);
    }

    public VentaService(ITransaccion transaccion,
                        IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IClienteRepositorio clienteRepository,
                        IMetodoPagoRepositorio metodoPagoRepository,
                        EventBus eventBus,
                        ICarritoRepository carritoRepository,
                        PagoService pagoService) {
        this.transaccion = transaccion;
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.clienteRepository = clienteRepository;
        this.eventBus = eventBus;
        this.carritoRepository = carritoRepository;
        this.pagoService = pagoService;
        this.executor = RegistroExecutors.obtener(TipoServicio.VENTA);
    }

    public VentaService(IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IMetodoPagoRepositorio metodoPagoRepository,
                        EventBus eventBus) {
        this(null, ventaRepository, productoRepository, null, metodoPagoRepository, eventBus, null);
    }

    public VentaService(ITransaccion transaccion,
                        IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IClienteRepositorio clienteRepository,
                        IMetodoPagoRepositorio metodoPagoRepository,
                        EventBus eventBus) {
        this(transaccion, ventaRepository, productoRepository, clienteRepository,
                metodoPagoRepository, eventBus, null);
    }

    public VentaService(IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IMetodoPagoRepositorio metodoPagoRepository,
                        EventBus eventBus,
                        ICarritoRepository carritoRepository) {
        this(null, ventaRepository, productoRepository, null, metodoPagoRepository, eventBus, carritoRepository);
    }

    @Override
    public CompletableFuture<Void> crearVentaAsync(final Carrito carrito,
                                                   final String tipoPago,
                                                   final IDescuentos descuento) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
            Carrito carritoOrigen = carrito;
            if (carritoRepository != null) {
                carritoOrigen = obtenerCarrito(carrito == null ? 0 : carrito.getIdCliente());
            persistirVentaAtomica(carritoOrigen, tipoPago, descuento);
                carritoRepository.eliminarPorClienteId(carritoOrigen.getIdCliente());
                return;
            }
            persistirVentaAtomica(carritoOrigen, tipoPago, descuento);
            }
        });
    }

    public CompletableFuture<Void> crearVentaAsync(final int idCliente,
                                                   final String tipoPago,
                                                   final IDescuentos descuento) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
            if (carritoRepository == null) {
                throw new ReglaNegocioException("El repositorio de carritos no está configurado.");
            }

            Carrito carrito = obtenerCarrito(idCliente);
            persistirVentaAtomica(carrito, tipoPago, descuento);
            carritoRepository.eliminarPorClienteId(idCliente);
            }
        });
    }

    private Carrito obtenerCarrito(int idCliente) {
        if (idCliente <= 0) {
            throw new ReglaNegocioException("El ID del cliente debe ser positivo.");
        }

        return carritoRepository.obtenerPorClienteId(idCliente)
                .filter(carrito -> carrito.getItems() != null && !carrito.getItems().isEmpty())
                .orElseThrow(() -> new CarritoVacioException(
                        "No existe un carrito con artículos para el cliente solicitado."));
    }

    private void persistirVentaAtomica(Carrito carrito, String tipoPago, IDescuentos descuento) {
        validarCarrito(carrito);
        TipoDePago tipoDePago = convertirTipoPago(tipoPago);
        Venta nuevaVenta = new Venta(carrito.getIdCliente(), tipoDePago);
        nuevaVenta.cargarDetallesDesdeCarrito(carrito, descuento);

        if (transaccion == null) {
            validarYActualizarStock(carrito);
            ventaRepository.guardar(nuevaVenta);
        } else {
            ejecutarTransaccion(connection -> {
                validarYActualizarStock(carrito, connection);
                ventaRepository.guardar(nuevaVenta, connection);
            });
        }

        carrito.vaciar();
        eventBus.publicar(new VentasEventos("CREADA", nuevaVenta));
    }

    @Override
    public CompletableFuture<ResultadoPago> crearVentaConPagoAsync(final Carrito carrito,
                                                                  final String tipoPago,
                                                                  final IDescuentos descuento,
                                                                  final String claveIdempotenciaSolicitud) {
        return executor.ejecutar(new java.util.concurrent.Callable<ResultadoPago>() {
            @Override
            public ResultadoPago call() {
                if (pagoService == null) {
                    throw new ReglaNegocioException("El servicio de pagos no está configurado.");
                }
                if (claveIdempotenciaSolicitud == null || claveIdempotenciaSolicitud.isBlank()) {
                    throw new ReglaNegocioException("El encabezado Idempotency-Key es obligatorio.");
                }
                if (claveIdempotenciaSolicitud.length() > 255) {
                    throw new ReglaNegocioException(
                            "El encabezado Idempotency-Key no puede superar 255 caracteres.");
                }

                validarCarrito(carrito);
                TipoDePago tipoDePago = convertirTipoPago(tipoPago);
                if (tipoDePago != TipoDePago.MERCADO_PAGO && tipoDePago != TipoDePago.STRIPE) {
                    throw new ReglaNegocioException(
                            "El checkout público solo admite Mercado Pago o Stripe.");
                }

                Venta ventaSolicitada = new Venta(
                        carrito.getIdCliente(), tipoDePago, claveIdempotenciaSolicitud);
                ventaSolicitada.cargarDetallesDesdeCarrito(carrito, descuento);
                Optional<Venta> ventaExistente =
                        ventaRepository.buscarPorClaveIdempotenciaSolicitud(claveIdempotenciaSolicitud);
                if (ventaExistente.isPresent()) {
                    validarMismaSolicitud(ventaExistente.get(), ventaSolicitada);
                    return pagoService.iniciarCheckout(ventaExistente.get());
                }

                pagoService.validarConfiguracionCheckout(tipoDePago);

                try {
                    guardarVentaDeCheckout(ventaSolicitada, carrito);
                } catch (RuntimeException exception) {
                    Optional<Venta> ventaConcurrente =
                            ventaRepository.buscarPorClaveIdempotenciaSolicitud(claveIdempotenciaSolicitud);
                    if (ventaConcurrente.isEmpty()) {
                        throw exception;
                    }
                    validarMismaSolicitud(ventaConcurrente.get(), ventaSolicitada);
                    return pagoService.iniciarCheckout(ventaConcurrente.get());
                }

                return pagoService.iniciarCheckout(ventaSolicitada);
            }
        });
    }

    private void guardarVentaDeCheckout(final Venta venta, final Carrito carrito) {
        if (transaccion == null) {
            throw new ReglaNegocioException(
                    "La creación idempotente de ventas requiere una transacción configurada.");
        }
        ejecutarTransaccion(new com.CapaAplicacion.Interfaces.OperacionTransaccional() {
            @Override
            public void ejecutar(Connection connection) {
                validarYActualizarStock(carrito, connection);
                try {
                    ventaRepository.guardar(venta, connection);
                } catch (java.sql.SQLException exception) {
                    throw new IllegalStateException(
                            "No se pudo guardar la venta dentro de la transacción.", exception);
                }
            }
        });
        eventBus.publicar(new VentasEventos("CREADA", venta));
    }

    private void validarMismaSolicitud(Venta existente, Venta solicitada) {
        if (existente.getIdCliente() != solicitada.getIdCliente()
                || existente.getTipoPago() != solicitada.getTipoPago()
                || existente.calculoTotal().compareTo(solicitada.calculoTotal()) != 0
                || !tienenMismosDetalles(existente, solicitada)) {
            throw new ReglaNegocioException(
                    "La clave Idempotency-Key ya fue utilizada con datos diferentes.");
        }
    }

    private boolean tienenMismosDetalles(Venta existente, Venta solicitada) {
        if (existente.getDetalles().size() != solicitada.getDetalles().size()) {
            return false;
        }
        for (DetalleVenta detalleSolicitado : solicitada.getDetalles()) {
            DetalleVenta detalleExistente = buscarDetalle(
                    existente.getDetalles(), detalleSolicitado.getIdProducto());
            if (detalleExistente == null
                    || detalleExistente.getCantidad() != detalleSolicitado.getCantidad()
                    || detalleExistente.getPrecioUnitario()
                    .compareTo(detalleSolicitado.getPrecioUnitario()) != 0) {
                return false;
            }
        }
        return true;
    }

    private DetalleVenta buscarDetalle(List<DetalleVenta> detalles, int idProducto) {
        for (DetalleVenta detalle : detalles) {
            if (detalle.getIdProducto() == idProducto) {
                return detalle;
            }
        }
        return null;
    }

    private void validarCarrito(Carrito carrito) {
        if (carrito == null || carrito.getItems() == null || carrito.getItems().isEmpty()) {
            throw new CarritoVacioException("El carrito no puede estar vacío para crear una venta.");
        }
    }

    private TipoDePago convertirTipoPago(String tipoPago) {
        if (tipoPago == null || tipoPago.isBlank()) {
            throw new ReglaNegocioException("Debe especificarse un tipo de pago.");
        }
        try {
            return TipoDePago.valueOf(tipoPago.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ReglaNegocioException("El tipo de pago indicado no es válido.");
        }
    }

    private void validarYActualizarStock(Carrito carrito) {
        validarYActualizarStock(carrito, null);
    }

    private void validarYActualizarStock(Carrito carrito, Connection connection) {
        for (ItemCarrito item : carrito.getItems().values()) {
            if (item == null || item.getProducto() == null) {
                throw new ReglaNegocioException("El carrito contiene un artículo inválido.");
            }
            Producto producto = item.getProducto();
            if (producto.getStockDisponible() < item.getCantidad()) {
                throw new StockInsuficienteException(
                        "No hay stock suficiente para el producto: " + producto.getNombre());
            }
        }
        for (ItemCarrito item : carrito.getItems().values()) {
            Producto producto = item.getProducto();
            producto.reducirStock(item.getCantidad());
            if (connection == null) {
                productoRepository.actualizar(producto.getId(), producto);
            } else {
                try {
                    productoRepository.actualizar(producto.getId(), producto, connection);
                } catch (java.sql.SQLException exception) {
                    throw new IllegalStateException("No se pudo actualizar el stock dentro de la transacción.", exception);
                }
            }
        }
    }

    private void ejecutarTransaccion(com.CapaAplicacion.Interfaces.OperacionTransaccional accion) {
        transaccion.ejecutarTransaccion(accion);
    }

    @Override
    public CompletableFuture<Optional<Venta>> buscarPorIdAsync(final int id) {
        return executor.ejecutar(new java.util.concurrent.Callable<Optional<Venta>>() {
            @Override
            public Optional<Venta> call() {
                return ventaRepository.buscarPorId(id);
            }
        });
    }


    @Override
    public CompletableFuture<ResultadoPago> registrarPagoAsync(final int idVenta) {
        return executor.ejecutar(new java.util.concurrent.Callable<ResultadoPago>() {
            @Override
            public ResultadoPago call() {
                if (pagoService == null) {
                    throw new ReglaNegocioException("El servicio de pagos no está configurado.");
                }
                Optional<Venta> venta = ventaRepository.buscarPorId(idVenta);
                if (venta.isEmpty()) {
                    throw new RecursoNoEncontradoException(
                            "No existe la venta solicitada para iniciar el pago.");
                }
                return pagoService.iniciarCheckout(venta.get());
            }
        });
    }

@Override
    public CompletableFuture<Void> cancelarVentaAsync(final int idVenta) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                try {
                    Optional<Venta> ventaOpt = ventaRepository.buscarPorId(idVenta);
                    if (!ventaOpt.isPresent()) {
                        throw new RuntimeException("No se puede cancelar. Venta no encontrada con ID: " + idVenta);
                    }
                    Venta venta = ventaOpt.get();

                    venta.Cancelar();

                    for (DetalleVenta i : venta.getDetalles()) {
                        Optional<Producto> productoOpt = productoRepository.buscarPorId(i.getIdProducto());
                        if (!productoOpt.isPresent()) {
                            throw new RuntimeException("Error crítico: El producto de la venta ya no existe.");
                        }

                        Producto producto = productoOpt.get();
                      producto.reintegrarStock(i.getCantidad());
                        productoRepository.actualizar(producto.getId(), producto);
                    }


                    ventaRepository.actualizar(idVenta, venta);

               
                    eventBus.publicar(new VentasEventos("CANCELADA", venta));

                } catch (Exception e) {
                    throw new RuntimeException("Error al cancelar la venta de forma atómica: " + e.getMessage(), e);
                }
            }
        });
    }
}