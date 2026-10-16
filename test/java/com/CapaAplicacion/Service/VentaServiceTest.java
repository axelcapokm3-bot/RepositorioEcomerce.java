package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IMetodoPagoRepositorio;
import com.CapaAplicacion.Interfaces.IProductoRepository;
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
import java.math.BigDecimal;
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
    private IMetodoPagoRepositorio metodoPagoRepository;

    @Mock
    private EventBus eventBus;

    @Mock
    private PagoService pagoService;

    private VentaService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new VentaService(
                null,
                ventaRepository,
                productoRepository,
                null,
                metodoPagoRepository,
                eventBus,
                null,
                pagoService);
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

        service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get();

        assertEquals(0, carrito.getItems().size(), "El carrito debe vaciarse tras crear la venta");

        verify(productoRepository).actualizar(eq(NEGOCIO), eq(1), any(Producto.class));
        verify(productoRepository).actualizar(eq(NEGOCIO), eq(2), any(Producto.class));

        ArgumentCaptor<Venta> ventaCaptor = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).guardar(ventaCaptor.capture());
        Venta guardada = ventaCaptor.getValue();
        assertEquals(EstadoVenta.PENDIENTE, guardada.getEstadoCompra());
        assertEquals(2, guardada.getDetalles().size());

        ArgumentCaptor<VentasEventos> eventoCaptor = ArgumentCaptor.forClass(VentasEventos.class);
        verify(eventBus).publicar(eventoCaptor.capture());
        assertEquals("CREADA", eventoCaptor.getValue().getTipoOperacion());
        assertNotNull(eventoCaptor.getValue().getVentaSnapshot());
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
    void crearVentaStockInsuficiente() {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 1, new BigDecimal("100")), 5));

        assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(NEGOCIO, carrito, "EFECTIVO", null).get());
        verify(ventaRepository, never()).guardar(any());
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
        verify(metodoPagoRepository, never()).actualizar(anyInt(), any());
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
                metodoPagoRepository,
                eventBus,
                null,
                null);

        assertThrows(ExecutionException.class, () -> service.registrarPagoAsync(NEGOCIO, 7).get());
    }

    // ------------------------------------------------------------------
    // CANCELAR VENTA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelarVentaAsync: reintegra stock y cancela una venta pagada")
    void cancelarVentaExitosa() throws Exception {
        Carrito carrito = new Carrito(1);
        Producto p = producto(1, 5, new BigDecimal("100"));
        carrito.agregarItem(new ItemCarrito(p, 2));

        Venta venta = new Venta(1, TipoDePago.EFECTIVO);
        venta.setIdNegocio(NEGOCIO);
        venta.cargarDetallesDesdeCarrito(carrito, null);
        venta.setId(7);
        venta.Pagar();

        when(ventaRepository.buscarPorId(NEGOCIO, 7)).thenReturn(Optional.of(venta));
        when(productoRepository.buscarPorId(NEGOCIO, 1)).thenReturn(Optional.of(p));

        service.cancelarVentaAsync(NEGOCIO, 7).get();

        assertEquals(EstadoVenta.CANCELADO, venta.getEstadoCompra());
        assertEquals(7, p.getStockDisponible());
        verify(productoRepository).actualizar(eq(NEGOCIO), eq(1), any(Producto.class));
        verify(ventaRepository).actualizar(NEGOCIO, 7, venta);

        ArgumentCaptor<VentasEventos> eventoCaptor = ArgumentCaptor.forClass(VentasEventos.class);
        verify(eventBus).publicar(eventoCaptor.capture());
        assertEquals("CANCELADA", eventoCaptor.getValue().getTipoOperacion());
    }
}
