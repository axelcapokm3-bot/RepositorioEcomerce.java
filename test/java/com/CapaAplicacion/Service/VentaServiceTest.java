package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.Interfaces.CarritoPersistido;
import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.ICarritoRepository;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import com.CapaDominio.Excepciones.PasarelaNoDisponibleException;
import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import com.CapaDominio.Excepciones.ReglaNegocioException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {
    private static final long NEGOCIO = 1L;

    @Mock
    private IVentaRepository ventaRepository;

    @Mock
    private IProductoRepository productoRepository;

    @Mock
    private EventBus eventBus;

    @Mock
    private PagoService pagoService;

    @Mock
    private ICarritoRepository carritoRepository;

    private VentaService service;
    private final Connection conexionTx = mock(Connection.class);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new VentaService(
                transaccionDirecta(),
                ventaRepository,
                productoRepository,
                null,
                eventBus,
                null,
                pagoService);
    }

    private ITransaccion transaccionDirecta() {
        return operacion -> {
            try {
                operacion.ejecutar(conexionTx);
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        };
    }

    private Producto producto(int id, int stock, BigDecimal precio) {
        Producto producto = new Producto("Producto " + id, precio, stock, 0, id, CategoriaProducto.ELECTRONICA);
        producto.setIdNegocio(NEGOCIO);
        return producto;
    }

    private Carrito carritoValido() {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 10, new BigDecimal("100")), 2));
        carrito.agregarItem(new ItemCarrito(producto(2, 10, new BigDecimal("50")), 1));
        return carrito;
    }

    private Venta ventaPendienteConDetalles() {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 10, new BigDecimal("100")), 2));
        Venta venta = new Venta(1, TipoDePago.EFECTIVO);
        venta.setIdNegocio(NEGOCIO);
        venta.cargarDetallesDesdeCarrito(carrito, null);
        venta.setId(7);
        return venta;
    }

    // ------------------------------------------------------------------
    // CREAR VENTA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("crearVentaAsync: reduce stock, guarda venta, vacía carrito y publica evento")
    void crearVentaExitosa() throws Exception {
        Carrito carrito = carritoValido();
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(1), any(Connection.class)))
                .thenReturn(Optional.of(producto(1, 10, new BigDecimal("100"))));
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(2), any(Connection.class)))
                .thenReturn(Optional.of(producto(2, 10, new BigDecimal("50"))));

        service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get();

        assertEquals(0, carrito.getItems().size(), "El carrito debe vaciarse tras crear la venta");

        verify(productoRepository).actualizar(eq(NEGOCIO), eq(1), any(Producto.class), any(Connection.class));
        verify(productoRepository).actualizar(eq(NEGOCIO), eq(2), any(Producto.class), any(Connection.class));

        ArgumentCaptor<Venta> ventaCaptor = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).guardar(ventaCaptor.capture(), any(Connection.class));
        Venta guardada = ventaCaptor.getValue();
        assertEquals(EstadoVenta.PENDIENTE, guardada.getEstadoCompra());
        assertEquals(2, guardada.getDetalles().size());

        ArgumentCaptor<VentasEventos> eventoCaptor = ArgumentCaptor.forClass(VentasEventos.class);
        verify(eventBus).publicar(eventoCaptor.capture());
        assertEquals("CREADA", eventoCaptor.getValue().getTipoOperacion());
        assertNotNull(eventoCaptor.getValue().getVentaSnapshot());
    }

    @Test
    @DisplayName("crearVentaAsync(idCliente): usa el carrito persistido del negocio y lo elimina")
    void crearVentaDesdeCarritoPersistido() throws Exception {
        VentaService serviceConCarrito = new VentaService(
                transaccionDirecta(),
                ventaRepository,
                productoRepository,
                null,
                eventBus,
                carritoRepository,
                pagoService);

        Map<Integer, Integer> cantidades = new LinkedHashMap<>();
        cantidades.put(1, 2);
        when(carritoRepository.obtener(NEGOCIO, 1))
                .thenReturn(Optional.of(new CarritoPersistido(1, cantidades)));
        when(productoRepository.buscarPorId(NEGOCIO, 1))
                .thenReturn(Optional.of(producto(1, 10, new BigDecimal("100"))));
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(1), any(Connection.class)))
                .thenReturn(Optional.of(producto(1, 10, new BigDecimal("100"))));

        serviceConCarrito.crearVentaAsync(NEGOCIO, 1, "EFECTIVO", null).get();

        verify(carritoRepository).obtener(NEGOCIO, 1);
        verify(carritoRepository).eliminar(NEGOCIO, 1);
        verify(ventaRepository).guardar(any(Venta.class), any(Connection.class));
    }

    @Test
    @DisplayName("crearVentaAsync: rechaza carrito vacío")
    void crearVentaCarritoVacio() {
        assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(NEGOCIO, new Carrito(1), "EFECTIVO", null).get());
        verify(ventaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("crearVentaAsync: rechaza stock insuficiente")
    void crearVentaStockInsuficiente() throws Exception {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 1, new BigDecimal("100")), 5));
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(1), any(Connection.class)))
                .thenReturn(Optional.of(producto(1, 1, new BigDecimal("100"))));

        assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get());
        verify(ventaRepository, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("crearVentaAsync: valida contra el stock fresco releído dentro de la transacción")
    void crearVentaValidaStockFrescoDentroDeLaTransaccion() throws Exception {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 10, new BigDecimal("100")), 5));
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(1), any(Connection.class)))
                .thenReturn(Optional.of(producto(1, 1, new BigDecimal("100"))));

        assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get());

        verify(ventaRepository, never()).guardar(any(), any());
        verify(productoRepository, never())
                .actualizar(anyLong(), anyInt(), any(Producto.class), any(Connection.class));
    }

    @Test
    @DisplayName("crearVentaAsync: producto inexistente en la relectura no descuenta stock")
    void crearVentaProductoInexistenteEnRelectura() throws Exception {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 10, new BigDecimal("100")), 2));
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(1), any(Connection.class)))
                .thenReturn(Optional.empty());

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get());

        assertInstanceOf(RecursoNoEncontradoException.class, excepcion.getCause());
        verify(ventaRepository, never()).guardar(any(), any());
        verify(productoRepository, never())
                .actualizar(anyLong(), anyInt(), any(Producto.class), any(Connection.class));
    }

    @Test
    @DisplayName("crearVentaAsync: rechaza tipo de pago inválido")
    void crearVentaTipoPagoInvalido() {
        assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(NEGOCIO, carritoValido(), "DESCONOCIDO", null).get());
        verify(ventaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("crearVentaConPagoAsync: reutiliza venta para la misma clave y el mismo contenido")
    void crearVentaConPagoReutilizaVentaExistente() throws Exception {
        String claveSolicitud = "solicitud-venta-1";
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 10, new BigDecimal("100")), 2));
        Venta ventaExistente = new Venta(1, TipoDePago.MERCADO_PAGO, claveSolicitud);
        ventaExistente.setIdNegocio(NEGOCIO);
        ventaExistente.cargarDetallesDesdeCarrito(carrito, null);
        ventaExistente.setId(7);
        ResultadoPago resultadoEsperado = ResultadoPago.pendiente(
                "7",
                new BigDecimal("200"),
                "MERCADO_PAGO",
                "https://checkout.example/7",
                "order-7",
                "{}");

        when(ventaRepository.buscarPorClaveIdempotenciaSolicitud(NEGOCIO, claveSolicitud))
                .thenReturn(Optional.of(ventaExistente));
        when(pagoService.iniciarCheckout(ventaExistente)).thenReturn(resultadoEsperado);

        ResultadoPago resultado = service.crearVentaConPagoAsync(
                NEGOCIO, carrito, "MERCADO_PAGO", null, claveSolicitud).get();

        assertSame(resultadoEsperado, resultado);
        verify(pagoService).iniciarCheckout(ventaExistente);
        verify(ventaRepository, never()).guardar(any(), any());
        verify(productoRepository, never()).actualizar(anyLong(), anyInt(), any(Producto.class), any());
    }

    @Test
    @DisplayName("crearVentaConPagoAsync: resuelve la carrera de idempotencia releyendo la venta")
    void crearVentaConPagoResuelveCarreraDeIdempotencia() throws Exception {
        String claveSolicitud = "solicitud-carrera";
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 10, new BigDecimal("100")), 2));
        Venta ventaExistente = new Venta(1, TipoDePago.MERCADO_PAGO, claveSolicitud);
        ventaExistente.setIdNegocio(NEGOCIO);
        ventaExistente.cargarDetallesDesdeCarrito(carrito, null);
        ventaExistente.setId(9);
        ResultadoPago resultadoEsperado = ResultadoPago.pendiente(
                "9",
                new BigDecimal("200"),
                "MERCADO_PAGO",
                "https://checkout.example/9",
                "order-9",
                "{}");

        when(ventaRepository.buscarPorClaveIdempotenciaSolicitud(NEGOCIO, claveSolicitud))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ventaExistente));
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(1), any(Connection.class)))
                .thenReturn(Optional.of(producto(1, 10, new BigDecimal("100"))));
        doThrow(new ReglaNegocioException("La clave Idempotency-Key ya fue utilizada para este negocio."))
                .when(ventaRepository).guardar(any(Venta.class), any(Connection.class));
        when(pagoService.iniciarCheckout(ventaExistente)).thenReturn(resultadoEsperado);

        ResultadoPago resultado = service.crearVentaConPagoAsync(
                NEGOCIO, carrito, "MERCADO_PAGO", null, claveSolicitud).get();

        assertSame(resultadoEsperado, resultado);
        verify(ventaRepository, times(2))
                .buscarPorClaveIdempotenciaSolicitud(NEGOCIO, claveSolicitud);
    }

    @Test
    @DisplayName("crearVentaConPagoAsync: rechaza clave existente con contenido diferente")
    void crearVentaConPagoRechazaContenidoDiferente() throws Exception {
        String claveSolicitud = "solicitud-venta-1";
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 10, new BigDecimal("100")), 2));
        Venta ventaExistente = new Venta(1, TipoDePago.MERCADO_PAGO, claveSolicitud);
        ventaExistente.setIdNegocio(NEGOCIO);
        Carrito carritoExistente = new Carrito(1);
        carritoExistente.agregarItem(new ItemCarrito(
                producto(1, 10, new BigDecimal("100")), 1));
        ventaExistente.cargarDetallesDesdeCarrito(carritoExistente, null);
        ventaExistente.setId(7);
        when(ventaRepository.buscarPorClaveIdempotenciaSolicitud(NEGOCIO, claveSolicitud))
                .thenReturn(Optional.of(ventaExistente));

        assertThrows(ExecutionException.class,
                () -> service.crearVentaConPagoAsync(
                        NEGOCIO, carrito, "MERCADO_PAGO", null, claveSolicitud).get());

        verify(pagoService, never()).iniciarCheckout(any());
        verify(ventaRepository, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("crearVentaConPagoAsync: exige transacción para guardar una venta nueva")
    void crearVentaConPagoExigeTransaccion() {
        service = new VentaService(
                null,
                ventaRepository,
                productoRepository,
                null,
                eventBus,
                null,
                pagoService);
        when(ventaRepository.buscarPorClaveIdempotenciaSolicitud(NEGOCIO, "solicitud-nueva"))
                .thenReturn(Optional.empty());

        assertThrows(ExecutionException.class,
                () -> service.crearVentaConPagoAsync(
                        NEGOCIO, carritoValido(), "MERCADO_PAGO", null, "solicitud-nueva").get());

        verify(productoRepository, never()).actualizar(anyLong(), anyInt(), any(Producto.class));
        verify(ventaRepository, never()).guardar(any());
        verify(pagoService, never()).iniciarCheckout(any());
    }

    @Test
    @DisplayName("crearVentaConPagoAsync: valida la pasarela antes de guardar venta y descontar stock")
    void crearVentaConPagoSinConfiguracionNoGuardaNiDescuentaStock() throws Exception {
        Carrito carrito = carritoValido();
        doThrow(new PasarelaNoDisponibleException("Falta configurar la pasarela."))
                .when(pagoService).validarConfiguracionCheckout(NEGOCIO, TipoDePago.MERCADO_PAGO);
        when(ventaRepository.buscarPorClaveIdempotenciaSolicitud(NEGOCIO, "solicitud-nueva"))
                .thenReturn(Optional.empty());

        assertThrows(ExecutionException.class,
                () -> service.crearVentaConPagoAsync(
                        NEGOCIO, carrito, "MERCADO_PAGO", null, "solicitud-nueva").get());

        assertEquals(2, carrito.getItems().size());
        verify(ventaRepository, never()).guardar(any(), any());
        verify(productoRepository, never()).actualizar(anyLong(), anyInt(), any(Producto.class), any());
        verify(pagoService, never()).iniciarCheckout(any());
    }

    @Test
    @DisplayName("crearVentaConPagoAsync: rechaza claves vacías y mayores al límite")
    void crearVentaConPagoValidaClaveSolicitud() {
        assertThrows(ExecutionException.class,
                () -> service.crearVentaConPagoAsync(
                        NEGOCIO, carritoValido(), "MERCADO_PAGO", null, " ").get());
        assertThrows(ExecutionException.class,
                () -> service.crearVentaConPagoAsync(
                        NEGOCIO, carritoValido(), "MERCADO_PAGO", null, "k".repeat(256)).get());

        verify(ventaRepository, never()).buscarPorClaveIdempotenciaSolicitud(anyLong(), anyString());
    }

    // ------------------------------------------------------------------
    // PAGO DE VENTA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("registrarPagoAsync: inicia checkout sin aprobar la venta")
    void registrarPagoExitoso() throws Exception {
        Venta venta = ventaPendienteConDetalles();
        ResultadoPago resultadoEsperado = ResultadoPago.pendiente(
                "7",
                new BigDecimal("200"),
                "MERCADO_PAGO",
                "https://checkout.example/7",
                "order-7",
                "{}");

        when(ventaRepository.buscarPorId(NEGOCIO, 7)).thenReturn(Optional.of(venta));
        when(pagoService.iniciarCheckout(venta)).thenReturn(resultadoEsperado);

        ResultadoPago resultado = service.registrarPagoAsync(NEGOCIO, 7).get();

        assertSame(resultadoEsperado, resultado);
        assertEquals(EstadoVenta.PENDIENTE, venta.getEstadoCompra());
        verify(pagoService).iniciarCheckout(venta);
        verify(ventaRepository, never()).actualizar(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("registrarPagoAsync: rechaza venta inexistente")
    void registrarPagoVentaInexistente() {
        when(ventaRepository.buscarPorId(NEGOCIO, 99)).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class, () -> service.registrarPagoAsync(NEGOCIO, 99).get());
        verify(eventBus, never()).publicar(any());
    }

    @Test
    @DisplayName("registrarPagoAsync: falla si el servicio de pagos no está configurado")
    void registrarPagoSinServicioDePagos() {
        service = new VentaService(
                null,
                ventaRepository,
                productoRepository,
                null,
                eventBus,
                null,
                null);

        assertThrows(ExecutionException.class, () -> service.registrarPagoAsync(NEGOCIO, 7).get());
    }

    // ------------------------------------------------------------------
    // CANCELAR VENTA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelarVentaAsync: reintegra stock y cancela una venta pendiente")
    void cancelarVentaExitosa() throws Exception {
        Carrito carrito = new Carrito(1);
        Producto p = producto(1, 5, new BigDecimal("100"));
        carrito.agregarItem(new ItemCarrito(p, 2));

        Venta venta = new Venta(1, TipoDePago.EFECTIVO);
        venta.setIdNegocio(NEGOCIO);
        venta.cargarDetallesDesdeCarrito(carrito, null);
        venta.setId(7);

        when(ventaRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(7), any(Connection.class)))
                .thenReturn(Optional.of(venta));
        when(productoRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(1), any(Connection.class)))
                .thenReturn(Optional.of(p));

        service.cancelarVentaAsync(NEGOCIO, 7).get();

        assertEquals(EstadoVenta.CANCELADO, venta.getEstadoCompra());
        assertEquals(7, p.getStockDisponible());
        verify(productoRepository).actualizar(eq(NEGOCIO), eq(1), any(Producto.class), eq(conexionTx));
        verify(ventaRepository).actualizar(NEGOCIO, 7, venta, conexionTx);

        ArgumentCaptor<VentasEventos> eventoCaptor = ArgumentCaptor.forClass(VentasEventos.class);
        verify(eventBus).publicar(eventoCaptor.capture());
        assertEquals("CANCELADA", eventoCaptor.getValue().getTipoOperacion());
    }

    @Test
    @DisplayName("cancelarVentaAsync: venta inexistente responde RecursoNoEncontrado")
    void cancelarVentaInexistente() throws Exception {
        when(ventaRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(99), any(Connection.class)))
                .thenReturn(Optional.empty());

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.cancelarVentaAsync(NEGOCIO, 99).get());

        assertInstanceOf(RecursoNoEncontradoException.class, excepcion.getCause());
        verify(ventaRepository, never()).actualizar(anyLong(), anyInt(), any(Venta.class), any(Connection.class));
        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("cancelarVentaAsync: una venta ya cancelada no reintegra stock de nuevo")
    void cancelarVentaYaCanceladaEsIdempotente() throws Exception {
        Venta venta = ventaPendienteConDetalles();
        venta.Cancelar();
        when(ventaRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(7), any(Connection.class)))
                .thenReturn(Optional.of(venta));

        service.cancelarVentaAsync(NEGOCIO, 7).get();

        assertEquals(EstadoVenta.CANCELADO, venta.getEstadoCompra());
        verify(productoRepository, never())
                .buscarPorIdParaUpdate(anyLong(), anyInt(), any(Connection.class));
        verify(productoRepository, never())
                .actualizar(anyLong(), anyInt(), any(Producto.class), any(Connection.class));
        verify(ventaRepository, never()).actualizar(anyLong(), anyInt(), any(Venta.class), any(Connection.class));
        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("cancelarVentaAsync: una venta pagada no se puede cancelar")
    void cancelarVentaPagadaNoEsPermitida() throws Exception {
        Venta venta = ventaPendienteConDetalles();
        venta.Pagar();
        when(ventaRepository.buscarPorIdParaUpdate(eq(NEGOCIO), eq(7), any(Connection.class)))
                .thenReturn(Optional.of(venta));

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> service.cancelarVentaAsync(NEGOCIO, 7).get());

        assertInstanceOf(IllegalStateException.class, excepcion.getCause());
        assertEquals(EstadoVenta.PAGADO, venta.getEstadoCompra());
        verify(productoRepository, never())
                .buscarPorIdParaUpdate(anyLong(), anyInt(), any(Connection.class));
        verify(ventaRepository, never()).actualizar(anyLong(), anyInt(), any(Venta.class), any(Connection.class));
        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("cancelarVentaAsync: exige una transacción configurada")
    void cancelarVentaSinTransaccion() {
        VentaService sinTransaccion = new VentaService(
                null,
                ventaRepository,
                productoRepository,
                null,
                eventBus,
                null);

        ExecutionException excepcion = assertThrows(ExecutionException.class,
                () -> sinTransaccion.cancelarVentaAsync(NEGOCIO, 7).get());

        assertInstanceOf(IllegalStateException.class, excepcion.getCause());
        verifyNoInteractions(ventaRepository, productoRepository, eventBus);
    }
}
