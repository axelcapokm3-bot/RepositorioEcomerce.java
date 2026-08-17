package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.ICuentaRepositorio;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.Cuenta;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private IVentaRepository ventaRepository;

    @Mock
    private IProductoRepository productoRepository;

    @Mock
    private ICuentaRepositorio cuentaRepository;

    @Mock
    private EventBus eventBus;

    @InjectMocks
    private VentaService service;

    private Producto producto(int id, int stock, BigDecimal precio) {
        return new Producto("Producto " + id, precio, stock, 0, id, CategoriaProducto.ELECTRONICA);
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

        service.crearVentaAsync(carrito, "EFECTIVO", null).get();

        assertEquals(0, carrito.getItems().size(), "El carrito debe vaciarse tras crear la venta");

        verify(productoRepository).actualizar(eq(1), any(Producto.class));
        verify(productoRepository).actualizar(eq(2), any(Producto.class));

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
                () -> service.crearVentaAsync(new Carrito(1), "EFECTIVO", null).get());
        verify(ventaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("crearVentaAsync: rechaza stock insuficiente")
    void crearVentaStockInsuficiente() {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, 1, new BigDecimal("100")), 5));

        assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(carrito, "EFECTIVO", null).get());
        verify(ventaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("crearVentaAsync: rechaza tipo de pago inválido")
    void crearVentaTipoPagoInvalido() {
        assertThrows(ExecutionException.class,
                () -> service.crearVentaAsync(carritoValido(), "DESCONOCIDO", null).get());
        verify(ventaRepository, never()).guardar(any());
    }

    // ------------------------------------------------------------------
    // PAGO DE VENTA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("registrarPagoAsync: debita la cuenta del cliente y pasa la venta a PAGADO")
    void registrarPagoExitoso() throws Exception {
        Venta venta = ventaPendienteConDetalles();
        Cuenta cuenta = new Cuenta(1, "jperez", "jperez@gmail.com", "Password123", "CLIENTE", 1, new BigDecimal("1000"));

        when(ventaRepository.buscarPorId(7)).thenReturn(Optional.of(venta));
        when(cuentaRepository.buscarPorIdCliente(1)).thenReturn(List.of(cuenta));

        service.registrarPagoAsync(7).get();

        assertEquals(EstadoVenta.PAGADO, venta.getEstadoCompra());
        assertBigDecimalIgual(new BigDecimal("800"), cuenta.getSaldo(), "Total de la venta: 2 x 100 = 200");
        verify(cuentaRepository).actualizar(1, cuenta);
        verify(ventaRepository).actualizar(7, venta);

        ArgumentCaptor<VentasEventos> eventoCaptor = ArgumentCaptor.forClass(VentasEventos.class);
        verify(eventBus).publicar(eventoCaptor.capture());
        assertEquals("PAGADA", eventoCaptor.getValue().getTipoOperacion());
    }

    @Test
    @DisplayName("registrarPagoAsync: rechaza venta inexistente")
    void registrarPagoVentaInexistente() {
        when(ventaRepository.buscarPorId(99)).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class, () -> service.registrarPagoAsync(99).get());
        verify(eventBus, never()).publicar(any());
    }

    @Test
    @DisplayName("registrarPagoAsync: rechaza cliente sin cuentas")
    void registrarPagoSinCuentas() {
        Venta venta = ventaPendienteConDetalles();
        when(ventaRepository.buscarPorId(7)).thenReturn(Optional.of(venta));
        when(cuentaRepository.buscarPorIdCliente(1)).thenReturn(List.of());

        assertThrows(ExecutionException.class, () -> service.registrarPagoAsync(7).get());
    }

    @Test
    @DisplayName("registrarPagoAsync: rechaza saldo insuficiente")
    void registrarPagoSaldoInsuficiente() {
        Venta venta = ventaPendienteConDetalles();
        Cuenta cuenta = new Cuenta(1, "jperez", "jperez@gmail.com", "Password123", "CLIENTE", 1, new BigDecimal("10"));

        when(ventaRepository.buscarPorId(7)).thenReturn(Optional.of(venta));
        when(cuentaRepository.buscarPorIdCliente(1)).thenReturn(List.of(cuenta));

        assertThrows(ExecutionException.class, () -> service.registrarPagoAsync(7).get());
        assertEquals(new BigDecimal("10"), cuenta.getSaldo());
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
        venta.cargarDetallesDesdeCarrito(carrito, null);
        venta.setId(7);
        venta.Pagar();

        when(ventaRepository.buscarPorId(7)).thenReturn(Optional.of(venta));
        when(productoRepository.buscarPorId(1)).thenReturn(Optional.of(p));

        service.cancelarVentaAsync(7).get();

        assertEquals(EstadoVenta.CANCELADO, venta.getEstadoCompra());
        assertEquals(7, p.getStockFisico(), "Se deben reintegrar las 2 unidades vendidas");
        verify(productoRepository).actualizar(1, p);

        ArgumentCaptor<VentasEventos> eventoCaptor = ArgumentCaptor.forClass(VentasEventos.class);
        verify(eventBus).publicar(eventoCaptor.capture());
        assertEquals("CANCELADA", eventoCaptor.getValue().getTipoOperacion());
    }

    @Test
    @DisplayName("cancelarVentaAsync: rechaza venta inexistente")
    void cancelarVentaInexistente() {
        when(ventaRepository.buscarPorId(99)).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class, () -> service.cancelarVentaAsync(99).get());
    }

    @Test
    @DisplayName("cancelarVentaAsync: falla si un producto de la venta ya no existe")
    void cancelarVentaProductoInexistente() {
        Venta venta = ventaPendienteConDetalles();
        venta.Pagar();
        when(ventaRepository.buscarPorId(7)).thenReturn(Optional.of(venta));
        when(productoRepository.buscarPorId(1)).thenReturn(Optional.empty());

        assertThrows(ExecutionException.class, () -> service.cancelarVentaAsync(7).get());
        verify(eventBus, never()).publicar(any());
    }

    private void assertBigDecimalIgual(BigDecimal esperado, BigDecimal actual, String mensaje) {
        assertEquals(0, esperado.compareTo(actual), () -> mensaje + " -> Esperado " + esperado + " pero fue " + actual);
    }
}