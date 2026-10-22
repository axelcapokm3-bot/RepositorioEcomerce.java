package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.OperacionTransaccional;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private IPagoRepository pagoRepository;

    @Mock
    private IVentaRepository ventaRepository;

    @Mock
    private EventBus eventBus;

    @Test
    void reutilizaPagoPersistidoConLaClavePropiaDeLaPasarela() {
        String claveSolicitud = "clave-cliente-1";
        String claveIdempotenciaPasarela = "venta-7-mercado_pago";
        Venta venta = new Venta(1, TipoDePago.MERCADO_PAGO, claveSolicitud);
        Carrito carrito = new Carrito(1);
        Producto producto = new Producto(
                "Producto",
                new BigDecimal("100.00"),
                10,
                0,
                1,
                CategoriaProducto.ELECTRONICA);
        carrito.agregarItem(new ItemCarrito(producto, 2));
        venta.setIdNegocio(1L);
        venta.cargarDetallesDesdeCarrito(carrito, null);
        venta.setId(7);
        Pago pago = new Pago(
                15,
                7,
                TipoDePago.MERCADO_PAGO.name(),
                null,
                null,
                "order-7",
                EstadoPago.PENDIENTE,
                new BigDecimal("200.00"),
                "ARS",
                "https://checkout.example/order-7",
                "{\"id\":\"order-7\"}",
                claveIdempotenciaPasarela,
                LocalDateTime.now(),
                null);
        pago.setIdNegocio(1L);
        when(pagoRepository.buscarPorClaveIdempotenciaPasarela(1L, claveIdempotenciaPasarela))
                .thenReturn(Optional.of(pago));

        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);
        ResultadoPago resultado = service.iniciarCheckout(venta);

        assertEquals("order-7", resultado.paymentId());
        assertEquals("https://checkout.example/order-7", resultado.urlPago());
        assertNotEquals(venta.getClaveIdempotenciaSolicitud(), pago.getClaveIdempotenciaPasarela());
        verify(pagoRepository).buscarPorClaveIdempotenciaPasarela(1L, claveIdempotenciaPasarela);
    }

    @Test
    void noReutilizaCheckoutSiLaVentaYaNoEstaPendiente() {
        String claveIdempotenciaPasarela = "venta-7-mercado_pago";
        Venta venta = new Venta(1, TipoDePago.MERCADO_PAGO, "clave-solicitud");
        venta.setIdNegocio(1L);
        venta.setId(7);
        venta.getDetalles().add(new com.CapaDominio.Entidades.DetalleVenta(
                1, new BigDecimal("200.00"), 1));
        venta.rehidratarEstado(EstadoVenta.CANCELADO);
        Pago pago = pagoMercadoPago(null, "order-7", EstadoPago.PENDIENTE);
        when(pagoRepository.buscarPorClaveIdempotenciaPasarela(1L, claveIdempotenciaPasarela))
                .thenReturn(Optional.of(pago));

        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);

        assertThrows(IllegalStateException.class, () -> service.iniciarCheckout(venta));
        verify(pagoRepository).buscarPorClaveIdempotenciaPasarela(1L, claveIdempotenciaPasarela);
        verifyNoInteractions(eventBus);
    }

    @Test
    void rechazaRetrocesoDeEstadoTerminal() {
        Pago pago = pagoMercadoPago("5001", "order-7", EstadoPago.APROBADO);
        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);

        assertThrows(com.CapaDominio.Excepciones.TransicionPagoInvalidaException.class,
                () -> service.aplicarEstadoDePasarela(
                        pago, EstadoPago.RECHAZADO, "5001", "order-7", "respuesta"));
        verifyNoInteractions(pagoRepository);
    }

    @Test
    void persisteIdentificadorNuevoAunqueElEstadoNoCambie() {
        Pago pago = pagoMercadoPago(null, "order-7", EstadoPago.PENDIENTE);
        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);

        service.aplicarEstadoDePasarela(
                pago, EstadoPago.PENDIENTE, "5001", "order-7", "respuesta");

        verify(pagoRepository).actualizarWebhook(
                15, "5001", "order-7", EstadoPago.PENDIENTE, "respuesta");
        assertEquals("5001", pago.getMpPaymentId());
        assertEquals("order-7", pago.getOrderId());
    }

    @Test
    void conservaIdentificadoresCuandoLaNotificacionNoLosTrae() {
        Pago pago = pagoMercadoPago("5001", "order-7", EstadoPago.PENDIENTE);
        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);

        service.aplicarEstadoDePasarela(
                pago, EstadoPago.RECHAZADO, null, " ", "respuesta");

        verify(pagoRepository).actualizarWebhook(
                15, "5001", "order-7", EstadoPago.RECHAZADO, "respuesta");
        assertEquals("5001", pago.getMpPaymentId());
        assertEquals("order-7", pago.getOrderId());
    }

    @Test
    void noActualizaSiNoHayCambios() {
        Pago pago = pagoMercadoPago("5001", "order-7", EstadoPago.PENDIENTE);
        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);

        service.aplicarEstadoDePasarela(
                pago, EstadoPago.PENDIENTE, "5001", "order-7", "respuesta");

        verifyNoInteractions(pagoRepository);
    }

    @Test
    void confirmaPagoYVentaEnLaMismaTransaccion() throws Exception {
        Pago pago = pagoMercadoPago(null, "order-7", EstadoPago.PENDIENTE);
        Venta venta = ventaPendiente();
        Connection conexion = mock(Connection.class);
        ITransaccion transaccion = mock(ITransaccion.class);
        when(ventaRepository.buscarPorIdParaUpdate(1L, 7, conexion))
                .thenReturn(Optional.of(venta));
        when(pagoRepository.buscarPorIdParaUpdate(15, conexion))
                .thenReturn(Optional.of(pago));
        doAnswer(invocacion -> {
            OperacionTransaccional operacion = invocacion.getArgument(0);
            operacion.ejecutar(conexion);
            return null;
        }).when(transaccion).ejecutarTransaccion(any());

        PagoService service = new PagoService(
                pagoRepository, ventaRepository, eventBus, transaccion);

        service.aplicarEstadoDePasarela(
                pago, EstadoPago.APROBADO, "5001", "order-7", "aprobado");

        assertEquals(EstadoVenta.PAGADO, venta.getEstadoCompra());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        verify(pagoRepository).actualizarWebhook(
                15, "5001", "order-7", EstadoPago.APROBADO, "aprobado", conexion);
        verify(ventaRepository).actualizar(1L, 7, venta, conexion);
        InOrder orden = inOrder(transaccion, eventBus);
        orden.verify(transaccion).ejecutarTransaccion(any());
        orden.verify(eventBus).publicar(any(VentasEventos.class));
    }

    @Test
    void persistePagoAprobadoTardioSinReactivarVenta() throws Exception {
        Pago pago = pagoMercadoPago(null, "order-7", EstadoPago.PENDIENTE);
        Venta venta = ventaCancelada();
        Connection conexion = mock(Connection.class);
        ITransaccion transaccion = transaccionCon(pago, venta, conexion);

        PagoService service = new PagoService(
                pagoRepository, ventaRepository, eventBus, transaccion);

        service.aplicarEstadoDePasarela(
                pago, EstadoPago.APROBADO, "5001", "order-7", "aprobado");

        assertEquals(EstadoVenta.CANCELADO, venta.getEstadoCompra());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        verify(pagoRepository).actualizarWebhook(
                15, "5001", "order-7", EstadoPago.APROBADO, "aprobado", conexion);
        verify(ventaRepository, never()).actualizar(
                anyLong(), anyInt(), any(Venta.class), any(Connection.class));
        verify(eventBus, never()).publicar(any());
    }

    @Test
    void webhookRepetidoDePagoTardioNoDuplicaActualizacion() throws Exception {
        Pago pago = pagoMercadoPago(null, "order-7", EstadoPago.PENDIENTE);
        Pago repetido = pagoMercadoPago("5001", "order-7", EstadoPago.APROBADO);
        Venta venta = ventaCancelada();
        Connection conexion = mock(Connection.class);
        ITransaccion transaccion = transaccionCon(pago, venta, conexion);

        PagoService service = new PagoService(
                pagoRepository, ventaRepository, eventBus, transaccion);

        service.aplicarEstadoDePasarela(
                pago, EstadoPago.APROBADO, "5001", "order-7", "aprobado");
        service.aplicarEstadoDePasarela(
                repetido, EstadoPago.APROBADO, "5001", "order-7", "aprobado");

        assertEquals(EstadoVenta.CANCELADO, venta.getEstadoCompra());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(EstadoPago.APROBADO, repetido.getEstado());
        verify(pagoRepository, times(1)).actualizarWebhook(
                15, "5001", "order-7", EstadoPago.APROBADO, "aprobado", conexion);
        verify(ventaRepository, never()).actualizar(
                anyLong(), anyInt(), any(Venta.class), any(Connection.class));
        verify(eventBus, never()).publicar(any());
    }

    @Test
    void noPublicaEventoSiFallaLaPersistenciaDelPago() throws Exception {
        Pago pago = pagoMercadoPago(null, "order-7", EstadoPago.PENDIENTE);
        Venta venta = ventaPendiente();
        Connection conexion = mock(Connection.class);
        ITransaccion transaccion = transaccionCon(pago, venta, conexion);
        doThrow(new RuntimeException("fallo de persistencia"))
                .when(pagoRepository).actualizarWebhook(
                        anyInt(), any(), any(), any(), any(), any());

        PagoService service = new PagoService(
                pagoRepository, ventaRepository, eventBus, transaccion);

        assertThrows(RuntimeException.class, () -> service.aplicarEstadoDePasarela(
                pago, EstadoPago.APROBADO, "5001", "order-7", "aprobado"));

        verify(eventBus, never()).publicar(any());
    }

    @Test
    void persistePagoAprobadoTardioSinTransaccionSinPublicarEvento() {
        Pago pago = pagoMercadoPago(null, "order-7", EstadoPago.PENDIENTE);
        Venta venta = ventaCancelada();
        when(ventaRepository.buscarPorId(1L, 7)).thenReturn(Optional.of(venta));

        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);
        service.aplicarEstadoDePasarela(
                pago, EstadoPago.APROBADO, "5001", "order-7", "aprobado");

        assertEquals(EstadoVenta.CANCELADO, venta.getEstadoCompra());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        verify(pagoRepository).actualizarWebhook(
                15, "5001", "order-7", EstadoPago.APROBADO, "aprobado");
        verify(ventaRepository, never()).marcarComoPagada(anyLong(), anyInt());
        verify(eventBus, never()).publicar(any());
    }

    private Venta ventaPendiente() {
        Venta venta = new Venta(1, TipoDePago.MERCADO_PAGO);
        venta.setIdNegocio(1L);
        venta.setId(7);
        return venta;
    }

    private Venta ventaCancelada() {
        Venta venta = ventaPendiente();
        venta.rehidratarEstado(EstadoVenta.CANCELADO);
        return venta;
    }

    private ITransaccion transaccionCon(Pago pago, Venta venta, Connection conexion) throws Exception {
        ITransaccion transaccion = mock(ITransaccion.class);
        when(ventaRepository.buscarPorIdParaUpdate(1L, 7, conexion))
                .thenReturn(Optional.of(venta));
        when(pagoRepository.buscarPorIdParaUpdate(15, conexion))
                .thenReturn(Optional.of(pago));
        doAnswer(invocacion -> {
            OperacionTransaccional operacion = invocacion.getArgument(0);
            operacion.ejecutar(conexion);
            return null;
        }).when(transaccion).ejecutarTransaccion(any());
        return transaccion;
    }

    private Pago pagoMercadoPago(String mpPaymentId, String orderId, EstadoPago estado) {
        Pago pago = new Pago(
                15,
                7,
                TipoDePago.MERCADO_PAGO.name(),
                mpPaymentId,
                null,
                orderId,
                estado,
                new BigDecimal("200.00"),
                "ARS",
                null,
                "{}",
                "venta-7-mercado_pago",
                LocalDateTime.now(),
                null);
        pago.setIdNegocio(1L);
        return pago;
    }
}
