package com.CapaAplicacion.DTOsalida;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultadoPaginadoTest {

    @Test
    @DisplayName("calcula correctamente el total de páginas y asigna valores por defecto")
    void calculaTotalPaginasYDefaults() {
        ResultadoPaginado<String> resultado = new ResultadoPaginado<>(List.of("a", "b"), 1, 10, 25);

        assertEquals(2, resultado.datos().size());
        assertEquals(1, resultado.paginaActual());
        assertEquals(3, resultado.totalPaginas()); // 25 / 10 = 2.5 -> 3 páginas
        assertEquals(25, resultado.totalRegistros());
        assertEquals(10, resultado.tamanioPagina());
    }

    @Test
    @DisplayName("maneja listas nulas y valores inválidos de paginación de forma segura")
    void manejaNulosYValoresInvalidos() {
        ResultadoPaginado<String> resultado = new ResultadoPaginado<>(null, -1, 0, -5);

        assertNotNull(resultado.datos());
        assertEquals(0, resultado.datos().size());
        assertEquals(1, resultado.paginaActual());
        assertEquals(1, resultado.tamanioPagina());
        assertEquals(0, resultado.totalRegistros());
        assertEquals(0, resultado.totalPaginas());
    }
}
