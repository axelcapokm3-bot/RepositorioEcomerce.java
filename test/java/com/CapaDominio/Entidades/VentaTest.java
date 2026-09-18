package com.CapaDominio.Entidades;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VentaTest {

    private Venta ventaPendiente() {
        return new Venta(1, TipoDePago.EFECTIVO);
    }

    private Carrito carritoConUnItem() {
        Producto p = new Producto("Mouse", new BigDecimal("50"), 10, 0, 1, CategoriaProducto.ELECTRONICA);
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(p, 2));
        return carrito;
    }

    @Test
    @DisplayName("Constructor: crea venta pendiente")
    void constructorValido() {
        Venta v = ventaPendiente();
        assertEquals(1, v.getIdCliente());
        assertEquals(EstadoVenta.PENDIENTE, v.getEstadoCompra());
        assertNotNull(v.getFechaCompra());
    }

    @Test
    @DisplayName("Constructor: rechaza cliente inválido o tipo de pago nulo")
    void constructorRechazaInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Venta(0, TipoDePago.EFECTIVO));
        assertThrows(IllegalArgumentException.class, () -> new Venta(1, null));
    }

    @Test
    @DisplayName("Cargar detalles: crea detalles con precio con descuento")
    void cargarDetallesDesdeCarrito() {
        Venta v = ventaPendiente();
        IDescuentos descuento = new DescuentoPorPorcentaje(new BigDecimal("0.10"));
        v.cargarDetallesDesdeCarrito(carritoConUnItem(), descuento);
        assertEquals(1, v.getDetalles().size());
        DetalleVenta d = v.getDetalles().get(0);
        assertEquals(1, d.getIdProducto());
        assertEquals(2, d.getCantidad());
        assertEquals(new BigDecimal("45.00"), d.getPrecioUnitario());
    }

    @Test
    @DisplayName("Cargar detalles: rechaza carrito vacío")
    void cargarDetallesCarritoVacio() {
        Venta v = ventaPendiente();
        assertThrows(IllegalArgumentException.class,
                () -> v.cargarDetallesDesdeCarrito(new Carrito(1), null));
    }

    @Test
    @DisplayName("Calcular total: suma los subtotales de los detalles")
    void calculoTotal() {
        Venta v = ventaPendiente();
        v.cargarDetallesDesdeCarrito(carritoConUnItem(), null);
        assertEquals(new BigDecimal("100.00"), v.calculoTotal());
    }

    @Test
    @DisplayName("Pagar: pasa una venta con detalles a PAGADO")
    void pagarVentaConDetalles() {
        Venta v = ventaPendiente();
        v.cargarDetallesDesdeCarrito(carritoConUnItem(), null);
        v.Pagar();
        assertEquals(EstadoVenta.PAGADO, v.getEstadoCompra());
    }

    @Test
    @DisplayName("Pagar: transición PENDIENTE -> PAGADO válida")
    void pagar() {
        Venta v = ventaPendiente();
        v.Pagar();
        assertEquals(EstadoVenta.PAGADO, v.getEstadoCompra());
        assertThrows(IllegalStateException.class, () -> v.Pagar());
    }

    @Test
    @DisplayName("Cancelar: solo permitido desde PAGADO")
    void cancelar() {
        Venta pendiente = ventaPendiente();
        assertThrows(IllegalStateException.class, () -> pendiente.Cancelar());

        Venta pagada = ventaPendiente();
        pagada.Pagar();
        pagada.Cancelar();
        assertEquals(EstadoVenta.CANCELADO, pagada.getEstadoCompra());
    }

    @Test
    @DisplayName("Enviar: solo permitido desde PAGADO")
    void enviar() {
        Venta pendiente = ventaPendiente();
        assertThrows(IllegalStateException.class, () -> pendiente.Enviar());

        Venta pagada = ventaPendiente();
        pagada.Pagar();
        pagada.Enviar();
        assertEquals(EstadoVenta.ENVIADO, pagada.getEstadoCompra());
    }

    @Test
    @DisplayName("El ID de la venta solo puede asignarse una vez")
    void setIdUnaSolaVez() {
        Venta v = ventaPendiente();
        v.setId(9);
        assertEquals(9, v.getId());
        assertThrows(IllegalStateException.class, () -> v.setId(10));
    }

}