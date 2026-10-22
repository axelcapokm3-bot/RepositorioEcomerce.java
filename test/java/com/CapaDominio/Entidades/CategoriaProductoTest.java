package com.CapaDominio.Entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoriaProductoTest {

    @Test
    @DisplayName("Mapea categorías reales ignorando mayúsculas, tildes y espacios")
    void mapeaCategoriasReales() {
        assertEquals(CategoriaProducto.ALMACEN, CategoriaProducto.desdeTexto("Almacén"));
        assertEquals(CategoriaProducto.HERRAMIENTAS, CategoriaProducto.desdeTexto(" Herramientas "));
        assertEquals(CategoriaProducto.BEBIDAS, CategoriaProducto.desdeTexto("BEBIDAS"));
        assertEquals(CategoriaProducto.HOGAR_Y_MUEBLES, CategoriaProducto.desdeTexto("HOGAR Y MUEBLES"));
        assertEquals(CategoriaProducto.ELECTRONICA, CategoriaProducto.desdeTexto("electrónica"));
    }

    @Test
    @DisplayName("Categorías desconocidas o vacías se mapean a OTROS")
    void categoriasDesconocidasSeMapeanAOtros() {
        assertEquals(CategoriaProducto.OTROS, CategoriaProducto.desdeTexto("Juguetes"));
        assertEquals(CategoriaProducto.OTROS, CategoriaProducto.desdeTexto(null));
        assertEquals(CategoriaProducto.OTROS, CategoriaProducto.desdeTexto("  "));
        assertFalse(CategoriaProducto.esConocida("Juguetes"));
        assertFalse(CategoriaProducto.esConocida(null));
        assertTrue(CategoriaProducto.esConocida("Almacén"));
    }
}
