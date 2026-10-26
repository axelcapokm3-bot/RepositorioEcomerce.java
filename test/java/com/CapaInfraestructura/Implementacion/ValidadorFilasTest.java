package com.CapaInfraestructura.Implementacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.CapaDominio.Excepciones.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;

class ValidadorFilasTest {

    @Test
    void exigirUnaFilaAceptaExactamenteUnaFila() {
        assertDoesNotThrow(() -> ValidadorFilas.exigirUnaFila(1, "Producto", 7));
    }

    @Test
    void exigirUnaFilaRechazaCeroYMultiplesFilas() {
        RecursoNoEncontradoException sinFilas = assertThrows(
                RecursoNoEncontradoException.class,
                () -> ValidadorFilas.exigirUnaFila(0, "Producto", 7));
        assertEquals("No se encontró Producto con id 7.", sinFilas.getMessage());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> ValidadorFilas.exigirUnaFila(2, "Producto", 7));
    }

    @Test
    void exigirFilaAceptaUnaOMasFilas() {
        assertDoesNotThrow(() -> ValidadorFilas.exigirFila(1, "Venta", 9));
        assertDoesNotThrow(() -> ValidadorFilas.exigirFila(2, "Venta", 9));
    }

    @Test
    void exigirFilaRechazaCeroFilas() {
        RecursoNoEncontradoException sinFilas = assertThrows(
                RecursoNoEncontradoException.class,
                () -> ValidadorFilas.exigirFila(0, "Venta", 9));
        assertEquals("No se encontró Venta con id 9.", sinFilas.getMessage());
    }
}
