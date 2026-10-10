package com.CapaAplicacion.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CapaAplicacion.Interfaces.EventBus;
import com.CapaAplicacion.Interfaces.IPagoRepository;
import com.CapaAplicacion.Interfaces.IVentaRepository;
import com.CapaDominio.Entidades.Carrito;
import com.CapaDominio.Entidades.CategoriaProducto;
import com.CapaDominio.Entidades.EstadoPago;
import com.CapaDominio.Entidades.ItemCarrito;
import com.CapaDominio.Entidades.Pago;
import com.CapaDominio.Entidades.Producto;
import com.CapaDominio.Entidades.ResultadoPago;
import com.CapaDominio.Entidades.TipoDePago;
import com.CapaDominio.Entidades.Venta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        when(pagoRepository.buscarPorClaveIdempotenciaPasarela(claveIdempotenciaPasarela))
                .thenReturn(Optional.of(pago));

        PagoService service = new PagoService(pagoRepository, ventaRepository, eventBus);
        ResultadoPago resultado = service.iniciarCheckout(venta);

        assertEquals("order-7", resultado.paymentId());
        assertEquals("https://checkout.example/order-7", resultado.urlPago());
        assertNotEquals(venta.getClaveIdempotenciaSolicitud(), pago.getClaveIdempotenciaPasarela());
        verify(pagoRepository).buscarPorClaveIdempotenciaPasarela(claveIdempotenciaPasarela);
    }
}
