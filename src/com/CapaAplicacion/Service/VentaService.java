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
import com.CapaDominio.Entidades.MetodoPago;
import com.CapaDominio.Excepciones.PagoInvalidoException;
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
import java.util.function.Supplier;

public class VentaService implements IVentasService {

    private final IVentaRepository ventaRepository;
    private final IProductoRepository productoRepository;
    private final IClienteRepositorio clienteRepository;
    private final IMetodoPagoRepositorio metodoPagoRepository;
    private final ITransaccion transaccion;
    private final EventBus eventBus;
    private final ICarritoRepository carritoRepository;
    private final IServiceExecutor executor;

    public VentaService(ITransaccion transaccion,
                        IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IClienteRepositorio clienteRepository,
                        IMetodoPagoRepositorio metodoPagoRepository,
                        EventBus eventBus,
                        ICarritoRepository carritoRepository) {
        this.transaccion = transaccion;
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.clienteRepository = clienteRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.eventBus = eventBus;
        this.carritoRepository = carritoRepository;
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
                persistirVentaAtomica(carritoOrigen, tipoPago, descuento, true);
                carritoRepository.eliminarPorClienteId(carritoOrigen.getIdCliente());
                return;
            }
            persistirVentaAtomica(carritoOrigen, tipoPago, descuento, false);
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
            persistirVentaAtomica(carrito, tipoPago, descuento, true);
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

    private void persistirVentaAtomica(Carrito carrito,
                                       String tipoPago,
                                       IDescuentos descuento,
                                       boolean procesarPago) {
        validarCarrito(carrito);
        TipoDePago tipoDePago = convertirTipoPago(tipoPago);
        Venta nuevaVenta = new Venta(carrito.getIdCliente(), tipoDePago);
        nuevaVenta.cargarDetallesDesdeCarrito(carrito, descuento);

        if (transaccion == null) {
            validarYActualizarStock(carrito);
            if (procesarPago) {
                procesarPago(nuevaVenta, null);
            }
            ventaRepository.guardar(nuevaVenta);
        } else {
            ejecutarTransaccion(connection -> {
                validarYActualizarStock(carrito, connection);
                if (procesarPago) {
                    procesarPago(nuevaVenta, connection);
                }
                ventaRepository.guardar(nuevaVenta, connection);
            });
        }

        if (!procesarPago) {
            carrito.vaciar();
        }
        eventBus.publicar(new VentasEventos("CREADA", nuevaVenta));
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

    private void procesarPago(Venta venta, Connection connection) {
        Optional<MetodoPago> metodoPagoOpt = metodoPagoRepository.buscarPorIdCliente(venta.getIdCliente());
        MetodoPago metodoPago = metodoPagoOpt.orElseThrow(() -> new PagoInvalidoException(
                "El cliente no posee un método de pago registrado."));
        metodoPago.debitar(venta.calculoTotal());
        if (connection == null) {
            metodoPagoRepository.actualizar(metodoPago.getIdCliente(), metodoPago);
        } else {
            metodoPagoRepository.actualizar(metodoPago.getIdCliente(), metodoPago, connection);
        }
        venta.Pagar();
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
    public CompletableFuture<Void> registrarPagoAsync(final int idVenta) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                try {
                    Optional<Venta> ventaOpt = ventaRepository.buscarPorId(idVenta);
                    if (!ventaOpt.isPresent()) {
                        throw new RuntimeException("No se puede pagar. Venta no encontrada con ID: " + idVenta);
                    }
                    Venta venta = ventaOpt.get();

                    Optional<MetodoPago> metodoPagoOpt = metodoPagoRepository.buscarPorIdCliente(venta.getIdCliente());
                    if (!metodoPagoOpt.isPresent()) {
                        throw new RuntimeException("El cliente no posee un método de pago registrado para realizar el pago.");
                    }

                    MetodoPago metodoPago = metodoPagoOpt.get();
                    BigDecimal totalVenta = venta.calculoTotal();
                    metodoPago.debitar(totalVenta);

                    venta.Pagar();

                    metodoPagoRepository.actualizar(metodoPago.getIdCliente(), metodoPago);
                    ventaRepository.actualizar(idVenta, venta);

                    eventBus.publicar(new VentasEventos("PAGADA", venta));
                    
                } catch (Exception e) {
                    throw new RuntimeException("Error al registrar el pago: " + e.getMessage(), e);
                }
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