package com.CapaAplicacion.DTOentrada;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.CapaDominio.Entidades.CategoriaProducto;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductoRequestDTOTest {

    @Test
    @DisplayName("Rechaza OTROS y categoría nula en escritura")
    void rechazaCategoriasNoEscribibles() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductoRequestDTO("Mouse", new BigDecimal("50"), 10, CategoriaProducto.OTROS));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductoRequestDTO("Mouse", new BigDecimal("50"), 10, null));
    }

    @Test
    @DisplayName("Acepta categorías reales en escritura")
    void aceptaCategoriasReales() {
        assertDoesNotThrow(() -> new ProductoRequestDTO("Agua", new BigDecimal("10"), 5, CategoriaProducto.BEBIDAS));
    }
}
