package com.CapaAplicacion.Seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HuellaTokenBootstrapTest {

    @Test
    @DisplayName("calcula SHA-256 en hexadecimal estable")
    void calculaHuella() {
        String huella = HuellaTokenBootstrap.calcular("token-secreto");
        assertEquals(64, huella.length());
        assertEquals(huella, HuellaTokenBootstrap.calcular("token-secreto"));
        assertNotEquals(huella, HuellaTokenBootstrap.calcular("otro-token"));
    }

    @Test
    @DisplayName("compara solo el token exacto")
    void comparaExacto() {
        String huella = HuellaTokenBootstrap.calcular("token-secreto");
        assertTrue(HuellaTokenBootstrap.coincide(huella, "token-secreto"));
        assertFalse(HuellaTokenBootstrap.coincide(huella, "token-secreto "));
        assertFalse(HuellaTokenBootstrap.coincide(huella, "Token-secreto"));
        assertFalse(HuellaTokenBootstrap.coincide(null, "token-secreto"));
    }

    @Test
    @DisplayName("rechaza tokens vacíos")
    void rechazaVacios() {
        assertThrows(IllegalArgumentException.class, () -> HuellaTokenBootstrap.calcular(""));
        assertThrows(IllegalArgumentException.class, () -> HuellaTokenBootstrap.calcular(null));
    }
}
