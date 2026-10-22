package com.CapaAplicacion.Service;

import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.DetalleVenta;
import com.CapaDominio.Entidades.EstadoVenta;
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
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.CarritoPersistido;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class VentaService implements IVentasService {
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(VentaService.class.getName());

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
                        EventBus eventBus,
                        ICarritoRepository carritoRepository) {
        this(transaccion, ventaRepository, productoRepository, clienteRepository,
                eventBus, carritoRepository, null);
    }

    public VentaService(ITransaccion transaccion,
                        IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IClienteRepositorio clienteRepository,
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
                        EventBus eventBus) {
        this(null, ventaRepository, productoRepository, null, eventBus, null);
    }

    public VentaService(ITransaccion transaccion,
                        IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        IClienteRepositorio clienteRepository,
                        EventBus eventBus) {
        this(transaccion, ventaRepository, productoRepository, clienteRepository,
                eventBus, null);
    }

    public VentaService(IVentaRepository ventaRepository,
                        IProductoRepository productoRepository,
                        EventBus eventBus,
                        ICarritoRepository carritoRepository) {
        this(null, ventaRepository, productoRepository, null, eventBus, carritoRepository);
    }

    @Override
    public CompletableFuture<Void> crearVentaAsync(final long idNegocio,
                                                   final Carrito carrito,
                                                   final String tipoPago,
                                                   final IDescuentos descuento) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                if (carrito == null) {
                    throw new ReglaNegocioException("El carrito es obligatorio para crear la venta.");
                }
                persistirVentaAtomica(idNegocio, carrito, tipoPago, descuento);
                if (carritoRepository != null) {
                    carritoRepository.eliminar(idNegocio, carrito.getIdCliente());
                }
            }
        });
    }

    public CompletableFuture<Void> crearVentaAsync(final long idNegocio,
                                                   final int idCliente,
                                                   final String tipoPago,
                                                   final IDescuentos descuento) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                if (carritoRepository == null) {
                    throw new ReglaNegocioException("El repositorio de carritos no está configurado.");
                }

                Carrito carrito = obtenerCarrito(idNegocio, idCliente);
                persistirVentaAtomica(idNegocio, carrito, tipoPago, descuento);
                carritoRepository.eliminar(idNegocio, idCliente);
            }
        });
    }

    private Carrito obtenerCarrito(long idNegocio, int idCliente) {
        if (idCliente <= 0) {
            throw new ReglaNegocioException("El ID del cliente debe ser positivo.");
        }

        CarritoPersistido persistido = carritoRepository.obtener(idNegocio, idCliente)
                .filter(carrito -> carrito.cantidades() != null && !carrito.cantidades().isEmpty())
                .orElseThrow(() -> new CarritoVacioException(
                        "No existe un carrito con artículos para el cliente solicitado."));

        Carrito carrito = new Carrito(idCliente);
        for (java.util.Map.Entry<Integer, Integer> entrada : persistido.cantidades().entrySet()) {
            Producto producto = productoRepository.buscarPorId(idNegocio, entrada.getKey())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "El producto del carrito ya no está disponible: " + entrada.getKey()));
            if (!producto.puedeVenderse()) {
                throw new RecursoNoEncontradoException(
                        "El producto del carrito ya no está disponible: " + entrada.getKey());
            }
            carrito.agregarItem(new ItemCarrito(producto, entrada.getValue()));
        }
        if (carrito.getItems().isEmpty()) {
            throw new CarritoVacioException(
                    "No existe un carrito con artículos para el cliente solicitado.");
        }
        return carrito;
    }

    private void persistirVentaAtomica(long idNegocio, Carrito carrito, String tipoPago, IDescuentos descuento) {
        validarCarrito(carrito);
        validarClienteDeNegocio(idNegocio, carrito.getIdCliente());
        TipoDePago tipoDePago = convertirTipoPago(tipoPago);
        if (tipoDePago == TipoDePago.TARJETA_CREDITO) {
            throw new ReglaNegocioException(
                    "El pago con tarjeta de crédito no está disponible. Use Mercado Pago o Stripe.");
        }
        Venta nuevaVenta = new Venta(carrito.getIdCliente(), tipoDePago);
        nuevaVenta.setIdNegocio(idNegocio);
        nuevaVenta.cargarDetallesDesdeCarrito(carrito, descuento);

        if (transaccion == null) {
            throw new IllegalStateException(
                    "La creación de ventas requiere una transacción configurada.");
        }
        final long negocioTx = idNegocio;
        ejecutarTransaccion(connection -> {
            validarYActualizarStock(negocioTx, carrito, connection);
            ventaRepository.guardar(nuevaVenta, connection);
        });

        carrito.vaciar();
        eventBus.publicar(new VentasEventos("CREADA", nuevaVenta));
    }

    private void validarClienteDeNegocio(long idNegocio, int idCliente) {
        if (idNegocio <= 0) {
            throw new ReglaNegocioException("El negocio de la venta no es válido.");
        }
        if (clienteRepository == null) {
            return;
        }
        if (clienteRepository.buscarPorId(idNegocio, idCliente).isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "El cliente no pertenece al negocio indicado.");
        }
    }

    @Override
    public CompletableFuture<ResultadoPago> crearVentaConPagoAsync(final long idNegocio,
                                                                   final Carrito carrito,
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
                validarClienteDeNegocio(idNegocio, carrito.getIdCliente());
                TipoDePago tipoDePago = convertirTipoPago(tipoPago);
                if (tipoDePago != TipoDePago.MERCADO_PAGO && tipoDePago != TipoDePago.STRIPE) {
                    throw new ReglaNegocioException(
                            "El checkout público solo admite Mercado Pago o Stripe.");
                }

                Venta ventaSolicitada = new Venta(
                        carrito.getIdCliente(), tipoDePago, claveIdempotenciaSolicitud);
                ventaSolicitada.setIdNegocio(idNegocio);
                ventaSolicitada.cargarDetallesDesdeCarrito(carrito, descuento);
                Optional<Venta> ventaExistente =
                        ventaRepository.buscarPorClaveIdempotenciaSolicitud(idNegocio, claveIdempotenciaSolicitud);
                if (ventaExistente.isPresent()) {
                    validarMismaSolicitud(ventaExistente.get(), ventaSolicitada);
                    return pagoService.iniciarCheckout(ventaExistente.get());
                }

                pagoService.validarConfiguracionCheckout(idNegocio, tipoDePago);

                try {
                    guardarVentaDeCheckout(ventaSolicitada, carrito);
                } catch (RuntimeException exception) {
                    Optional<Venta> ventaConcurrente =
                            ventaRepository.buscarPorClaveIdempotenciaSolicitud(idNegocio, claveIdempotenciaSolicitud);
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
                validarYActualizarStock(venta.getIdNegocio(), carrito, connection);
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

    private void validarYActualizarStock(long idNegocio, Carrito carrito, Connection connection) {
        if (connection == null) {
            throw new IllegalStateException(
                    "La actualización de stock requiere una transacción configurada.");
        }
        List<ItemCarrito> items = new ArrayList<>(carrito.getItems().values());
        for (ItemCarrito item : items) {
            if (item == null || item.getProducto() == null) {
                throw new ReglaNegocioException("El carrito contiene un artículo inválido.");
            }
            Producto producto = item.getProducto();
            if (producto.getIdNegocio() != 0 && producto.getIdNegocio() != idNegocio) {
                throw new ReglaNegocioException(
                        "El producto no pertenece al negocio de la venta: " + producto.getNombre());
            }
        }
        items.sort(Comparator.comparingInt(item -> item.getProducto().getId()));
        for (ItemCarrito item : items) {
            Producto productoCarrito = item.getProducto();
            Producto producto;
            try {
                producto = productoRepository
                        .buscarPorIdParaUpdate(idNegocio, productoCarrito.getId(), connection)
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                "El producto ya no está disponible: " + productoCarrito.getNombre()));
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(
                        "No se pudo releer el stock dentro de la transacción.", exception);
            }
            if (!producto.puedeVenderse()) {
                throw new RecursoNoEncontradoException(
                        "El producto ya no está disponible: " + producto.getNombre());
            }
            if (producto.getStockDisponible() < item.getCantidad()) {
                throw new StockInsuficienteException(
                        "No hay stock suficiente para el producto: " + producto.getNombre());
            }
            producto.reducirStock(item.getCantidad());
            try {
                productoRepository.actualizar(idNegocio, producto.getId(), producto, connection);
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException("No se pudo actualizar el stock dentro de la transacción.", exception);
            }
        }
    }

    private void ejecutarTransaccion(com.CapaAplicacion.Interfaces.OperacionTransaccional accion) {
        transaccion.ejecutarTransaccion(accion);
    }

    @Override
    public CompletableFuture<Optional<Venta>> buscarPorIdAsync(final long idNegocio, final int id) {
        return executor.ejecutar(new java.util.concurrent.Callable<Optional<Venta>>() {
            @Override
            public Optional<Venta> call() {
                return ventaRepository.buscarPorId(idNegocio, id);
            }
        });
    }


    @Override
    public CompletableFuture<ResultadoPago> registrarPagoAsync(final long idNegocio, final int idVenta) {
        return executor.ejecutar(new java.util.concurrent.Callable<ResultadoPago>() {
            @Override
            public ResultadoPago call() {
                if (pagoService == null) {
                    throw new ReglaNegocioException("El servicio de pagos no está configurado.");
                }
                Optional<Venta> venta = ventaRepository.buscarPorId(idNegocio, idVenta);
                if (venta.isEmpty()) {
                    throw new RecursoNoEncontradoException(
                            "No existe la venta solicitada para iniciar el pago.");
                }
                return pagoService.iniciarCheckout(venta.get());
            }
        });
    }

    @Override
    public CompletableFuture<Void> cancelarVentaAsync(final long idNegocio, final int idVenta) {
        return executor.ejecutar(new Runnable() {
            @Override
            public void run() {
                cancelarVentaAtomica(idNegocio, idVenta);
            }
        });
    }

    private void cancelarVentaAtomica(final long idNegocio, final int idVenta) {
        if (transaccion == null) {
            throw new IllegalStateException(
                    "La cancelación de ventas requiere una transacción configurada.");
        }

        final boolean[] cancelada = {false};
        final boolean[] estadoNoPermitido = {false};
        final String[] mensajeEstado = {null};
        final Venta[] ventaCancelada = {null};

        ejecutarTransaccion(connection -> {
            Venta venta = buscarVentaParaUpdate(idNegocio, idVenta, connection);
            if (venta.getEstadoCompra() == EstadoVenta.CANCELADO) {
                return;
            }
            if (!venta.getEstadoCompra().puedeTransicionarA(EstadoVenta.CANCELADO)) {
                estadoNoPermitido[0] = true;
                mensajeEstado[0] = "La venta no se puede cancelar en su estado actual: "
                        + venta.getEstadoCompra() + ".";
                return;
            }
            venta.Cancelar();
            reintegrarStockDeVenta(idNegocio, venta, connection);
            actualizarVenta(idNegocio, idVenta, venta, connection);
            cancelada[0] = true;
            ventaCancelada[0] = venta;
        });

        if (estadoNoPermitido[0]) {
            throw new IllegalStateException(mensajeEstado[0]);
        }
        if (cancelada[0]) {
            eventBus.publicar(new VentasEventos("CANCELADA", ventaCancelada[0]));
        }
    }

    public int expirarVentasPendientes(int minutos, int cantidad) {
        if (minutos <= 0) {
            throw new IllegalArgumentException("Los minutos de expiración deben ser positivos.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de ventas a expirar debe ser positiva.");
        }
        if (transaccion == null) {
            throw new IllegalStateException(
                    "La expiración de ventas requiere una transacción configurada.");
        }

        final java.time.LocalDateTime limite = java.time.LocalDateTime.now().minusMinutes(minutos);
        List<Venta> candidatas = ventaRepository.buscarPendientesAnteriores(limite, cantidad);
        int expiradas = 0;
        for (Venta candidata : candidatas) {
            try {
                if (expirarVenta(candidata.getIdNegocio(), candidata.getId(), limite)) {
                    expiradas++;
                }
            } catch (RuntimeException exception) {
                LOGGER.log(java.util.logging.Level.WARNING,
                        "No se pudo expirar la venta " + candidata.getId()
                                + " del negocio " + candidata.getIdNegocio() + ".",
                        exception);
            }
        }
        return expiradas;
    }

    private boolean expirarVenta(final long idNegocio, final int idVenta,
                                 final java.time.LocalDateTime limite) {
        final boolean[] expirada = {false};
        final Venta[] ventaExpirada = {null};
        ejecutarTransaccion(connection -> {
            Optional<Venta> candidata = ventaRepository.buscarPorIdParaUpdate(
                    idNegocio, idVenta, connection);
            if (candidata.isEmpty()) {
                return;
            }
            Venta venta = candidata.get();
            if (venta.getEstadoCompra() != EstadoVenta.PENDIENTE
                    || venta.getFechaCompra() == null
                    || !venta.getFechaCompra().isBefore(limite)) {
                return;
            }
            venta.Expirar();
            reintegrarStockDeVenta(idNegocio, venta, connection);
            actualizarVenta(idNegocio, idVenta, venta, connection);
            expirada[0] = true;
            ventaExpirada[0] = venta;
        });
        if (expirada[0]) {
            eventBus.publicar(new VentasEventos("EXPIRADA", ventaExpirada[0]));
        }
        return expirada[0];
    }

    private Venta buscarVentaParaUpdate(long idNegocio, int idVenta, Connection connection) {
        try {
            return ventaRepository.buscarPorIdParaUpdate(idNegocio, idVenta, connection)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe la venta solicitada para cancelar."));
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(
                    "No se pudo releer la venta dentro de la transacción.", exception);
        }
    }

    private void reintegrarStockDeVenta(long idNegocio, Venta venta, Connection connection) {
        List<DetalleVenta> detalles = new ArrayList<>(venta.getDetalles());
        detalles.sort(Comparator.comparingInt(DetalleVenta::getIdProducto));
        for (DetalleVenta detalle : detalles) {
            Producto producto;
            try {
                producto = productoRepository
                        .buscarPorIdParaUpdate(idNegocio, detalle.getIdProducto(), connection)
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                "El producto de la venta ya no está disponible: "
                                        + detalle.getIdProducto()));
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(
                        "No se pudo releer el producto dentro de la transacción.", exception);
            }
            producto.reintegrarStock(detalle.getCantidad());
            try {
                productoRepository.actualizar(idNegocio, producto.getId(), producto, connection);
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(
                        "No se pudo actualizar el stock dentro de la transacción.", exception);
            }
        }
    }

    private void actualizarVenta(long idNegocio, int idVenta, Venta venta, Connection connection) {
        try {
            ventaRepository.actualizar(idNegocio, idVenta, venta, connection);
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(
                    "No se pudo actualizar la venta dentro de la transacción.", exception);
        }
    }
}