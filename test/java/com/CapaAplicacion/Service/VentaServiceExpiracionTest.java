package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaAplicacion.Interfaces.ITransaccion;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaAplicacion.Interfaces.OperacionTransaccional;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.DetalleVenta;
import com.CapaDominio.Entidades.EstadoVenta;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import com.CapaDominio.Entidades.VentasEventos;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VentaServiceExpiracionTest {

    @Test
    void expiraSoloVentasPendientesVencidasYReintegraStock() throws Exception {
        IVentaRepository ventaRepository = mock(IVentaRepository.class);
        IProductoRepository productoRepository = mock(IProductoRepository.class);
        EventBus eventBus = mock(EventBus.class);
        ITransaccion transaccion = mock(ITransaccion.class);
        Connection connection = mock(Connection.class);

        Venta vencida = venta(7, LocalDateTime.now().minusMinutes(60), 1, 2);
        Venta reciente = venta(8, LocalDateTime.now().minusMinutes(5), 1, 2);
        Producto producto = new Producto(
                "Producto", new BigDecimal("100.00"), 10, 0, 1,
                CategoriaProducto.ELECTRONICA);

        when(ventaRepository.buscarPendientesAnteriores(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(vencida, reciente));
        when(ventaRepository.buscarPorIdParaUpdate(1L, 7, connection))
                .thenReturn(Optional.of(vencida));
        when(ventaRepository.buscarPorIdParaUpdate(1L, 8, connection))
                .thenReturn(Optional.of(reciente));
        when(productoRepository.buscarPorIdParaUpdate(1L, 1, connection))
                .thenReturn(Optional.of(producto));
        doAnswer(invocacion -> {
            OperacionTransaccional operacion = invocacion.getArgument(0);
            operacion.ejecutar(connection);
            return null;
        }).when(transaccion).ejecutarTransaccion(any());

        VentaService service = new VentaService(
                transaccion, ventaRepository, productoRepository, null, eventBus, null, null);

        int expiradas = service.expirarVentasPendientes(30, 10);

        assertEquals(1, expiradas);
        assertEquals(EstadoVenta.EXPIRADO, vencida.getEstadoCompra());
        assertEquals(EstadoVenta.PENDIENTE, reciente.getEstadoCompra());
        assertEquals(12, producto.getStockDisponible());
        verify(ventaRepository).actualizar(1L, 7, vencida, connection);
        verify(ventaRepository, never()).actualizar(1L, 8, reciente, connection);
        verify(productoRepository).actualizar(1L, 1, producto, connection);

        ArgumentCaptor<VentasEventos> evento = ArgumentCaptor.forClass(VentasEventos.class);
        verify(eventBus).publicar(evento.capture());
        assertEquals("EXPIRADA", evento.getValue().getTipoOperacion());
        assertEquals(7, evento.getValue().getIdVenta());
    }

    @Test
    void noExpiraVentasQueYaNoEstanPendientes() throws Exception {
        IVentaRepository ventaRepository = mock(IVentaRepository.class);
        IProductoRepository productoRepository = mock(IProductoRepository.class);
        EventBus eventBus = mock(EventBus.class);
        ITransaccion transaccion = mock(ITransaccion.class);
        Connection connection = mock(Connection.class);

        Venta pagada = venta(9, LocalDateTime.now().minusMinutes(60), 1, 1);
        pagada.rehidratarEstado(EstadoVenta.PAGADO);
        when(ventaRepository.buscarPendientesAnteriores(any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(pagada));
        when(ventaRepository.buscarPorIdParaUpdate(1L, 9, connection))
                .thenReturn(Optional.of(pagada));
        doAnswer(invocacion -> {
            OperacionTransaccional operacion = invocacion.getArgument(0);
            operacion.ejecutar(connection);
            return null;
        }).when(transaccion).ejecutarTransaccion(any());

        VentaService service = new VentaService(
                transaccion, ventaRepository, productoRepository, null, eventBus, null, null);

        assertEquals(0, service.expirarVentasPendientes(30, 10));
        assertEquals(EstadoVenta.PAGADO, pagada.getEstadoCompra());
        verify(ventaRepository, never()).actualizar(anyLong(), anyInt(),
                any(Venta.class), any(Connection.class));
        verify(productoRepository, never()).actualizar(
                anyLong(), anyInt(), any(Producto.class), any(Connection.class));
        verify(eventBus, never()).publicar(any());
    }

    private Venta venta(int id, LocalDateTime fechaCompra, int idProducto, int cantidad) {
        Venta venta = new Venta(1, TipoDePago.MERCADO_PAGO);
        venta.setIdNegocio(1L);
        venta.setId(id);
        venta.rehidratarFechaCompra(fechaCompra);
        venta.getDetalles().add(new DetalleVenta(
                idProducto, new BigDecimal("100.00"), cantidad));
        return venta;
    }
}
