package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductoTest {

    private Producto productoValido() {
        return new Producto("Laptop", new BigDecimal("1000"), 20, 0, 1, CategoriaProducto.ELECTRONICA);
    }

    @Test
    @DisplayName("Constructor: crea producto válido")
    void constructorValido() {
        Producto p = productoValido();
        assertEquals("Laptop", p.getNombre());
        assertEquals(new BigDecimal("1000"), p.getPrecio());
        assertEquals(20, p.getStockFisico());
        assertEquals(0, p.getStockReservado());
        assertEquals(CategoriaProducto.ELECTRONICA, p.getCategoria());
    }

    @Test
    @DisplayName("Constructor: rechaza nombre vacío o nulo")
    void constructorRechazaNombreInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Producto(null, new BigDecimal("10"), 5, 0, 1, CategoriaProducto.LIBROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("", new BigDecimal("10"), 5, 0, 1, CategoriaProducto.LIBROS));
    }

    @Test
    @DisplayName("Constructor: rechaza precio nulo o menor/igual a cero")
    void constructorRechazaPrecioInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("X", null, 5, 0, 1, CategoriaProducto.LIBROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("X", BigDecimal.ZERO, 5, 0, 1, CategoriaProducto.LIBROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("X", new BigDecimal("-1"), 5, 0, 1, CategoriaProducto.LIBROS));
    }

    @Test
    @DisplayName("Constructor: rechaza stock negativo, id negativo y categoría nula")
    void constructorRechazaRestoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("X", new BigDecimal("10"), -1, 0, 1, CategoriaProducto.LIBROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("X", new BigDecimal("10"), 5, 0, -1, CategoriaProducto.LIBROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("X", new BigDecimal("10"), 5, 0, 1, null));
    }

    @Test
    @DisplayName("Stock disponible = stock físico - stock reservado")
    void stockDisponible() {
        Producto p = productoValido();
        p.reservarStock(5);
        assertEquals(15, p.getStockDisponible());
    }

    @Test
    @DisplayName("Reservar stock: válido y con stock insuficiente")
    void reservarStock() {
        Producto p = productoValido();
        p.reservarStock(8);
        assertEquals(8, p.getStockReservado());
        assertThrows(IllegalArgumentException.class, () -> p.reservarStock(30));
        assertThrows(IllegalArgumentException.class, () -> p.reservarStock(0));
    }

    @Test
    @DisplayName("Liberar reserva: válido y liberando más de lo reservado")
    void liberarReserva() {
        Producto p = productoValido();
        p.reservarStock(8);
        p.liberarReserva(3);
        assertEquals(5, p.getStockReservado());
        assertThrows(IllegalArgumentException.class, () -> p.liberarReserva(99));
    }

    @Test
    @DisplayName("Reducir stock consume primero la reserva")
    void reducirStockConReserva() {
        Producto p = productoValido();
        p.reservarStock(4);
        p.reducirStock(4);
        assertEquals(0, p.getStockReservado());
        assertEquals(16, p.getStockFisico());
    }

    @Test
    @DisplayName("Reducir stock a cero dispara reposición automática de 100 unidades")
    void reducirStockDisparaReposicion() {
        Producto p = new Producto("Escaso", new BigDecimal("50"), 1, 0, 2, CategoriaProducto.ROPA);
        p.reducirStock(1);
        assertEquals(100, p.getStockFisico());
    }

    @Test
    @DisplayName("Reducir stock con cantidad inválida o insuficiente lanza error")
    void reducirStockInvalido() {
        Producto p = productoValido();
        assertThrows(IllegalArgumentException.class, () -> p.reducirStock(0));
        assertThrows(IllegalArgumentException.class, () -> p.reducirStock(999));
    }

    @Test
    @DisplayName("Reponer stock en estado crítico repone 50 unidades")
    void reponerStockCritico() {
        Producto p = new Producto("Critico", new BigDecimal("50"), 10, 0, 3, CategoriaProducto.HOGAR);
        p.reponerStock();
        assertEquals(60, p.getStockFisico());
    }

    @Test
    @DisplayName("Reponer stock en estado disponible no cambia nada")
    void reponerStockDisponible() {
        Producto p = productoValido();
        p.reponerStock();
        assertEquals(20, p.getStockFisico());
    }

    @Test
    @DisplayName("Reintegrar stock suma unidades y valida cantidad")
    void reintegrarStock() {
        Producto p = productoValido();
        p.reintegrarStock(5);
        assertEquals(25, p.getStockFisico());
        assertThrows(IllegalArgumentException.class, () -> p.reintegrarStock(0));
    }

    @Test
    @DisplayName("El ID solo puede asignarse una vez")
    void setIdUnaSolaVez() {
        Producto p = new Producto("X", new BigDecimal("10"), 5, 0, 0, CategoriaProducto.LIBROS);
        p.setId(7);
        assertEquals(7, p.getId());
        assertThrows(IllegalArgumentException.class, () -> p.setId(8));
    }
}
