package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarritoTest {

    private Producto producto(int id, BigDecimal precio) {
        return new Producto("Producto " + id, precio, 100, 0, id, CategoriaProducto.ROPA);
    }

    @Test
    @DisplayName("Agregar ítem: suma cantidades del mismo producto")
    void agregarItemAcumula() {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, new BigDecimal("10")), 2));
        carrito.agregarItem(new ItemCarrito(producto(1, new BigDecimal("10")), 3));
        assertEquals(1, carrito.getItems().size());
        assertEquals(5, carrito.getItems().get(1).getCantidad());
    }

    @Test
    @DisplayName("Agregar ítem: rechaza nulos")
    void agregarItemNulo() {
        Carrito carrito = new Carrito(1);
        assertThrows(IllegalArgumentException.class, () -> carrito.agregarItem(null));
    }

    @Test
    @DisplayName("Calcular total: sin descuento y con descuento")
    void calcularTotalCarrito() {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, new BigDecimal("100")), 2));
        assertBigDecimalIgual(new BigDecimal("200"), carrito.calcularTotalCarrito(null));

        IDescuentos descuento = new DescuentoPorPorcentaje(new BigDecimal("0.25"));
        assertBigDecimalIgual(new BigDecimal("150"), carrito.calcularTotalCarrito(descuento));
    }

    @Test
    @DisplayName("Vaciar: elimina todos los ítems")
    void vaciar() {
        Carrito carrito = new Carrito(1);
        carrito.agregarItem(new ItemCarrito(producto(1, new BigDecimal("10")), 1));
        carrito.vaciar();
        assertTrue(carrito.getItems().isEmpty());
    }

    private void assertBigDecimalIgual(BigDecimal esperado, BigDecimal actual) {
        assertEquals(0, esperado.compareTo(actual), () -> "Esperado " + esperado + " pero fue " + actual);
    }
}